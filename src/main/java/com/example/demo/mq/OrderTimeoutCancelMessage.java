package com.example.demo.mq;

import java.io.Serializable;

/**
 * 订单超时取消消息体
 *
 * 它不是“创建订单指令”，而是“未来触发取消”的提醒消息。
 * 当前阶段只放取消所需最小字段，先把 TTL + 超时取消主链路跑通。
 */
public class OrderTimeoutCancelMessage implements Serializable {

    private Long userId;
    private Long productId;
    private int quantity;
    private String requestId;

    public OrderTimeoutCancelMessage() {
    }

    public OrderTimeoutCancelMessage(Long userId, Long productId, int quantity, String requestId) {
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.requestId = requestId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
