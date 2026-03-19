// src/main/java/com/example/demo/redis/RedisKeys.java
package com.example.demo.redis;

/**
 * 【新增】统一管理 Redis Key 命名（非常重要）
 *
 * 你可以把 Redis Key 当成“表名 + 主键”的组合：
 * - 只要命名一致，你就能像查表一样定位数据
 * - 也方便你用 redis-cli 直接验证
 */
public final class RedisKeys {

    private RedisKeys() {}

    // ========== String：计数器 ==========
    // 例：product:view:count:1 -> "123"（浏览次数）
    public static String productViewCount(long productId) {
        return "product:view:count:" + productId;
    }

    // ========== ZSet：排行榜 ==========
    // 例：product:view:rank  member=商品id  score=浏览量
    public static final String PRODUCT_VIEW_RANK = "product:view:rank";

    // ========== List：用户最近浏览 ==========
    // 例：user:1:recent_views -> ["10","7","1",...]
    public static String userRecentViews(long userId) {
        return "user:" + userId + ":recent_views";
    }

    // ========== Set：用户收藏 ==========
    // 例：user:1:favorites -> {"1","2","9"}
    public static String userFavorites(long userId) {
        return "user:" + userId + ":favorites";
    }

    // ========== Hash：商品快照（可按字段读取） ==========
    // 例：product:hash:1  {id:1, name:"iPhone", price:"899", stock:"48"}
    public static String productHash(long productId) {
        return "product:hash:" + productId;
    }
}

