package com.example.demo.controller;

import com.example.demo.dto.CreateStripeCheckoutRequest;
import com.example.demo.dto.CreateStripeCheckoutResponse;
import com.example.demo.service.StripeCheckoutPaymentService;
import com.example.demo.service.StripeWebhookService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * 支付入口控制器
 *
 * 当前分两段能力：
 * 1. 支付一期：发起 Stripe Checkout
 * 2. 支付二期：接收 Stripe Webhook 做服务端最终状态推进
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final StripeCheckoutPaymentService stripeCheckoutPaymentService;
    private final StripeWebhookService stripeWebhookService;

    public PaymentController(StripeCheckoutPaymentService stripeCheckoutPaymentService,
                             StripeWebhookService stripeWebhookService) {
        this.stripeCheckoutPaymentService = stripeCheckoutPaymentService;
        this.stripeWebhookService = stripeWebhookService;
    }

    /**
     * Stripe Checkout 发起接口
     *
     * 关键约束：
     * 前端传 userId + requestId，
     * 后端据此定位“已经异步创建完成”的订单再拉起支付。
     */
    @PostMapping("/stripe/checkout")
    public CreateStripeCheckoutResponse createStripeCheckout(@RequestBody CreateStripeCheckoutRequest request) {
        return stripeCheckoutPaymentService.createCheckout(request.getUserId(), request.getRequestId());
    }

    /**
     * Stripe Webhook 回调入口
     *
     * 为什么必须收原始字符串？
     * 因为验签要求 payload 字节级一致，不能先反序列化再组装。
     */
    @PostMapping(value = "/stripe/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String stripeWebhook(@RequestBody String payload,
                                @RequestHeader("Stripe-Signature") String stripeSignature) {
        stripeWebhookService.handleWebhook(payload, stripeSignature);
        return "ok";
    }
}
