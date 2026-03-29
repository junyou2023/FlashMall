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
     * 当前阶段我们不改业务逻辑，
     * 只做一件事：
     * 把它从“默认异步执行器”
     * 升级成“跑在你自己定义的业务线程池里”。
     */
    @Async("cacheTaskExecutor")
    public void delayedDeleteProductCache(Long productId, long delayMillis) {
        String threadName = Thread.currentThread().getName();

        System.out.println("【异步任务开始】线程 = " + threadName + "，productId = " + productId);

        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("【异步任务中断】线程 = " + threadName + "，productId = " + productId);
            return;
        }

        productCacheService.evictAfterProductChanged(productId);

        System.out.println("【延迟双删】第二次删除商品缓存完成，线程 = "
                + threadName + "，productId = " + productId);
    }
}