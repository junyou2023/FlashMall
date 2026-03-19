package com.example.demo.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncDeleteService {

    private final ProductCacheService productCacheService;

    public AsyncDeleteService(ProductCacheService productCacheService) {
        this.productCacheService = productCacheService;
    }

    /**
     * 延迟双删的第二次删除
     *
     * 当前阶段我们用最轻量的方式：
     * - 异步
     * - sleep 一小段时间
     * - 再删一次缓存
     *
     * 这不是最终生产级最优方案，
     * 但非常适合你当前项目阶段：
     * 1. 容易理解
     * 2. 容易验证
     * 3. 面试时也讲得清楚
     */
    @Async
    public void delayedDeleteProductCache(Long productId, long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        productCacheService.evictAfterProductChanged(productId);

        System.out.println("【延迟双删】第二次删除商品缓存完成，productId = " + productId);
    }
}