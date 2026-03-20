package com.example.demo.controller;

import com.example.demo.service.RedisStockService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/redis/stock")
public class StockGatewayController {

    private final RedisStockService redisStockService;

    public StockGatewayController(RedisStockService redisStockService) {
        this.redisStockService = redisStockService;
    }

    /**
     * 手动预热全部库存
     */
    @PostMapping("/preheat/all")
    public Map<String, Object> preloadAll() {
        redisStockService.preloadAllStocks();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "全部商品库存预热成功");
        return result;
    }

    /**
     * 手动预热单个商品库存
     */
    @PostMapping("/preheat/{productId}")
    public Map<String, Object> preloadOne(@PathVariable Long productId) {
        redisStockService.preloadProductStock(productId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("productId", productId);
        result.put("redisStock", redisStockService.getRedisStock(productId));
        return result;
    }

    /**
     * 查看 Redis 中的库存
     */
    @GetMapping("/{productId}")
    public Map<String, Object> getStock(@PathVariable Long productId) {
        Map<String, Object> result = new HashMap<>();
        result.put("productId", productId);
        result.put("redisStock", redisStockService.getRedisStock(productId));
        return result;
    }

    /**
     * 直接测试 Lua 原子扣减（便于你独立验证）
     */
    @PostMapping("/deduct/{productId}")
    public Map<String, Object> deduct(@PathVariable Long productId,
                                      @RequestParam int quantity) {
        long luaResult = redisStockService.tryDeductStock(productId, quantity);

        Map<String, Object> result = new HashMap<>();
        result.put("productId", productId);
        result.put("quantity", quantity);
        result.put("luaResult", luaResult);

        if (luaResult == -2) {
            result.put("message", "Redis 未预热库存");
        } else if (luaResult == -1) {
            result.put("message", "库存不足");
        } else {
            result.put("message", "扣减成功");
            result.put("remainStock", luaResult);
        }

        return result;
    }

    /**
     * 回补 Redis 库存（便于调试）
     */
    @PostMapping("/rollback/{productId}")
    public Map<String, Object> rollback(@PathVariable Long productId,
                                        @RequestParam int quantity) {
        redisStockService.rollbackStock(productId, quantity);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("productId", productId);
        result.put("redisStock", redisStockService.getRedisStock(productId));
        return result;
    }
}