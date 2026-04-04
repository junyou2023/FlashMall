package com.example.demo.dto;

public class CreateOrderRequest {

    /**
     * 【本轮改动】新增 userId 显式入参
     *
     * 【为什么改】
     * 之前写链路在 Controller 层固定 userId=1L，
     * 这会导致压测时所有请求都挤在“同一个用户”维度，
     * 无法模拟真实的多用户并发场景。
     *
     * 这会直接影响：
     * - 一人一单拦截比例
     * - Redis 锁竞争形态
     * - MQ 入队节奏
     * - 最终订单对账结果
     *
     * 所以这次改成“请求显式携带 userId”，
     * 这是写链路真实压测的前提条件。
     */
    private Long userId;

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
