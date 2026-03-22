package com.example.demo.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 订单守门服务
 *
 * 它管理两类非常关键的状态：
 *
 * 1. 一人一单状态
 *    key: order:once:{userId}:{productId}
 *    value:
 *      - PENDING 订单已受理，异步处理中
 *      - SUCCESS 订单已成功创建
 *
 * 2. 幂等状态
 *    key: order:idempotent:{requestId}
 *    value:
 *      - PROCESSING 请求正在处理
 *      - DONE      请求已处理完成
 */
@Service
public class RedisOrderGuardService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final long IDEMPOTENT_TTL_MINUTES = 10;
    private static final long PENDING_TTL_MINUTES = 15;

    public RedisOrderGuardService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String buildUserOrderOnceKey(Long userId, Long productId) {
        return "order:once:" + userId + ":" + productId;
    }

    public String buildIdempotentKey(String requestId) {
        return "order:idempotent:" + requestId;
    }

    /**
     * 获取当前用户对当前商品的订单状态
     * 可能返回：
     * - null
     * - PENDING
     * - SUCCESS
     */
    public String getOrderStatus(Long userId, Long productId) {
        return stringRedisTemplate.opsForValue().get(buildUserOrderOnceKey(userId, productId));
    }

    /**
     * 抢幂等标记
     *
     * 成功说明：
     * 当前是第一次处理这个 requestId
     *
     * 失败说明：
     * 这个 requestId 已经在处理中或处理过
     */
    public boolean tryMarkIdempotent(String requestId) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                buildIdempotentKey(requestId),
                "PROCESSING",
                Duration.ofMinutes(IDEMPOTENT_TTL_MINUTES)
        );
        return Boolean.TRUE.equals(success);
    }

    /**
     * 请求处理完成后，把幂等状态改成 DONE
     */
    public void markIdempotentDone(String requestId) {
        stringRedisTemplate.opsForValue().set(
                buildIdempotentKey(requestId),
                "DONE",
                Duration.ofMinutes(IDEMPOTENT_TTL_MINUTES)
        );
    }

    /**
     * 处理失败时，清理幂等标记
     * 这样用户后续还能重新发起请求
     */
    public void clearIdempotent(String requestId) {
        stringRedisTemplate.delete(buildIdempotentKey(requestId));
    }

    /**
     * 入口先写 PENDING
     *
     * 含义：
     * “这个用户对这个商品的订单请求已经受理，正在异步处理中”
     */
    public void markPending(Long userId, Long productId) {
        stringRedisTemplate.opsForValue().set(
                buildUserOrderOnceKey(userId, productId),
                "PENDING",
                Duration.ofMinutes(PENDING_TTL_MINUTES)
        );
    }

    /**
     * 消费者真正落库成功后，改成 SUCCESS
     *
     * 含义：
     * “这个用户对这个商品已经成功创建订单”
     */
    public void markSuccess(Long userId, Long productId) {
        stringRedisTemplate.opsForValue().set(
                buildUserOrderOnceKey(userId, productId),
                "SUCCESS"
        );
    }

    /**
     * 如果异步下单失败，要删掉 PENDING
     * 否则用户会一直卡在“处理中”
     */
    public void clearOrderStatus(Long userId, Long productId) {
        stringRedisTemplate.delete(buildUserOrderOnceKey(userId, productId));
    }
}