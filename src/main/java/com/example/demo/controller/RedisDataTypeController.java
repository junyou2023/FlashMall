// src/main/java/com/example/demo/controller/RedisDataTypeController.java
package com.example.demo.controller;

import com.example.demo.service.RedisDataTypeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 【新增】Redis 数据类型“可视化/可验证”接口
 *
 * 你学习 Redis 最容易卡住的点是：
 * - Java 里写了，但不知道 Redis 里变成了什么
 *
 * 所以这里提供一组“读 Redis 的 API”，让你不用一直切 redis-cli。
 *（当然我们仍然建议你配合 redis-cli 看 key，更直观。）
 */
@RestController
@RequestMapping("/redis")
public class RedisDataTypeController {

    private final RedisDataTypeService redisDataTypeService;

    public RedisDataTypeController(RedisDataTypeService redisDataTypeService) {
        this.redisDataTypeService = redisDataTypeService;
    }

    // ========== String：浏览量 ==========
    @GetMapping("/products/{id}/view-count")
    public Map<String, Object> viewCount(@PathVariable("id") long productId) {
        return Map.of(
                "productId", productId,
                "viewCount", redisDataTypeService.getProductViewCount(productId)
        );
    }

    // ========== ZSet：热门榜 ==========
    @GetMapping("/rank/top")
    public List<Map<String, Object>> topRank(@RequestParam(defaultValue = "10") int limit) {
        return redisDataTypeService.topViewedProducts(limit);
    }

    // ========== List：最近浏览 ==========
    @GetMapping("/users/{userId}/recent-views")
    public List<String> recentViews(
            @PathVariable long userId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return redisDataTypeService.getRecentViews(userId, limit);
    }

    // ========== Set：收藏 ==========
    @PostMapping("/users/{userId}/favorites/{productId}")
    public Map<String, Object> addFavorite(@PathVariable long userId, @PathVariable long productId) {
        long added = redisDataTypeService.addFavorite(userId, productId);
        return Map.of(
                "userId", userId,
                "productId", productId,
                "added", added,
                "isFavorite", redisDataTypeService.isFavorite(userId, productId)
        );
    }

    @DeleteMapping("/users/{userId}/favorites/{productId}")
    public Map<String, Object> removeFavorite(@PathVariable long userId, @PathVariable long productId) {
        long removed = redisDataTypeService.removeFavorite(userId, productId);
        return Map.of(
                "userId", userId,
                "productId", productId,
                "removed", removed,
                "isFavorite", redisDataTypeService.isFavorite(userId, productId)
        );
    }

    @GetMapping("/users/{userId}/favorites")
    public Set<String> listFavorites(@PathVariable long userId) {
        return redisDataTypeService.listFavorites(userId);
    }

    // ========== Hash：商品快照 ==========
    @GetMapping("/products/{id}/snapshot")
    public Map<Object, Object> snapshot(@PathVariable("id") long productId) {
        return redisDataTypeService.getProductSnapshot(productId);
    }

    @GetMapping("/products/{id}/snapshot/{field}")
    public Map<String, Object> snapshotField(
            @PathVariable("id") long productId,
            @PathVariable String field
    ) {
        return Map.of(
                "productId", productId,
                "field", field,
                "value", redisDataTypeService.getProductSnapshotField(productId, field)
        );
    }
}

