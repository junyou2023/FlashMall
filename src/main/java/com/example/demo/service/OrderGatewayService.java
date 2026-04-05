package com.example.demo.service;

import com.example.demo.dto.OrderSubmitTraceResult;
import com.example.demo.mq.OrderCreateMessage;
import com.example.demo.mq.OrderMessageProducer;
import com.example.demo.observability.WriteChainMetricsService;
import org.springframework.stereotype.Service;

/**
 * 异步下单入口服务
 *
 * 这个类运行在“用户请求线程”里，
 * 只负责把请求快速校验、快速预扣、快速投递到 MQ。
 *
 * 它本身不做最终落库：
 * 真正的订单创建、MySQL 扣库存、写订单表，
 * 都交给 RabbitMQ 消费者异步完成。
 */
@Service
public class OrderGatewayService {

    private final RedisLockService redisLockService;
    private final RedisOrderGuardService redisOrderGuardService;
    private final RedisStockService redisStockService;
    private final OrderMessageProducer orderMessageProducer;
    private final WriteChainMetricsService writeChainMetricsService;

    /**
     * 入口锁的过期时间。
     *
     * 作用：
     * 防止同一个用户对同一个商品在极短时间内并发提交多次。
     */
    private static final long ORDER_LOCK_TTL_SECONDS = 10L;

    public OrderGatewayService(RedisLockService redisLockService,
                               RedisOrderGuardService redisOrderGuardService,
                               RedisStockService redisStockService,
                               OrderMessageProducer orderMessageProducer,
                               WriteChainMetricsService writeChainMetricsService) {
        this.redisLockService = redisLockService;
        this.redisOrderGuardService = redisOrderGuardService;
        this.redisStockService = redisStockService;
        this.orderMessageProducer = orderMessageProducer;
        this.writeChainMetricsService = writeChainMetricsService;
    }

    /**
     * 提交订单（异步版）
     *
     * 注意：
     * 这里返回的不是最终订单号，
     * 而是“请求已受理，正在处理”的结果。
     *
     * 因为真正的订单创建发生在 MQ 消费端，
     * 当前线程只负责把这次下单请求安全地送进异步链路。
     */
    public String submitOrder(Long userId, Long productId, int quantity, String requestId) {
        return submitOrderWithTrace(userId, productId, quantity, requestId).getMessage();
    }

