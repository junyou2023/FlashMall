package com.example.demo.lab.controller;

import com.example.demo.lab.service.ThreadPoolLabService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池实验控制器
 *
 * 这不是正式业务接口，
 * 而是为了当前阶段验证线程池行为而加的实验入口。
 *
 * 【本轮改动】
 * 只在 perf 环境暴露，避免 dev/prod 环境误用实验接口。
 */
@RestController
@Profile("perf")
public class ThreadPoolLabController {

    private final ThreadPoolLabService threadPoolLabService;
    private final ThreadPoolTaskExecutor cacheTaskExecutor;

    public ThreadPoolLabController(ThreadPoolLabService threadPoolLabService,
                                   @Qualifier("cacheTaskExecutor") ThreadPoolTaskExecutor cacheTaskExecutor) {
        this.threadPoolLabService = threadPoolLabService;
        this.cacheTaskExecutor = cacheTaskExecutor;
    }

    /**
     * 查看当前线程池状态
     */
    @GetMapping("/lab/thread-pool/stats")
    public Map<String, Object> stats() {
        ThreadPoolExecutor executor = cacheTaskExecutor.getThreadPoolExecutor();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("corePoolSize", executor.getCorePoolSize());
        result.put("maxPoolSize", executor.getMaximumPoolSize());
        result.put("poolSize", executor.getPoolSize());
        result.put("activeCount", executor.getActiveCount());
        result.put("queueSize", executor.getQueue().size());
        result.put("remainingQueueCapacity", executor.getQueue().remainingCapacity());
        result.put("completedTaskCount", executor.getCompletedTaskCount());
        result.put("taskCount", executor.getTaskCount());
        result.put("largestPoolSize", executor.getLargestPoolSize());

        /**
         * 【本轮改动】
         * 增加实验计数器，便于判断是否触发 CallerRunsPolicy。
         */
        result.put("labCounters", threadPoolLabService.snapshotCounters());
        return result;
    }

    /**
     * 批量提交模拟任务
     *
     * 示例：
     * POST /lab/thread-pool/submit?taskCount=100&sleepMillis=1000
     *
     * 含义：
     * - 一次请求提交 100 个异步任务
     * - 每个任务执行 1000ms
     *
     * 当任务足够多时：
     * - 先占满核心线程
     * - 再塞满队列
     * - 再扩到 max
     * - 最后触发 CallerRunsPolicy
     */
    @PostMapping("/lab/thread-pool/submit")
    public Map<String, Object> submit(
            @RequestParam(defaultValue = "20") int taskCount,
            @RequestParam(defaultValue = "1000") long sleepMillis) {

        long start = System.currentTimeMillis();

        for (int i = 0; i < taskCount; i++) {
            String taskId = UUID.randomUUID().toString();
            threadPoolLabService.recordSubmit();
            threadPoolLabService.submitDummyTask(taskId, sleepMillis);
        }

        long cost = System.currentTimeMillis() - start;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submittedTaskCount", taskCount);
        result.put("sleepMillis", sleepMillis);
        result.put("submitRequestCostMs", cost);

        ThreadPoolExecutor executor = cacheTaskExecutor.getThreadPoolExecutor();
        result.put("currentPoolSize", executor.getPoolSize());
        result.put("currentActiveCount", executor.getActiveCount());
        result.put("currentQueueSize", executor.getQueue().size());
        result.put("labCounters", threadPoolLabService.snapshotCounters());

        return result;
    }
}
