package com.example.demo.dto;

/**
 * Stripe Checkout 发起响应
 *
 * 这个返回结构面向支付一期：
 * 前端只要拿到 checkoutUrl，就可以跳转到 Stripe 托管收银台。
 */
public class CreateStripeCheckoutResponse {

    private Long orderId;
    private Long paymentOrderId;
    private String paymentStatus;
    private String checkoutUrl;
    private String message;

    public static CreateStripeCheckoutResponse waiting(String message) {
        CreateStripeCheckoutResponse response = new CreateStripeCheckoutResponse();
        response.setPaymentStatus("WAIT_ORDER_CREATED");
        response.setMessage(message);
        return response;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getPaymentOrderId() {
        return paymentOrderId;
    }

    public void setPaymentOrderId(Long paymentOrderId) {
        this.paymentOrderId = paymentOrderId;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
