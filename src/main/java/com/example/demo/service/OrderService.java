package com.example.demo.service;

import com.example.demo.mapper.OrderItemMapper;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final ProductCacheService productCacheService;
    private final AsyncDeleteService asyncDeleteService;
    private final RedisStockService redisStockService;
    private final RedisOrderGuardService redisOrderGuardService;
    private final RedisLockService redisLockService;

    /**
     * 当前阶段演示锁 TTL：10 秒
     * demo 项目足够用
     */
    private static final long ORDER_LOCK_TTL_SECONDS = 10L;

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        ProductMapper productMapper,
                        ProductCacheService productCacheService,
                        AsyncDeleteService asyncDeleteService,
                        RedisStockService redisStockService,
                        RedisOrderGuardService redisOrderGuardService,
                        RedisLockService redisLockService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.productCacheService = productCacheService;
        this.asyncDeleteService = asyncDeleteService;
        this.redisStockService = redisStockService;
        this.redisOrderGuardService = redisOrderGuardService;
        this.redisLockService = redisLockService;
    }

}