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

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        ProductMapper productMapper,
                        ProductCacheService productCacheService,
                        AsyncDeleteService asyncDeleteService,
                        RedisStockService redisStockService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.productCacheService = productCacheService;
        this.asyncDeleteService = asyncDeleteService;
        this.redisStockService = redisStockService;
    }

    @Transactional
    public Long placeOrder(Long userId, Long productId, int quantity) {

        /**
         * 第 1 步：先走 Redis 预扣
         *
         * 这一步的意义：
         * - 高并发入口先在 Redis 做快速裁决
         * - 避免大量请求一上来就打 MySQL
         */
        long redisResult = redisStockService.tryDeductStock(productId, quantity);

        // -2：说明这个商品库存没有预热到 Redis
        if (redisResult == -2) {
            throw new RuntimeException("Redis 库存未预热，暂无法下单");
        }

        // -1：Redis 判断库存不足
        if (redisResult == -1) {
            throw new RuntimeException("库存不足（Redis 预扣失败）");
        }

        /**
         * 只要走到这里，就说明 Redis 预扣成功了。
         * 后面如果 MySQL 下单失败，一定要记得回补 Redis。
         */
        boolean redisDeductSuccess = true;

        try {
            // 第 2 步：查商品（拿价格/校验存在）
            Product product = productMapper.findById(productId);
            if (product == null) {
                throw new RuntimeException("商品不存在");
            }

            // 第 3 步：MySQL 原子扣减库存（最终真相确认）
            int affected = productMapper.deductStock(productId, quantity);
            if (affected == 0) {
                throw new RuntimeException("库存不足（MySQL 最终裁决失败）");
            }

            // 第 4 步：计算总价
            double totalPrice = product.getPrice() * quantity;

            // 第 5 步：写订单主表
            Order order = new Order();
            order.setUserId(userId);
            order.setTotalPrice(totalPrice);
            order.setStatus("CREATED");
            order.setCreatedAt(LocalDateTime.now());

            int inserted = orderMapper.insert(order);
            if (inserted != 1) {
                throw new RuntimeException("创建订单失败");
            }

            // 第 6 步：写订单明细
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
             * 第 7 步：事务提交后删缓存 + 延迟双删
             * 这是你前一层已经补过的逻辑，这里继续沿用。
             */
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    productCacheService.evictAfterProductChanged(productId);
                    asyncDeleteService.delayedDeleteProductCache(productId, 200);
                }
            });

            return order.getId();

        } catch (RuntimeException e) {
            /**
             * 第 8 步：如果后续 MySQL 事务失败，要把 Redis 预扣库存补回去
             *
             * 这一步非常重要。
             * 否则 Redis 和 MySQL 的库存会越跑越不一致。
             */
            if (redisDeductSuccess) {
                redisStockService.rollbackStock(productId, quantity);
            }
            throw e;
        }
    }
}