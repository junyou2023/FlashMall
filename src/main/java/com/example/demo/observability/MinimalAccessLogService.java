package com.example.demo.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 最小可观测性日志服务
 *
 * 目标：
 * - 不引入重型观测平台
 * - 先把“读链路 / 写链路 / 线程池实验链路”打上统一结构化日志
 * - 让压测时至少能对齐 requestId/traceId、耗时、成功失败和关键业务标签
 *
 * 【本轮改动】
 * 这是一个“最小实现”，核心是统一日志字段而不是追求完整链路追踪系统。
 */
@Service
public class MinimalAccessLogService {

    private static final Logger log = LoggerFactory.getLogger(MinimalAccessLogService.class);

    /**
     * 生成最小 traceId。
     *
     * 为什么不用复杂链路系统？
     * 当前阶段先保证“每次请求都能被唯一标识 + 能串起关键日志”即可。
     */
    public String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 统一输出最小可观测性日志。
     */
    public void logAccess(String traceId,
                          String requestId,
                          String path,
                          String method,
                          long costMs,
                          boolean success,
                          Map<String, Object> bizTags) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", traceId);
        payload.put("requestId", requestId);
        payload.put("path", path);
        payload.put("method", method);
        payload.put("costMs", costMs);
        payload.put("success", success);
        payload.put("bizTags", bizTags);

        if (success) {
            log.info("[MIN-OBS] {}", payload);
        } else {
            log.warn("[MIN-OBS] {}", payload);
        }
    }
}
