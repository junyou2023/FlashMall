package com.example.demo.controller;

import com.example.demo.observability.WriteChainMetricsService;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
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
 * 提供一个轻量只读入口，统一查看：
 * 1) 写链路关键计数器
 * 2) 聚合查询线程池状态
 * 3) RabbitMQ ready/unacked 口径（基于 RabbitAdmin）
 */
@RestController
@RequestMapping("/ops")
public class ObservabilityController {

    private final WriteChainMetricsService writeChainMetricsService;
    private final ThreadPoolTaskExecutor queryTaskExecutor;
    private final RabbitAdmin rabbitAdmin;
    private final String orderQueue;

    public ObservabilityController(WriteChainMetricsService writeChainMetricsService,
                                   @Qualifier("queryTaskExecutor") ThreadPoolTaskExecutor queryTaskExecutor,
                                   RabbitAdmin rabbitAdmin,
                                   @Value("${app.mq.order.queue:order.create.queue}") String orderQueue) {
        this.writeChainMetricsService = writeChainMetricsService;
        this.queryTaskExecutor = queryTaskExecutor;
        this.rabbitAdmin = rabbitAdmin;
        this.orderQueue = orderQueue;
    }

    @GetMapping("/write-chain/stats")
    public Map<String, Object> writeChainStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("writeChainCounters", writeChainMetricsService.snapshot());

        Properties properties = rabbitAdmin.getQueueProperties(orderQueue);
        Map<String, Object> mqGauge = new LinkedHashMap<>();
        mqGauge.put("queue", orderQueue);
        mqGauge.put("queueProbe", properties == null ? "queue-not-found-or-no-permission" : properties);
        mqGauge.put("note", "ready ~= messages, consumers 需结合 RabbitMQ 管理台看 unacked 与 backlog");
        result.put("mqGaugeHint", mqGauge);

        return result;
    }

    @GetMapping("/query-thread-pool/stats")
    public Map<String, Object> queryThreadPoolStats() {
        ThreadPoolExecutor executor = queryTaskExecutor.getThreadPoolExecutor();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("corePoolSize", executor.getCorePoolSize());
        result.put("maxPoolSize", executor.getMaximumPoolSize());
        result.put("poolSize", executor.getPoolSize());
        result.put("activeCount", executor.getActiveCount());
        result.put("queueSize", executor.getQueue().size());
        result.put("remainingQueueCapacity", executor.getQueue().remainingCapacity());
        result.put("completedTaskCount", executor.getCompletedTaskCount());
        return result;
    }
}
