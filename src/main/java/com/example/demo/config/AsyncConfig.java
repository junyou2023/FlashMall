package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${app.async.cache.core-pool-size:2}")
    private int corePoolSize;

    @Value("${app.async.cache.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${app.async.cache.queue-capacity:50}")
    private int queueCapacity;

    @Value("${app.async.cache.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Value("${app.async.cache.thread-name-prefix:cache-delete-}")
    private String threadNamePrefix;

    /**
     * 缓存异步任务线程池
     *
     * 这轮先只服务一个真实任务：
     * 延迟双删的第二次删缓存。
     *
     * 你现在先把它理解成：
     * “专门负责缓存类异步任务的后厨”
     */
    @Bean("cacheTaskExecutor")
    public Executor cacheTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 常驻线程数：平时主要靠它处理任务
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数：队列满了以后，线程池最多扩到多少
        executor.setMaxPoolSize(maxPoolSize);

        // 队列容量：核心线程忙不过来时，任务先排队
        executor.setQueueCapacity(queueCapacity);

        // 非核心线程空闲多久后回收
        executor.setKeepAliveSeconds(keepAliveSeconds);

        // 线程名前缀：方便日志和排查
        executor.setThreadNamePrefix(threadNamePrefix);

        /**
         * 拒绝策略：CallerRunsPolicy
         *
         * 含义：
         * 如果线程池和队列都满了，就让提交任务的线程自己执行任务。
         *
         * 当前阶段为什么选它？
         * 因为它不会直接丢任务，
         * 同时能把压力反推回调用方，是最适合你当前学习阶段的策略。
         */
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 应用关闭时，尽量等待已提交任务执行完
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);

        executor.initialize();
        return executor;
    }
}