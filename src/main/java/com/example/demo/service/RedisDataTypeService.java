// src/main/java/com/example/demo/service/RedisDataTypeService.java
package com.example.demo.service;

import com.example.demo.model.Product;
import com.example.demo.redis.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 【新增】Redis 数据类型教学用 Service（和你电商项目“业务场景”强绑定）
 *
 * 目标：不是背命令，而是让你看到：
 * - String / Hash / List / Set / ZSet 各自擅长解决什么“业务问题”
 * - Java 代码里怎么写（StringRedisTemplate）
 * - Redis 里最终长什么样（redis-cli 能看到的 key/value）
 */
@Service
public class RedisDataTypeService {

    private final StringRedisTemplate redis;

    public RedisDataTypeService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 【核心演示】用户“浏览一次商品详情”时，我们顺手把 4 种类型的数据都记录下来。
     *
     * 场景为什么合理？
     * - 浏览量统计（String）
     * - 热门榜（ZSet）
     * - 用户最近浏览（List）
     * - 商品快照（Hash）
     */
    public void recordProductView(Long userId, Product product) {
        long pid = product.getId();

        // ========== 1) String：计数器（浏览量） ==========
        // Redis 命令等价：INCR product:view:count:{id}
        Long viewCount = redis.opsForValue().increment(RedisKeys.productViewCount(pid));

        // ========== 2) ZSet：排行榜（按 score 排序） ==========
        // Redis 命令等价：ZINCRBY product:view:rank 1 {id}
        Double newScore = redis.opsForZSet().incrementScore(RedisKeys.PRODUCT_VIEW_RANK, String.valueOf(pid), 1);

        // ========== 3) List：用户最近浏览（有序、可截断） ==========
        // 业务诉求：
        // - “最近浏览”要有顺序（最新的在最前）
        // - 数量不能无限增长（只保留最近 20 个）
        // - 允许重复吗？一般不想重复，所以我们先 remove 再 push
        String recentKey = RedisKeys.userRecentViews(userId);
        String pidStr = String.valueOf(pid);
        redis.opsForList().remove(recentKey, 0, pidStr);     // LREM recentKey 0 pid
        redis.opsForList().leftPush(recentKey, pidStr);      // LPUSH recentKey pid
        redis.opsForList().trim(recentKey, 0, 19);           // LTRIM recentKey 0 19
        redis.expire(recentKey, Duration.ofDays(7));         // 演示用：7 天

        // ========== 4) Hash：商品快照（按字段读写） ==========
        // 你现在的 Spring Cache 用的是“JSON 作为 value”（整体读写）。
        // Hash 的优势：
        // - 可以按字段读（比如只读 stock / price）
        // - 更新某一个字段不需要覆盖整段 JSON（但要配合一致性策略）
        String hashKey = RedisKeys.productHash(pid);
        Map<String, String> snapshot = new HashMap<>();
        snapshot.put("id", String.valueOf(product.getId()));
        snapshot.put("name", product.getName());
        snapshot.put("price", String.valueOf(product.getPrice()));
        snapshot.put("stock", String.valueOf(product.getStock()));
        redis.opsForHash().putAll(hashKey, snapshot);        // HMSET
        redis.expire(hashKey, Duration.ofMinutes(30));       // 快照 TTL

        // 【学习辅助日志】你要能“看见”每次请求干了什么
        System.out.println("[RedisDataTypeService] user=" + userId
                + " view product=" + pid
                + " | String(viewCount)=" + viewCount
                + " | ZSet(score)=" + newScore);
    }

    // ===================== String：浏览量 =====================
    public long getProductViewCount(long productId) {
        String v = redis.opsForValue().get(RedisKeys.productViewCount(productId));
        return v == null ? 0L : Long.parseLong(v);
    }

    // ===================== ZSet：热门榜 =====================
    public List<Map<String, Object>> topViewedProducts(int limit) {
        // ZREVRANGE product:view:rank 0 (limit-1) WITHSCORES
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet()
                .reverseRangeWithScores(RedisKeys.PRODUCT_VIEW_RANK, 0, limit - 1);

        if (tuples == null) return List.of();

        return tuples.stream().map(t -> {
            Map<String, Object> m = new HashMap<>();
            m.put("productId", t.getValue());
            m.put("views", t.getScore() == null ? 0 : t.getScore().longValue());
            return m;
        }).collect(Collectors.toList());
    }

    // ===================== List：最近浏览 =====================
    public List<String> getRecentViews(long userId, int limit) {
        String key = RedisKeys.userRecentViews(userId);
        return redis.opsForList().range(key, 0, limit - 1);
    }

    // ===================== Set：收藏 =====================
    public long addFavorite(long userId, long productId) {
        String key = RedisKeys.userFavorites(userId);
        Long added = redis.opsForSet().add(key, String.valueOf(productId)); // SADD
        redis.expire(key, Duration.ofDays(30)); // 演示：30 天
        return added == null ? 0L : added;
    }

    public long removeFavorite(long userId, long productId) {
        String key = RedisKeys.userFavorites(userId);
        Long removed = redis.opsForSet().remove(key, String.valueOf(productId)); // SREM
        return removed == null ? 0L : removed;
    }

    public Set<String> listFavorites(long userId) {
        return redis.opsForSet().members(RedisKeys.userFavorites(userId)); // SMEMBERS
    }

    public boolean isFavorite(long userId, long productId) {
        Boolean member = redis.opsForSet().isMember(RedisKeys.userFavorites(userId), String.valueOf(productId));
        return Boolean.TRUE.equals(member);
    }

    // ===================== Hash：商品快照 =====================
    public Map<Object, Object> getProductSnapshot(long productId) {
        return redis.opsForHash().entries(RedisKeys.productHash(productId)); // HGETALL
    }

    public String getProductSnapshotField(long productId, String field) {
        Object v = redis.opsForHash().get(RedisKeys.productHash(productId), field); // HGET
        return v == null ? null : v.toString();
    }
}