    /**
     * 【本轮改动】
     * 新增一个“带链路标签”的返回结构，供 Controller 打最小观测日志。
     *
     * 【注意】
     * 不改变现有主链路流程；
     * 只是把“Redis 预扣 / MQ 发送 / 异步受理”这三个关键步骤显式化。
     */
    public OrderSubmitTraceResult submitOrderWithTrace(Long userId, Long productId, int quantity, String requestId) {
        String preDeductStatus = "NOT_STARTED";
        String mqSendStatus = "NOT_STARTED";
        boolean asyncAccepted = false;

        /**
         * requestId 是这次请求的业务幂等标识。
         * 如果没有它，就无法判断“这是不是同一次请求的重复提交”。
         */
        if (requestId == null || requestId.isBlank()) {
            throw new RuntimeException("requestId 不能为空");
        }

        /**
         * 第 1 步：先抢 userId + productId 维度的分布式锁
         *
         * 为什么先加锁：
         * 防止同一个用户对同一个商品在同一时刻并发冲进来，
         * 这样后面的“一人一单”和库存逻辑会更稳。
         */
        String lockKey = redisLockService.buildOrderLockKey(userId, productId);
        String lockValue = redisLockService.tryLock(lockKey, ORDER_LOCK_TTL_SECONDS);

        if (lockValue == null) {
            throw new RuntimeException("请求过于频繁，请勿重复操作");
        }

        try {
            /**
             * 第 2 步：检查当前订单状态
             *
             * SUCCESS：
             *   说明这个用户已经成功买过，直接拦截。
             *
             * PENDING：
             *   说明这笔订单已经进入异步链路，正在处理中，
             *   此时不能再重复提交。
             */
            String orderStatus = redisOrderGuardService.getOrderStatus(userId, productId);
            if ("SUCCESS".equals(orderStatus)) {
                throw new RuntimeException("一人一单限制：你已经购买过该商品");
            }
            if ("PENDING".equals(orderStatus)) {
                throw new RuntimeException("订单正在处理中，请勿重复提交");
            }

            /**
             * 第 3 步：抢 requestId 幂等标记
             *
             * 这里和上面的“一人一单”不是一回事：
             *
             * - 一人一单：限制“这个用户只能买一次这个商品”
             * - requestId 幂等：限制“同一次请求不要被重复执行”
             *
             * 也就是说，
             * 一人一单是业务规则，
             * requestId 幂等是防重复提交机制。
             */
            boolean idempotentLocked = redisOrderGuardService.tryMarkIdempotent(requestId);
            if (!idempotentLocked) {
                throw new RuntimeException("重复请求，请勿重复提交");
            }

            /**
             * 第 4 步：Redis 预扣库存
             *
             * 这是高并发入口的第一道库存裁决。
             * 先在 Redis 层快速判断并扣减，避免大量请求直接打到 MySQL。
             *
             * 返回值约定：
             * -2：库存还没预热到 Redis
             * -1：库存不足
             * 其他：预扣成功
             */
            long redisResult = redisStockService.tryDeductStock(productId, quantity);

            if (redisResult == -2) {
                preDeductStatus = "STOCK_NOT_PREHEATED";
                writeChainMetricsService.recordRedisPreDeductNotPreheated();
                /**
                 * 预扣根本没开始成功，所以要把幂等标记清掉，
                 * 否则这次失败请求会一直占着 requestId。
                 */
                redisOrderGuardService.clearIdempotent(requestId);
                throw new RuntimeException("Redis 库存未预热，暂无法下单");
            }

            if (redisResult == -1) {
                preDeductStatus = "INSUFFICIENT";
                writeChainMetricsService.recordRedisPreDeductInsufficient();
                /**
                 * Redis 已明确判定库存不足，
                 * 同样要清理这次 requestId 的幂等标记。
                 */
                redisOrderGuardService.clearIdempotent(requestId);
                throw new RuntimeException("库存不足（Redis 预扣失败）");
            }
            preDeductStatus = "SUCCESS";
            writeChainMetricsService.recordRedisPreDeductSuccess();

            /**
             * 第 5 步：标记订单状态为 PENDING
             *
             * 含义是：
             * 这次请求已经被系统接收，
             * Redis 预扣也成功了，
             * 接下来订单会进入 MQ 异步创建阶段。
             */
            redisOrderGuardService.markPending(userId, productId);

            /**
             * 第 6 步：发送订单创建消息
             *
             * 从这一步开始，请求线程的核心任务就完成了：
             * 它已经把“下单请求”安全地交给 MQ。
             *
             * 真正的订单落库、最终扣减 MySQL 库存，
             * 由消费者异步完成。
             *
             * 注意：
             * 这一版里，发送失败补偿统一收口到 Producer 层，
             * Gateway 不再自己做补偿，避免两边重复回滚。
             */
            orderMessageProducer.sendCreateOrderMessage(
                    new OrderCreateMessage(userId, productId, quantity, requestId)
            );
            mqSendStatus = "SUCCESS";
            writeChainMetricsService.recordMqSendSuccess();

            /**
             * 返回“已受理”而不是“已成功创建订单”。
             * 因为当前线程只负责入口受理，
             * 订单是否最终成功，要看消费者异步处理结果。
             */
            asyncAccepted = true;
            writeChainMetricsService.recordOrderGatewayAccepted();
            return new OrderSubmitTraceResult("订单请求已受理，正在异步处理中", preDeductStatus, mqSendStatus, asyncAccepted);

        } catch (RuntimeException e) {
            writeChainMetricsService.recordOrderGatewayRejected();
            throw e;
        } finally {
            /**
             * 第 7 步：释放入口锁
             *
             * 为什么这里就可以释放：
             * 因为入口线程的职责到“校验 + 预扣 + 发消息”就结束了，
             * 后续真正的订单创建已经交给异步消费者。
             *
             * 所以这里不需要等 MySQL 落库完成再解锁。
             */
            redisLockService.unlock(lockKey, lockValue);
        }
    }
}
