// src/main/java/com/example/demo/service/ProductCacheService.java
package com.example.demo.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
public class ProductCacheService {

    /**
     * 删除某个商品详情缓存
     *
     * cacheNames = "product:detail"
     * key = "#productId"
     *
     * 对应 Redis 里大致会删掉：
     *   product:detail::1
     */
    @CacheEvict(cacheNames = "product:detail", key = "#productId")
    public void evictProductDetail(Long productId) {
        // 方法体可以为空
        // 因为真正的删除动作由 Spring Cache 代理帮你完成
        System.out.println("【删除缓存】商品详情缓存被删除，productId = " + productId);
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
    @CacheEvict(cacheNames = "product:list", allEntries = true)
    public void evictProductList() {
        System.out.println("【删除缓存】商品列表缓存被删除");
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