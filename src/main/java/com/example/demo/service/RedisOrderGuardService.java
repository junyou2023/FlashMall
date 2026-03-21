package com.example.demo.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisOrderGuardService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 幂等标记 TTL：
     * 当前阶段建议设置一个适中的时间，避免异常情况下永久占住。
     * 比如 5 分钟。
     */
    private static final long IDEMPOTENT_TTL_MINUTES = 5;

    public RedisOrderGuardService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 一人一单 key
     */
    public String buildUserOrderOnceKey(Long userId, Long productId) {
        return "order:once:" + userId + ":" + productId;
    }

    /**
     * 幂等标记 key
     */
    public String buildIdempotentKey(String requestId) {
        return "order:idempotent:" + requestId;
    }

    /**
     * 判断是否已经成功下过单（一人一单）
     */
    public boolean hasOrdered(Long userId, Long productId) {
        String key = buildUserOrderOnceKey(userId, productId);
        Boolean exists = stringRedisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 尝试抢占幂等标记
     *
     * 成功：说明这是第一次处理这个 requestId
     * 失败：说明这个 requestId 已经被处理/处理中
     */
    public boolean tryMarkIdempotent(String requestId) {
        String key = buildIdempotentKey(requestId);
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                key,
                "PROCESSING",
                Duration.ofMinutes(IDEMPOTENT_TTL_MINUTES)
        );
        return Boolean.TRUE.equals(success);
    }

    /**
     * 下单成功后，记录一人一单标记
     *
     * 当前阶段先不设置 TTL，
     * 因为这个业务规则通常就是长期有效的。
     */
    public void markOrdered(Long userId, Long productId) {
        String key = buildUserOrderOnceKey(userId, productId);
        stringRedisTemplate.opsForValue().set(key, "1");
    }

    /**
     * 处理失败时，删除幂等标记，让用户后续可以重新发起请求
     */
    public void clearIdempotent(String requestId) {
        stringRedisTemplate.delete(buildIdempotentKey(requestId));
    }
}