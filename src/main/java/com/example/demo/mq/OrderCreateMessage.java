package com.example.demo.mq;

import java.io.Serializable;

/**
 * 订单创建消息体
 *
 * 这是“入口线程”发给 MQ 的消息内容。
 * MQ 消费者收到它之后，才真正去落 MySQL。
 *
 * 为什么只放这些字段？
 * 因为它们就是“异步创建订单”所必需的最小信息。
 */
public class OrderCreateMessage implements Serializable {

    /**
     * 下单用户 id
     */
    private Long userId;

    /**
     * 商品 id
     */
    private Long productId;

    /**
     * 购买数量
     */
    private int quantity;

    /**
     * 幂等请求号
     *
     * 未来消费端也可以继续基于它做幂等控制。
     */
    private String requestId;

    public OrderCreateMessage() {}

    public OrderCreateMessage(Long userId, Long productId, int quantity, String requestId) {
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.requestId = requestId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}