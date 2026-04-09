package com.example.demo.dto;

/**
 * 发起 Stripe Checkout 请求
 *
 * 按当前阶段目标，前端只需要传 userId + requestId。
 * requestId 会映射回已经由 MQ 异步创建成功的 orders 记录。
 */
public class CreateStripeCheckoutRequest {

    private Long userId;
    private String requestId;

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
}
