package com.example.demo.service;

import com.example.demo.mapper.OrderItemMapper;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final ProductCacheService productCacheService;
    private final AsyncDeleteService asyncDeleteService;
    private final RedisStockService redisStockService;
    private final RedisOrderGuardService redisOrderGuardService;

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        ProductMapper productMapper,
                        ProductCacheService productCacheService,
                        AsyncDeleteService asyncDeleteService,
                        RedisStockService redisStockService,
                        RedisOrderGuardService redisOrderGuardService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.productCacheService = productCacheService;
        this.asyncDeleteService = asyncDeleteService;
        this.redisStockService = redisStockService;
        this.redisOrderGuardService = redisOrderGuardService;
    }

    @Transactional
    public Long placeOrder(Long userId, Long productId, int quantity, String requestId) {

        /**
         * 第 0 步：基础参数校验
         */
        if (requestId == null || requestId.isBlank()) {
            throw new RuntimeException("requestId 不能为空");
        }

        /**
         * 第 1 步：先做一人一单校验
         *
         * 为什么放最前面？
         * 因为如果这个用户已经成功买过了，
         * 就没必要再去做后面的 Redis 预扣、MySQL 扣减、写订单。
         */
        if (redisOrderGuardService.hasOrdered(userId, productId)) {
            throw new RuntimeException("一人一单限制：你已经购买过该商品");
        }

        /**
         * 第 2 步：再做幂等标记抢占
         *
         * 为什么它和“一人一单”不是一回事？
         * - 一人一单：业务规则（用户维度）
         * - 幂等标记：系统防重（请求维度）
         */
        boolean idempotentLocked = redisOrderGuardService.tryMarkIdempotent(requestId);
        if (!idempotentLocked) {
            throw new RuntimeException("重复请求，请勿重复提交");
        }

        /**
         * 第 3 步：Redis 预扣库存
         * 让高并发入口先由 Redis 快速裁决
         */
        long redisResult = redisStockService.tryDeductStock(productId, quantity);

        if (redisResult == -2) {
            // 库存未预热，当前阶段直接报错最清楚
            redisOrderGuardService.clearIdempotent(requestId);
            throw new RuntimeException("Redis 库存未预热，暂无法下单");
        }

        if (redisResult == -1) {
            // Redis 判断库存不足
            redisOrderGuardService.clearIdempotent(requestId);
            throw new RuntimeException("库存不足（Redis 预扣失败）");
        }

        boolean redisDeductSuccess = true;

        try {
            /**
             * 第 4 步：查商品（拿价格、校验存在）
             */
            Product product = productMapper.findById(productId);
            if (product == null) {
                throw new RuntimeException("商品不存在");
            }

            /**
             * 第 5 步：MySQL 最终真相裁决
             *
             * 即使 Redis 预扣成功，这里仍然要让 MySQL 做最终扣减，
             * 因为 MySQL 才是库存真相源。
             */
            int affected = productMapper.deductStock(productId, quantity);
            if (affected == 0) {
                throw new RuntimeException("库存不足（MySQL 最终裁决失败）");
            }

            /**
             * 第 6 步：创建订单主表
             */
            double totalPrice = product.getPrice() * quantity;

            Order order = new Order();
            order.setUserId(userId);
            order.setTotalPrice(totalPrice);
            order.setStatus("CREATED");
            order.setCreatedAt(LocalDateTime.now());

            int inserted = orderMapper.insert(order);
            if (inserted != 1) {
                throw new RuntimeException("创建订单失败");
            }

            /**
             * 第 7 步：创建订单明细
             */
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(productId);
            item.setQuantity(quantity);
            item.setPrice(product.getPrice());

            int insertedItem = orderItemMapper.insert(item);
            if (insertedItem != 1) {
                throw new RuntimeException("创建订单明细失败");
            }

            /**
             * 第 8 步：事务提交后处理
             *
             * 这里做两件事：
             * 1. 删除商品缓存（前一层你已经补过）
             * 2. 订单成功后，写入“一人一单”标记
             *
             * 为什么放到 afterCommit？
             * 因为只有事务真正提交成功，这笔订单才算真正成立。
             */
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 8.1 删除商品相关缓存
                    productCacheService.evictAfterProductChanged(productId);
                    asyncDeleteService.delayedDeleteProductCache(productId, 200);

                    // 8.2 记录“一人一单”成功标记
                    redisOrderGuardService.markOrdered(userId, productId);
                }
            });

            return order.getId();

        } catch (RuntimeException e) {
            /**
             * 第 9 步：失败补偿
             *
             * 只要 Redis 已经预扣成功，后面失败就要回补 Redis。
             * 同时清理幂等标记，让后续用户可以重新发起请求。
             */
            if (redisDeductSuccess) {
                redisStockService.rollbackStock(productId, quantity);
            }

            redisOrderGuardService.clearIdempotent(requestId);
            throw e;
        }
    }
}