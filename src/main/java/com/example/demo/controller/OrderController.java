package com.example.demo.controller;

import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.dto.OrderSubmitTraceResult;
import com.example.demo.observability.MinimalAccessLogService;
import com.example.demo.service.OrderGatewayService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单控制器
 *
 * 当前阶段它只做一件事：
 * 接受请求，然后交给异步下单入口服务。
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderGatewayService orderGatewayService;
    private final MinimalAccessLogService minimalAccessLogService;

    public OrderController(OrderGatewayService orderGatewayService,
                           MinimalAccessLogService minimalAccessLogService) {
        this.orderGatewayService = orderGatewayService;
        this.minimalAccessLogService = minimalAccessLogService;
    }

    /**
     * 异步下单入口
     *
     * 这里返回的不是订单号，
     * 而是“订单请求已受理，正在异步处理中”。
     */
    @PostMapping("/create")
    public String placeOrder(@RequestBody CreateOrderRequest request) {
        long start = System.currentTimeMillis();
        String traceId = minimalAccessLogService.newTraceId();
        String requestId = request.getRequestId();

        Map<String, Object> bizTags = new LinkedHashMap<>();
        bizTags.put("userId", request.getUserId());
        bizTags.put("productId", request.getProductId());
        bizTags.put("quantity", request.getQuantity());

        try {
            /**
             * 【本轮改动】
             * 去掉 userId = 1L 硬编码，改为从请求体显式传入。
             *
             * 【为什么改】
             * 写链路压测如果始终固定一个用户，会被“一人一单”和同 key 锁竞争严重扭曲，
             * 无法反映真实多用户并发下单行为。
             *
             * 所以这一步是“正式写链路压测闭环”的前提。
             */
            if (request.getUserId() == null) {
                throw new RuntimeException("userId 不能为空");
            }

            OrderSubmitTraceResult traceResult = orderGatewayService.submitOrderWithTrace(
                    request.getUserId(),
                    request.getProductId(),
                    request.getQuantity(),
                    requestId
            );

            bizTags.put("redisPreDeduct", traceResult.getPreDeductStatus());
            bizTags.put("mqSend", traceResult.getMqSendStatus());
            bizTags.put("asyncAccepted", traceResult.isAsyncAccepted());

            minimalAccessLogService.logAccess(
                    traceId,
                    requestId,
                    "/orders/create",
                    "POST",
                    System.currentTimeMillis() - start,
                    true,
                    bizTags
            );

            return traceResult.getMessage();
        } catch (RuntimeException e) {
            bizTags.put("error", e.getMessage());
            minimalAccessLogService.logAccess(
                    traceId,
                    requestId,
                    "/orders/create",
                    "POST",
                    System.currentTimeMillis() - start,
                    false,
                    bizTags
            );
            throw e;
        }
    }
}
