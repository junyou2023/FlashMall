package com.example.demo.controller;

import com.example.demo.lab.service.ThreadPoolLabService;
import com.example.demo.observability.WriteChainMetricsService;
import com.example.demo.service.DashboardAggregationService;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 观测辅助接口（最小实现）
 *
 * 【本轮改动】
 * 在不推翻现有最小可观测性方案的前提下，补一个更友好的总览入口：
 * 1) 保留原有细分接口，兼容已有脚本和笔记口径
 * 2) 新增 /ops/overview，让浏览器一次打开就能看“写链路 + 线程池 + MQ 提示”
 *
 * 【注意】
 * 这个 Controller 仍然是“只读观测入口”，不是重型监控平台：
 * - JMeter 负责可视化发压
 * - /ops/** 负责暴露项目内部最小可观测口径
 * - 更完整的历史指标与看板，需配合 Actuator + Prometheus + Grafana
 */
@RestController
@RequestMapping("/ops")
public class ObservabilityController {

    private final WriteChainMetricsService writeChainMetricsService;
    private final DashboardAggregationService dashboardAggregationService;
    private final ThreadPoolTaskExecutor queryTaskExecutor;
    private final ThreadPoolTaskExecutor cacheTaskExecutor;
    private final RabbitAdmin rabbitAdmin;
    private final ObjectProvider<ThreadPoolLabService> threadPoolLabServiceProvider;
    private final String orderQueue;

    public ObservabilityController(WriteChainMetricsService writeChainMetricsService,
                                   DashboardAggregationService dashboardAggregationService,
                                   @Qualifier("queryTaskExecutor") ThreadPoolTaskExecutor queryTaskExecutor,
                                   @Qualifier("cacheTaskExecutor") ThreadPoolTaskExecutor cacheTaskExecutor,
                                   RabbitAdmin rabbitAdmin,
                                   ObjectProvider<ThreadPoolLabService> threadPoolLabServiceProvider,
                                   @Value("${app.mq.order.queue:order.create.queue}") String orderQueue) {
        this.writeChainMetricsService = writeChainMetricsService;
        this.dashboardAggregationService = dashboardAggregationService;
        this.queryTaskExecutor = queryTaskExecutor;
        this.cacheTaskExecutor = cacheTaskExecutor;
        this.rabbitAdmin = rabbitAdmin;
        this.threadPoolLabServiceProvider = threadPoolLabServiceProvider;
        this.orderQueue = orderQueue;
    }

    @GetMapping("/write-chain/stats")
    public Map<String, Object> writeChainStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("writeChainCounters", writeChainMetricsService.snapshot());
        result.put("mqGaugeHint", mqGaugeHint());
        return result;
    }

    @GetMapping("/query-thread-pool/stats")
    public Map<String, Object> queryThreadPoolStats() {
        return executorSnapshot(queryTaskExecutor);
    }

    /**
     * 聚合总览（本轮新增）
     *
     * 为什么要有这个接口？
     * - 之前指标分散在 /ops/** /dashboard/** /lab/**
     * - 压测时想“先看全貌再深挖”不够顺手
     *
     * 所以这里做一个只读聚合视图：
     * 1) 写链路计数器
     * 2) queryTaskExecutor 状态（正式聚合查询线程池）
     * 3) cacheTaskExecutor 状态（延迟双删 + 实验任务承载池）
     * 4) perf 环境下的实验计数器（如果 ThreadPoolLabService 可用）
     * 5) MQ 队列口径提示（ready 近似值 + 管理台核对建议）
     */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("readme", "只读总览：先看全局趋势，再到 /ops/** /dashboard/** /lab/** 深挖细节");

        result.put("writeChain", writeChainMetricsService.snapshot());

        Map<String, Object> queryPool = new LinkedHashMap<>();
        queryPool.put("rawExecutor", executorSnapshot(queryTaskExecutor));
        queryPool.put("dashboardAggregation", dashboardAggregationService.queryPoolSnapshot());
        result.put("queryTaskExecutor", queryPool);

        Map<String, Object> cachePool = new LinkedHashMap<>();
        cachePool.put("rawExecutor", executorSnapshot(cacheTaskExecutor));

        ThreadPoolLabService labService = threadPoolLabServiceProvider.getIfAvailable();
        if (labService != null) {
            cachePool.put("labCounters", labService.snapshotCounters());
            cachePool.put("labEntryHint", "perf 环境可配合 /lab/thread-pool/submit 做可控实验");
        } else {
            cachePool.put("labCounters", "N/A (当前 profile 未启用 lab 模块)");
        }
        result.put("cacheTaskExecutor", cachePool);

        result.put("mqGaugeHint", mqGaugeHint());
        result.put("deeperDiveEndpoints", Map.of(
                "writeChain", "/ops/write-chain/stats",
                "queryPool", "/ops/query-thread-pool/stats",
                "dashboardQueryPool", "/dashboard/query-pool/stats",
                "labPool", "/lab/thread-pool/stats"
        ));
        return result;
    }

    private Map<String, Object> executorSnapshot(ThreadPoolTaskExecutor executor) {
        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("corePoolSize", threadPoolExecutor.getCorePoolSize());
        result.put("maxPoolSize", threadPoolExecutor.getMaximumPoolSize());
        result.put("poolSize", threadPoolExecutor.getPoolSize());
        result.put("activeCount", threadPoolExecutor.getActiveCount());
        result.put("queueSize", threadPoolExecutor.getQueue().size());
        result.put("remainingQueueCapacity", threadPoolExecutor.getQueue().remainingCapacity());
        result.put("completedTaskCount", threadPoolExecutor.getCompletedTaskCount());
        return result;
    }

    private Map<String, Object> mqGaugeHint() {
        Properties properties = rabbitAdmin.getQueueProperties(orderQueue);
        Map<String, Object> mqGauge = new LinkedHashMap<>();
        mqGauge.put("queue", orderQueue);
        mqGauge.put("queueProbe", properties == null ? "queue-not-found-or-no-permission" : properties);
        mqGauge.put("note", "ready ~= messages；unacked/backlog 需结合 RabbitMQ 管理台核对");
        return mqGauge;
    }
}
