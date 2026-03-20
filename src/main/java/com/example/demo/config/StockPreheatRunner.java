package com.example.demo.config;

import com.example.demo.service.RedisStockService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class StockPreheatRunner implements CommandLineRunner {

    private final RedisStockService redisStockService;

    public StockPreheatRunner(RedisStockService redisStockService) {
        this.redisStockService = redisStockService;
    }

    @Override
    public void run(String... args) {
        redisStockService.preloadAllStocks();
    }
}