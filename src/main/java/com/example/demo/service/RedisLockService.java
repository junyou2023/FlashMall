package com.example.demo.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Service
public class RedisLockService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Lua 脚本：只有锁 value 匹配，才能删除
     *
     * 为什么必须这样？
     * 因为：
     * - 线程 A 拿到锁
     * - 锁过期后，线程 B 拿到新锁
     * - 如果线程 A 结束时直接 delete(key)，就会把线程 B 的锁删掉
     *
     * 所以释放锁时必须判断“是不是自己的锁”
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                            "return redis.call('del', KEYS[1]) " +
                            "else return 0 end",
                    Long.class
            );

    public RedisLockService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 构造订单锁 key
     * 作用域：同一个用户、同一个商品
     */
    public String buildOrderLockKey(Long userId, Long productId) {
        return "lock:order:user:" + userId + ":product:" + productId;
    }

    /**
     * 抢锁
     *
     * 返回锁 value（抢到锁时返回 uuid）
     * 抢不到返回 null
     */
    public String tryLock(String lockKey, long ttlSeconds) {
        String lockValue = UUID.randomUUID().toString();

        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockValue,
                Duration.ofSeconds(ttlSeconds)
        );

        return Boolean.TRUE.equals(success) ? lockValue : null;
    }

    /**
     * 安全释放锁
     */
    public void unlock(String lockKey, String lockValue) {
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(lockKey),
                lockValue
        );
    }
}