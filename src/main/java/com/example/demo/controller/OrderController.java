package com.example.demo.controller;

import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.dto.OrderSubmitTraceResult;
import com.example.demo.observability.annotation.BizTrace;
import com.example.demo.service.OrderGatewayService;
import org.springframework.web.bind.annotation.*;

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

    public OrderController(OrderGatewayService orderGatewayService) {
        this.orderGatewayService = orderGatewayService;
    }

    /**
     * 异步下单入口
     *
     * 这里返回的不是订单号，
     * 而是“订单请求已受理，正在异步处理中”。
     */
    @PostMapping("/create")
    @BizTrace(
            scene = "order.create.entry",
            path = "/orders/create",
            method = "POST",
            requestIdSpel = "#request.requestId",
            bizTagSpel = "{'userId': #request.userId, 'productId': #request.productId, 'quantity': #request.quantity}"
    )
    public String placeOrder(@RequestBody CreateOrderRequest request) {
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
                request.getRequestId()
        );

        return traceResult.getMessage();
    }
}
