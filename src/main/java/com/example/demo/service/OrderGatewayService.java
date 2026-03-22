package com.example.demo.service;

import com.example.demo.mq.OrderCreateMessage;
import com.example.demo.mq.OrderMessageProducer;
import org.springframework.stereotype.Service;

/**
 * 异步下单入口服务
 *
 * 它是“用户请求线程”真正走的地方。
 *
 * 这一层的目标：
 * - 快速校验
 * - 快速预扣
 * - 发消息
 * - 快速返回
 *
 * 它不做 MySQL 落库。
 */
@Service
public class OrderGatewayService {

    private final RedisLockService redisLockService;
    private final RedisOrderGuardService redisOrderGuardService;
    private final RedisStockService redisStockService;
    private final OrderMessageProducer orderMessageProducer;

    private static final long ORDER_LOCK_TTL_SECONDS = 10L;

    public OrderGatewayService(RedisLockService redisLockService,
                               RedisOrderGuardService redisOrderGuardService,
                               RedisStockService redisStockService,
                               OrderMessageProducer orderMessageProducer) {
        this.redisLockService = redisLockService;
        this.redisOrderGuardService = redisOrderGuardService;
        this.redisStockService = redisStockService;
        this.orderMessageProducer = orderMessageProducer;
    }

    /**
     * 提交订单（异步版）
     *
     * 返回值不是“订单号”，而是“请求已受理”的提示。
     * 因为真正的订单是消费者异步创建的。
     */
    public String submitOrder(Long userId, Long productId, int quantity, String requestId) {

        if (requestId == null || requestId.isBlank()) {
            throw new RuntimeException("requestId 不能为空");
        }

        /**
         * 第 1 步：先抢 userId + productId 维度的锁
         * 目的：防止同一个用户对同一个商品并发冲进来。
         */
        String lockKey = redisLockService.buildOrderLockKey(userId, productId);
        String lockValue = redisLockService.tryLock(lockKey, ORDER_LOCK_TTL_SECONDS);

        if (lockValue == null) {
            throw new RuntimeException("请求过于频繁，请勿重复操作");
        }

        try {
            /**
             * 第 2 步：一人一单校验
             *
             * 如果已经 SUCCESS，说明已经买过
             * 如果是 PENDING，说明上一次请求还在处理中
             */
            String orderStatus = redisOrderGuardService.getOrderStatus(userId, productId);
            if ("SUCCESS".equals(orderStatus)) {
                throw new RuntimeException("一人一单限制：你已经购买过该商品");
            }
            if ("PENDING".equals(orderStatus)) {
                throw new RuntimeException("订单正在处理中，请勿重复提交");
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
             *
             * 这是高并发入口的第一道库存裁决。
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

            /**
             * 第 5 步：写 PENDING
             *
             * 含义：
             * 这笔订单已经被系统受理，正在异步处理中。
             */
            redisOrderGuardService.markPending(userId, productId);

            /**
             * 第 6 步：发 MQ 消息
             *
             * 这一步开始，真正的订单创建就交给消费者去完成。
             */
            try {
                orderMessageProducer.sendCreateOrderMessage(
                        new OrderCreateMessage(userId, productId, quantity, requestId)
                );
            } catch (Exception e) {
                /**
                 * 如果发消息失败，入口层必须立即补偿：
                 * - 回补 Redis 库存
                 * - 清理幂等标记
                 * - 清理 PENDING 状态
                 */
                redisStockService.rollbackStock(productId, quantity);
                redisOrderGuardService.clearIdempotent(requestId);
                redisOrderGuardService.clearOrderStatus(userId, productId);
                throw new RuntimeException("发送订单消息失败");
            }

            return "订单请求已受理，正在异步处理中";

        } finally {
            /**
             * 第 7 步：释放入口锁
             *
             * 这里和同步下单场景不同：
             * 入口层已经完成了“受理 + 发消息”，
             * 后面的落库交给 MQ 消费者。
             *
             * 所以锁此时就可以释放。
             */
            redisLockService.unlock(lockKey, lockValue);
        }
    }
}