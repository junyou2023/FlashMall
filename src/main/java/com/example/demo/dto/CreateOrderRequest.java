package com.example.demo.dto;

public class CreateOrderRequest {

    private Long productId;
    private int quantity;

    /**
     * 幂等请求号
     * 当前阶段可以让前端传，也可以测试时手动传。
     *
     * 以后真正生产环境里，它可以来自：
     * - 前端生成 UUID
     * - 网关注入
     * - 调用链唯一请求号
     */
    private String requestId;

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