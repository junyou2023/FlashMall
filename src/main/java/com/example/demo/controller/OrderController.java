package com.example.demo.controller;

import com.example.demo.service.OrderService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/create")
    public Long placeOrder(@RequestBody CreateOrderRequest request) {
        Long userId = 1L;
        return orderService.placeOrder(userId, request.getProductId(), request.getQuantity());
    }

}
