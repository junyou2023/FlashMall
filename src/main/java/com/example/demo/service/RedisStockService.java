package com.example.demo.service;

import com.example.demo.mapper.ProductMapper;
import com.example.demo.model.Product;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class RedisStockService {

    private final ProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Lua 原子扣减脚本
     *
     * 返回值约定：
     * -2 : Redis 里没有这个库存 key（未预热）
     * -1 : 库存不足
     * >=0: 扣减成功，返回扣减后的剩余库存
     *
     * 为什么要 Lua？
     * 因为“判断库存是否足够 + 扣减库存”必须在 Redis 内部一次原子执行，
     * 不能拆成 Java 里的 get 再 decrby，否则又会变成“查后改”。
     */
    private static final DefaultRedisScript<Long> STOCK_DEDUCT_SCRIPT =
            new DefaultRedisScript<>(
                    "local stock = redis.call('GET', KEYS[1]) " +
                            "if not stock then return -2 end " +
                            "stock = tonumber(stock) " +
                            "local qty = tonumber(ARGV[1]) " +
                            "if stock < qty then return -1 end " +
                            "return redis.call('DECRBY', KEYS[1], qty)",
                    Long.class
            );

    public RedisStockService(ProductMapper productMapper,
                             StringRedisTemplate stringRedisTemplate) {
        this.productMapper = productMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Redis 中库存 key 统一规范
     * 例如：stock:product:1
     */
    public String buildStockKey(Long productId) {
        return "stock:product:" + productId;
    }

    /**
     * 预热单个商品库存
     *
     * 作用：
     * 把 MySQL 中的库存复制一份到 Redis，作为高并发入口的库存副本。
     *
     * 当前阶段不设置 TTL：
     * 因为这是库存状态，不适合自然过期。
     */
    public void preloadProductStock(Long productId) {
        Product product = productMapper.findById(productId);
        if (product == null) {
            throw new RuntimeException("商品不存在，无法预热库存");
        }

        String stockKey = buildStockKey(productId);
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStock()));

        System.out.println("【库存预热】单个商品库存已写入 Redis，key = "
                + stockKey + ", stock = " + product.getStock());
    }

    /**
     * 批量预热全部商品库存
     * 适合当前 demo 项目启动时执行
     */
    public void preloadAllStocks() {
        List<Product> products = productMapper.findAll();
        for (Product product : products) {
            String stockKey = buildStockKey(product.getId());
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStock()));
        }
        System.out.println("【库存预热】全部商品库存已写入 Redis，共 " + products.size() + " 个商品");
    }

    /**
     * 查看 Redis 里某个商品当前库存（测试/排查用）
     */
    public Integer getRedisStock(Long productId) {
        String value = stringRedisTemplate.opsForValue().get(buildStockKey(productId));
        return value == null ? null : Integer.parseInt(value);
    }

    /**
     * Lua 原子扣减 Redis 库存
     *
     * 返回值：
     * -2 : 未预热
     * -1 : 库存不足
     * >=0: 扣减成功，返回剩余库存
     */
    public long tryDeductStock(Long productId, int quantity) {
        Long result = stringRedisTemplate.execute(
                STOCK_DEDUCT_SCRIPT,
                Collections.singletonList(buildStockKey(productId)),
                String.valueOf(quantity)
        );
        return result == null ? -2 : result;
    }

    /**
     * 如果后续 MySQL 事务失败，需要把 Redis 预扣的库存补回去
     *
     * 为什么需要这个方法？
     * 因为当前阶段我们已经让 Redis 先在入口预扣了，
     * 如果后面 MySQL 下单失败，而不把 Redis 补回去，
     * Redis 和 MySQL 的库存就会越来越偏。
     */
    public void rollbackStock(Long productId, int quantity) {
        stringRedisTemplate.opsForValue().increment(buildStockKey(productId), quantity);
        System.out.println("【库存回补】Redis 库存已回补，productId = "
                + productId + ", quantity = " + quantity);
    }

    /**
     * 删除库存 key（调试用）
     */
    public void deleteStockKey(Long productId) {
        stringRedisTemplate.delete(buildStockKey(productId));
    }
}