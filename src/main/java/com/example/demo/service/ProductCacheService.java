// src/main/java/com/example/demo/service/ProductCacheService.java
package com.example.demo.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ProductCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public ProductCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 商品详情缓存 key（必须和 ProductService 保持一致）
     *
     * 为什么这里要手写 key？
     * 因为：
     * 1. ProductService 用的是 redisTemplate 手写缓存
     * 2. 如果我用 @CacheEvict，Spring Cache 会按自己的规则拼接 key（双冒号）
     * 3. 那样会导致“写的 key”和“删的 key”不一致
     *
     * 所以最稳妥的方式：
     * - 统一用一套手写的 key 生成规则
     * - 保证写和删操作的是同一个 Redis key
     */
    private String productDetailKey(Long productId) {
        return "product:detail:" + productId;
    }

    /**
     * 删除某个商品详情缓存（直接操作 Redis）
     *
     * 为什么不使用 @CacheEvict？
     * 因为 Spring Cache 的 key 拼接规则和手写的不一致，
     * 会导致“以为删了，实际上没删到”。
     */
    public void evictProductDetail(Long productId) {
        String key = productDetailKey(productId);
        Boolean deleted = redisTemplate.delete(key);
        
        if (Boolean.TRUE.equals(deleted)) {
            System.out.println("【删除缓存】商品详情缓存已删除，key = " + key);
        } else {
            System.out.println("【删除缓存】商品详情缓存不存在，key = " + key);
        }
    }

    /**
     * 删除商品列表缓存
     *
     * allEntries = true 表示：
     * 把 product:list 这个缓存空间下的所有 key 都删掉
     *
     * 为什么列表也要删？
     * 因为商品库存变化后，列表页里展示的库存/状态也可能过期。
     */
    public void evictProductList() {
        // 简单方案：直接删整个 pattern
        // 生产环境可以用 scan + delete 分批处理
        String pattern = "product:list*";
        redisTemplate.delete(pattern);
        System.out.println("【删除缓存】商品列表缓存已删除，pattern = " + pattern);
    }

    /**
     * 商品数据发生变化后，统一删除相关缓存
     *
     * 这是一个“组合动作”：
     *  - 删商品详情缓存
     *  - 删商品列表缓存
     *
     * 当前项目阶段，这样做最简单、最稳妥、最容易讲清楚。
     */
    public void evictAfterProductChanged(Long productId) {
        evictProductDetail(productId);
        evictProductList();
    }
}