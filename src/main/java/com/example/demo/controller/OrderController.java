package com.example.demo.controller;

import com.example.demo.dto.CreateOrderRequest;
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
    public String placeOrder(@RequestBody CreateOrderRequest request) {
        Long userId = 1L; // 当前阶段继续用固定 userId 演示

        return orderGatewayService.submitOrder(
                userId,
                request.getProductId(),
                request.getQuantity(),
                request.getRequestId()
        );
    }
}