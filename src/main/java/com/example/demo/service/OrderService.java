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
    private final RedisLockService redisLockService;

    /**
     * 当前阶段演示锁 TTL：10 秒
     * demo 项目足够用
     */
    private static final long ORDER_LOCK_TTL_SECONDS = 10L;

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        ProductMapper productMapper,
                        ProductCacheService productCacheService,
                        AsyncDeleteService asyncDeleteService,
                        RedisStockService redisStockService,
                        RedisOrderGuardService redisOrderGuardService,
                        RedisLockService redisLockService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.productCacheService = productCacheService;
        this.asyncDeleteService = asyncDeleteService;
        this.redisStockService = redisStockService;
        this.redisOrderGuardService = redisOrderGuardService;
        this.redisLockService = redisLockService;
    }

    @Transactional
    public Long placeOrder(Long userId, Long productId, int quantity, String requestId) {

        if (requestId == null || requestId.isBlank()) {
            throw new RuntimeException("requestId 不能为空");
        }

        /**
         * 第 1 步：先抢“用户 + 商品”维度的分布式锁
         *
         * 为什么是这个粒度？
         * 因为我们要解决的问题是：
         * 同一个用户对同一个商品，在并发时只能有一个请求进入下单主流程。
         */
        String orderLockKey = redisLockService.buildOrderLockKey(userId, productId);
        String lockValue = redisLockService.tryLock(orderLockKey, ORDER_LOCK_TTL_SECONDS);

        if (lockValue == null) {
            throw new RuntimeException("请求过于频繁，请勿重复操作");
        }

        /**
         * 注意：
         * 这里不能在方法 finally 里立刻解锁。
         *
         * 因为当前方法有 @Transactional，
         * 方法返回时事务可能还没真正 commit。
         *
         * 所以更严谨的做法是：
         * 让锁一直持有到事务完成（提交或回滚）再释放。
         */
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                redisLockService.unlock(orderLockKey, lockValue);
            }
        });

        /**
         * 第 2 步：一人一单校验
         */
        if (redisOrderGuardService.hasOrdered(userId, productId)) {
            throw new RuntimeException("一人一单限制：你已经购买过该商品");
        }

        /**
         * 第 3 步：幂等标记
         */
        boolean idempotentLocked = redisOrderGuardService.tryMarkIdempotent(requestId);
        if (!idempotentLocked) {
            throw new RuntimeException("重复请求，请勿重复提交");
        }

        /**
         * 第 4 步：Redis 预扣库存
         */
        long redisResult = redisStockService.tryDeductStock(productId, quantity);

        if (redisResult == -2) {
            redisOrderGuardService.clearIdempotent(requestId);
            throw new RuntimeException("Redis 库存未预热，暂无法下单");
        }

        if (redisResult == -1) {
            redisOrderGuardService.clearIdempotent(requestId);
            throw new RuntimeException("库存不足（Redis 预扣失败）");
        }

        boolean redisDeductSuccess = true;

        try {
            /**
             * 第 5 步：查商品信息
             */
            Product product = productMapper.findById(productId);
            if (product == null) {
                throw new RuntimeException("商品不存在");
            }

            /**
             * 第 6 步：MySQL 最终真相裁决
             */
            int affected = productMapper.deductStock(productId, quantity);
            if (affected == 0) {
                throw new RuntimeException("库存不足（MySQL 最终裁决失败）");
            }

            /**
             * 第 7 步：写订单主表
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
             * 第 8 步：写订单明细
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
             * 第 9 步：事务提交后做善后动作
             * - 删商品缓存
             * - 延迟双删
             * - 记录一人一单成功标记
             */
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    productCacheService.evictAfterProductChanged(productId);
                    asyncDeleteService.delayedDeleteProductCache(productId, 200);
                    redisOrderGuardService.markOrdered(userId, productId);
                }
            });

            return order.getId();

        } catch (RuntimeException e) {
            /**
             * 第 10 步：失败补偿
             */
            if (redisDeductSuccess) {
                redisStockService.rollbackStock(productId, quantity);
            }

            redisOrderGuardService.clearIdempotent(requestId);
            throw e;
        }
    }
}