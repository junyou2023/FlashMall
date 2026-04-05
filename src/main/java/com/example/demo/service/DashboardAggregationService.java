package com.example.demo.service;

import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.observability.MinimalAccessLogService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 正式业务聚合查询服务
 *
 * 【本轮改动】
 * 新增一个更接近“正式业务主链路”的线程池治理场景：
 * - 接口：/dashboard/home
 * - 模式：CompletableFuture + queryTaskExecutor 并发拆分多个读任务
 *
 * 【它在系统里的角色】
 * - cacheTaskExecutor：偏“缓存一致性善后任务 + 实验田”
 * - queryTaskExecutor：偏“用户读请求主流程中的并发聚合查询”
 *
 * 所以 queryTaskExecutor 更接近“真实请求链路上的线程池治理问题”：
 * - 高并发时会排队
 * - 排队后 RT 会抬高
 * - 子任务超时时需要降级返回
 */
@Service
public class DashboardAggregationService {

    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final RedisStockService redisStockService;
    private final RedisDataTypeService redisDataTypeService;
    private final ThreadPoolTaskExecutor queryTaskExecutor;
    private final MinimalAccessLogService minimalAccessLogService;

    @Value("${app.dashboard.task-timeout-ms:180}")
    private long taskTimeoutMs;

    private final AtomicLong degradeCount = new AtomicLong(0);
    private final AtomicLong timeoutCount = new AtomicLong(0);

    public DashboardAggregationService(ProductMapper productMapper,
                                       OrderMapper orderMapper,
                                       RedisStockService redisStockService,
                                       RedisDataTypeService redisDataTypeService,
                                       @Qualifier("queryTaskExecutor") ThreadPoolTaskExecutor queryTaskExecutor,
                                       MinimalAccessLogService minimalAccessLogService) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.redisStockService = redisStockService;
        this.redisDataTypeService = redisDataTypeService;
        this.queryTaskExecutor = queryTaskExecutor;
        this.minimalAccessLogService = minimalAccessLogService;
    }

    public Map<String, Object> loadHome(Long productId, Long userId) {
        long start = System.currentTimeMillis();
        String traceId = minimalAccessLogService.newTraceId();
        String requestId = traceId;

        Map<String, Object> bizTags = new LinkedHashMap<>();
        bizTags.put("productId", productId);
        bizTags.put("userId", userId);

        CompletableFuture<Long> totalProductsFuture = CompletableFuture.supplyAsync(
                productMapper::countAllProducts,
                queryTaskExecutor
        );

        CompletableFuture<Long> totalOrdersFuture = CompletableFuture.supplyAsync(
                orderMapper::countAllOrders,
                queryTaskExecutor
        );

        CompletableFuture<Integer> redisStockFuture = CompletableFuture.supplyAsync(
                () -> redisStockService.getRedisStock(productId),
                queryTaskExecutor
        );

        CompletableFuture<List<Map<String, Object>>> hotProductsFuture = CompletableFuture.supplyAsync(
                () -> redisDataTypeService.topViewedProducts(5),
                queryTaskExecutor
        );

        Map<String, Object> dashboard = new LinkedHashMap<>();
        Map<String, Object> degrade = new LinkedHashMap<>();
        dashboard.put("totalProducts", awaitOrDegrade(totalProductsFuture, "totalProducts", 0L, degrade));
        dashboard.put("totalOrders", awaitOrDegrade(totalOrdersFuture, "totalOrders", 0L, degrade));
        dashboard.put("redisStock", awaitOrDegrade(redisStockFuture, "redisStock", -1, degrade));
        dashboard.put("hotProducts", awaitOrDegrade(hotProductsFuture, "hotProducts", List.of(), degrade));

        LocalDateTime latestOrderTime = orderMapper.latestOrderCreatedAt();
        dashboard.put("latestOrderTime", latestOrderTime);
        dashboard.put("distinctUsersOfProduct", orderMapper.countDistinctUsersByProduct(productId));

        ThreadPoolTaskExecutor executor = queryTaskExecutor;
        bizTags.put("degrade", !degrade.isEmpty());
        bizTags.put("degradeDetail", degrade);
        bizTags.put("activeCount", executor.getActiveCount());
        bizTags.put("queueSize", executor.getThreadPoolExecutor().getQueue().size());

        minimalAccessLogService.logAccess(
                traceId,
                requestId,
                "/dashboard/home",
                "GET",
                System.currentTimeMillis() - start,
                true,
                bizTags
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dashboard", dashboard);
        response.put("degrade", degrade);
        response.put("queryPool", queryPoolSnapshot());
        return response;
    }

    private <T> T awaitOrDegrade(CompletableFuture<T> future,
                                 String taskName,
                                 T fallback,
                                 Map<String, Object> degrade) {
        try {
            return future.get(taskTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            timeoutCount.incrementAndGet();
            degradeCount.incrementAndGet();
            degrade.put(taskName, "timeout-fallback");
            future.cancel(true);
            return fallback;
        } catch (Exception e) {
            degradeCount.incrementAndGet();
            degrade.put(taskName, "error-fallback:" + e.getClass().getSimpleName());
            return fallback;
        }
    }

    public Map<String, Object> queryPoolSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("activeCount", queryTaskExecutor.getActiveCount());
        snapshot.put("poolSize", queryTaskExecutor.getPoolSize());
        snapshot.put("queueSize", queryTaskExecutor.getThreadPoolExecutor().getQueue().size());
        snapshot.put("completedTaskCount", queryTaskExecutor.getThreadPoolExecutor().getCompletedTaskCount());
        snapshot.put("degradeCount", degradeCount.get());
        snapshot.put("timeoutCount", timeoutCount.get());
        return snapshot;
    }
}
