// src/main/java/com/example/demo/model/Order.java
package com.example.demo.model;

import java.time.LocalDateTime;
import java.util.List;

public class Order {

    private Long id;            // 订单ID
    private Long userId;        // 下单用户ID
    private String requestId;   // 业务请求幂等号：用于把“下单受理请求”和“异步落库订单”关联起来
    private double totalPrice;  // 订单总价
    private String status;      // 状态：CREATED / PAID / CANCELED ...
    private LocalDateTime createdAt;  // 创建时间

    // 方便后面扩展：一个订单包含多个明细
    private List<OrderItem> items;

    public Order() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }
}
