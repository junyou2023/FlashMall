package com.example.demo.controller;

import com.example.demo.service.DashboardAggregationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 聚合查询正式业务入口
 *
 * 【本轮改动】
 * 这个接口不是实验接口，它模拟的是典型“首页看板类”读请求：
 * - 同时需要 DB + Redis 多份数据
 * - 通过 queryTaskExecutor 并发读取
 * - 支持超时降级，避免单点拖垮整体 RT
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardAggregationService dashboardAggregationService;

    public DashboardController(DashboardAggregationService dashboardAggregationService) {
        this.dashboardAggregationService = dashboardAggregationService;
    }

    @GetMapping("/home")
    public Map<String, Object> home(
            @RequestParam(defaultValue = "1") Long productId,
            @RequestParam(defaultValue = "1") Long userId
    ) {
        return dashboardAggregationService.loadHome(productId, userId);
    }

    @GetMapping("/query-pool/stats")
    public Map<String, Object> queryPoolStats() {
        return dashboardAggregationService.queryPoolSnapshot();
    }
}
