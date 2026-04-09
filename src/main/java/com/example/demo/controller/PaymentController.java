package com.example.demo.controller;

import com.example.demo.dto.CreateStripeCheckoutRequest;
import com.example.demo.dto.CreateStripeCheckoutResponse;
import com.example.demo.service.StripeCheckoutPaymentService;
import org.springframework.web.bind.annotation.*;

/**
 * 支付入口控制器（支付第一阶段）
 *
 * 责任边界：
 * 1. 接收前端“发起支付”请求
 * 2. 转发给支付服务
 * 3. 返回 checkoutUrl 给前端跳转
 *
 * 不负责 webhook、退款、超时取消等后续阶段逻辑。
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final StripeCheckoutPaymentService stripeCheckoutPaymentService;

    public PaymentController(StripeCheckoutPaymentService stripeCheckoutPaymentService) {
        this.stripeCheckoutPaymentService = stripeCheckoutPaymentService;
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
}
