package com.example.demo.lab.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程池实验服务
 *
 * 作用：
 * 专门用于向 cacheTaskExecutor 提交可控的模拟任务，
 * 方便你观察线程池在高并发下的行为。
 *
 * 【本轮改动】
 * 1) 该服务只在 perf 环境加载，避免污染日常开发和生产环境。
 * 2) 增加了实验计数器，帮助判断 CallerRunsPolicy 是否触发。
 */
@Service
@Profile("perf")
public class ThreadPoolLabService {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolLabService.class);

    private final String threadNamePrefix;

    /**
     * 实验计数器（只用于 perf 环境观测）
     *
     * totalSubmitted：收到 submit 请求时累计提交次数
     * totalExecuted：任务真正开始执行次数
     * callerRunsTriggered：检测到“非线程池前缀线程执行任务”的次数
     */
    private final AtomicLong totalSubmitted = new AtomicLong(0);
    private final AtomicLong totalExecuted = new AtomicLong(0);
    private final AtomicLong callerRunsTriggered = new AtomicLong(0);

    public ThreadPoolLabService(
            @Value("${app.async.cache.thread-name-prefix:cache-delete-}") String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    /**
     * 提交计数（在 Controller 中每次提交前调用）
     *
     * 【为什么改】
     * 仅靠线程池原生 completedTaskCount 很难关联“本轮实验提交量”，
     * 这里额外记录一次，方便压测时横向对比。
     */
    public void recordSubmit() {
        totalSubmitted.incrementAndGet();
    }

    /**
     * 提交一个模拟任务
     *
     * taskId：方便日志定位
     * sleepMillis：模拟任务耗时
     */
    @Async("cacheTaskExecutor")
    public void submitDummyTask(String taskId, long sleepMillis) {
        String threadName = Thread.currentThread().getName();
        long start = System.currentTimeMillis();
        totalExecuted.incrementAndGet();

        boolean callerRuns = !threadName.startsWith(threadNamePrefix);
        if (callerRuns) {
            long callerRunsCount = callerRunsTriggered.incrementAndGet();
            log.warn("【CallerRunsPolicy 触发】taskId={}，thread={}，callerRunsCount={}",
                    taskId, threadName, callerRunsCount);
        }

        log.info("【线程池任务开始】taskId={}，thread={}，sleepMillis={}", taskId, threadName, sleepMillis);

        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("【线程池任务中断】taskId={}，thread={}", taskId, threadName);
            return;
        }

        long cost = System.currentTimeMillis() - start;
        log.info("【线程池任务完成】taskId={}，thread={}，cost={}ms", taskId, threadName, cost);
    }


    /**
     * 【本轮改动】重置实验计数器
     *
     * 为什么要保留这个能力？
     * - 压测经常是“多轮对比”
     * - 如果计数器一直累加，不容易判断单轮实验效果
     *
     * 注意：
     * 这个方法只在 perf profile 下可用，不会影响 dev/prod。
     */
    public void resetCounters() {
        totalSubmitted.set(0);
        totalExecuted.set(0);
        callerRunsTriggered.set(0);
    }
    /**
     * 返回实验计数器快照
     */
    public Map<String, Object> snapshotCounters() {
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("totalSubmitted", totalSubmitted.get());
        counters.put("totalExecuted", totalExecuted.get());
        counters.put("callerRunsTriggered", callerRunsTriggered.get());
        return counters;
    }
}
