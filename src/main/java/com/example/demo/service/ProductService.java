package com.example.demo.service;

import com.example.demo.mapper.ProductMapper;
import com.example.demo.model.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductCacheService productCacheService;

    @Value("${app.cache.product-ttl-seconds:300}")
    private long productTtlSeconds;

    @Value("${app.cache.product-null-ttl-seconds:60}")
    private long productNullTtlSeconds;

    /**
     * 热点 key 互斥锁过期时间
     * 不能太短：避免重建缓存时锁过早过期
     * 也不能太长：避免锁异常残留太久
     */
    @Value("${app.cache.product-lock-ttl-seconds:10}")
    private long productLockTtlSeconds;

    /**
     * 没抢到锁时，等待后重试的间隔
     */
    @Value("${app.cache.product-retry-sleep-millis:50}")
    private long retrySleepMillis;

    /**
     * 最多重试次数，避免无限递归/无限自旋
     */
    @Value("${app.cache.product-max-retry-times:5}")
    private int maxRetryTimes;

    /**
     * 空值标记：防缓存穿透
     */
    private static final String NULL_MARKER = "__NULL__";

    /**
     * 用 Lua 保证“只有锁的持有者才能删锁”
     * 避免：
     * 线程 A 锁过期后，线程 B 抢到新锁，
     * 线程 A 回来误删线程 B 的锁。
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else return 0 end",
            Long.class
    );

    public ProductService(ProductMapper productMapper,
                          RedisTemplate<String, Object> redisTemplate,
                          ProductCacheService productCacheService) {
        this.productMapper = productMapper;
        this.redisTemplate = redisTemplate;
        this.productCacheService = productCacheService;
    }

    /**
     * 商品列表：当前先不动，保持你现阶段已有方案即可
     * 如果你现在还是 @Cacheable，也可以先保留
     */
    public List<Product> getAllProducts() {
        return productMapper.findAll();
    }

    /**
     * 为Controller提供缓存清除功能
     * @param id 商品ID
     */
    public void evictProductCache(Long id) {
        productCacheService.evictAfterProductChanged(id);
    }

    /**
     * 商品详情缓存 key
     */
    private String productDetailKey(Long id) {
        return "product:detail:" + id;
    }

    /**
     * 商品详情锁 key
     */
    private String productLockKey(Long id) {
        return "lock:product:detail:" + id;
    }

    /**
     * 对外暴露的方法：商品详情
     * 当前阶段我们把：
     * - 缓存穿透
     * - 缓存击穿
     * 都集中在这里处理
     */
    public Product getProductById(Long id) {
        return getProductByIdWithRetry(id, 0);
    }

    /**
     * 真正的核心逻辑：
     * - Redis 正常对象命中
     * - Redis 空标记命中（防穿透）
     * - 互斥锁重建缓存（防击穿）
     */
    private Product getProductByIdWithRetry(Long id, int retryTimes) {
        String cacheKey = productDetailKey(id);

        // 1. 先查 Redis
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        // 2. 命中空标记：说明之前已经确认“商品不存在”
        if (NULL_MARKER.equals(cached)) {
            System.out.println("【命中空缓存】商品不存在，productId = " + id);
            return null;
        }

        // 3. 命中正常商品对象：直接返回
        if (cached instanceof Product) {
            System.out.println("【命中缓存】商品详情，productId = " + id);
            return (Product) cached;
        }

        // 4. Redis 没有命中，开始处理“缓存重建”
        String lockKey = productLockKey(id);
        String lockValue = UUID.randomUUID().toString();

        boolean locked = tryLock(lockKey, lockValue);

        if (locked) {
            try {
                /**
                 * 为什么抢到锁后还要再查一次 Redis？
                 *
                 * 因为在你抢锁成功之前，可能已经有别的线程刚刚把缓存建好了，
                 * 只是你第一次查缓存时还没看到。
                 *
                 * 这一步叫“双检”，可以减少不必要的 DB 查询。
                 */
                Object cachedAgain = redisTemplate.opsForValue().get(cacheKey);

                if (NULL_MARKER.equals(cachedAgain)) {
                    System.out.println("【双检命中空缓存】商品不存在，productId = " + id);
                    return null;
                }

                if (cachedAgain instanceof Product) {
                    System.out.println("【双检命中缓存】商品详情，productId = " + id);
                    return (Product) cachedAgain;
                }

                // 5. 只有抢到锁并且双检后仍无缓存的人，才真正去查 DB
                System.out.println("【查数据库并重建缓存】商品详情，productId = " + id);
                Product product = productMapper.findById(id);

                // 6. DB 也查不到：写空标记（防穿透）
                if (product == null) {
                    redisTemplate.opsForValue().set(
                            cacheKey,
                            NULL_MARKER,
                            Duration.ofSeconds(productNullTtlSeconds)
                    );
                    return null;
                }

                // 7. DB 查到了：写正常商品缓存
                redisTemplate.opsForValue().set(
                        cacheKey,
                        product,
                        Duration.ofSeconds(productTtlSeconds)
                );

                return product;
            } finally {
                // 8. 释放锁（Lua 保证只能删自己的锁）
                unlock(lockKey, lockValue);
            }
        }

        /**
         * 9. 没抢到锁：说明“已经有别的线程在重建缓存”
         *
         * 当前阶段最简单有效的做法：
         * - 不打 DB
         * - 稍微 sleep 一下
         * - 再查 Redis
         */
        if (retryTimes >= maxRetryTimes) {
            // 重试多次后仍不行，做一次兜底：
            // 当前阶段可接受的简化方案：直接查 DB（避免一直等待）
            System.out.println("【重试上限兜底】直接查数据库，productId = " + id);
            return productMapper.findById(id);
        }

        try {
            Thread.sleep(retrySleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return getProductByIdWithRetry(id, retryTimes + 1);
    }

    /**
     * 抢锁：
     * setIfAbsent = SETNX
     * 并设置过期时间，避免死锁
     */
    private boolean tryLock(String lockKey, String lockValue) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockValue,
                Duration.ofSeconds(productLockTtlSeconds)
        );
        return Boolean.TRUE.equals(success);
    }

    /**
     * 安全释放锁：
     * 只有锁的 value 和自己一致，才删
     */
    private void unlock(String lockKey, String lockValue) {
        redisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(lockKey),
                lockValue
        );
    }
}