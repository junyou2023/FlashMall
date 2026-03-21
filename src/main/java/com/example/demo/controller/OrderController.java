package com.example.demo.controller;

import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.service.OrderService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 当前阶段为了聚焦高并发主线，userId 先继续用固定值演示。
     * 后面你再接用户登录体系。
     */
    @PostMapping("/create")
    public Long placeOrder(@RequestBody CreateOrderRequest request) {
        Long userId = 1L;
        return orderService.placeOrder(
                userId,
                request.getProductId(),
                request.getQuantity(),
                request.getRequestId()
        );
    }
}