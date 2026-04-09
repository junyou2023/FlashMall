package com.example.demo.service;

import com.example.demo.config.StripeProperties;
import com.example.demo.dto.CreateStripeCheckoutResponse;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.PaymentOrderMapper;
import com.example.demo.model.Order;
import com.example.demo.model.PaymentOrder;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Stripe Checkout 支付发起服务（支付一期）
 *
 * 这个服务只负责一件事：
 * 在“订单已经异步创建成功”之后，给前端返回可跳转的 checkoutUrl。
 *
 * 关键原则：
 * 1. 不改原有下单主链路
 * 2. 订单仍由 MQ 消费端先创建为 CREATED
 * 3. 支付只是在订单之后追加一个独立阶段
 */
@Service
public class StripeCheckoutPaymentService {

    private static final String PROVIDER_STRIPE = "STRIPE";
    private static final String PAYMENT_STATUS_INIT = "INIT";
    private static final String PAYMENT_STATUS_WAIT_PAY = "WAIT_PAY";

    private final OrderMapper orderMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final StripeProperties stripeProperties;

    public StripeCheckoutPaymentService(OrderMapper orderMapper,
                                        PaymentOrderMapper paymentOrderMapper,
                                        StripeProperties stripeProperties) {
        this.orderMapper = orderMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.stripeProperties = stripeProperties;
    }

    /**
     * 发起 Stripe Checkout
     *
     * 入参使用 userId + requestId，
     * 与现有异步下单链路保持一致，避免让前端感知 MQ 内部细节。
     */
    @Transactional
    public CreateStripeCheckoutResponse createCheckout(Long userId, String requestId) {
        if (userId == null) {
            throw new RuntimeException("userId 不能为空");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new RuntimeException("requestId 不能为空");
        }

        /**
         * 第 1 步：根据 requestId 找异步订单
         *
         * 这一层明确遵循“先有订单，再发起支付”：
         * 如果订单还没被消费者创建出来，直接返回等待提示。
         */
        Order order = orderMapper.findByRequestId(requestId);
        if (order == null) {
            return CreateStripeCheckoutResponse.waiting("订单尚未创建完成，请稍后重试支付");
        }

        // 防止用户串单：requestId 虽然全局唯一，但支付入口依然要校验 userId 一致。
        if (!userId.equals(order.getUserId())) {
            throw new RuntimeException("订单与当前用户不匹配");
        }

        /**
         * 第 2 步：只允许 CREATED 状态发起支付
         *
         * 支付一期暂不处理 PAID/FAIL 的最终流转，
         * 所以先把入口收紧，避免非待支付订单被重复拉起会话。
         */
        if (!"CREATED".equals(order.getStatus())) {
            throw new RuntimeException("当前订单状态不允许发起支付，status=" + order.getStatus());
        }

        /**
         * 第 3 步：优先复用未过期会话
         *
         * 这样可以避免用户多次点击导致重复创建 Stripe Session。
         */
        PaymentOrder reusable = paymentOrderMapper.findReusableUnpaidByOrderId(order.getId(), PROVIDER_STRIPE);
        if (reusable != null) {
            return buildResponse(order, reusable, "已复用历史未完成支付会话");
        }

        /**
         * 第 4 步：创建本地 payment_order 占位记录
         *
         * 先落本地，再调第三方，便于排障追踪。
         */
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(stripeProperties.getCheckoutExpireMinutes());

        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setOrderId(order.getId());
        paymentOrder.setUserId(userId);
        paymentOrder.setProvider(PROVIDER_STRIPE);
        paymentOrder.setOutTradeNo(generateOutTradeNo());
        paymentOrder.setAmount(order.getTotalPrice());
        paymentOrder.setCurrency(stripeProperties.getCurrency());
        paymentOrder.setStatus(PAYMENT_STATUS_INIT);
        paymentOrder.setIdempotencyKey(generateIdempotencyKey(requestId));
        paymentOrder.setExpiresAt(expiresAt);
        paymentOrder.setCreatedAt(now);
        paymentOrder.setUpdatedAt(now);

        int inserted = paymentOrderMapper.insert(paymentOrder);
        if (inserted != 1) {
            throw new RuntimeException("创建本地支付单失败");
        }

        /**
         * 第 5 步：调用 Stripe 创建 Checkout Session，并回填本地支付单
         */
        Session session = createStripeSession(order, paymentOrder, expiresAt);

        paymentOrder.setStatus(PAYMENT_STATUS_WAIT_PAY);
        paymentOrder.setStripeCheckoutSessionId(session.getId());
        paymentOrder.setStripePaymentIntentId(session.getPaymentIntent() == null ? null : session.getPaymentIntent());
        paymentOrder.setCheckoutUrl(session.getUrl());
        paymentOrder.setUpdatedAt(LocalDateTime.now());

        int updated = paymentOrderMapper.updateStripeSessionInfo(paymentOrder);
        if (updated != 1) {
            throw new RuntimeException("回填 Stripe 会话信息失败");
        }

        return buildResponse(order, paymentOrder, "支付会话创建成功");
    }

    private Session createStripeSession(Order order, PaymentOrder paymentOrder, LocalDateTime expiresAt) {
        if (stripeProperties.getSecretKey() == null || stripeProperties.getSecretKey().isBlank()) {
            throw new RuntimeException("Stripe secretKey 未配置，请先在配置文件填写 app.payment.stripe.secret-key");
        }

        Stripe.apiKey = stripeProperties.getSecretKey();

        long amountInCents = Math.round(order.getTotalPrice() * 100);
        if (amountInCents <= 0) {
            throw new RuntimeException("订单金额非法，无法发起支付");
        }

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(stripeProperties.getSuccessUrl())
                .setCancelUrl(stripeProperties.getCancelUrl())
                .setClientReferenceId(String.valueOf(order.getId()))
                .putMetadata("orderId", String.valueOf(order.getId()))
                .putMetadata("requestId", order.getRequestId())
                .putMetadata("paymentOrderId", String.valueOf(paymentOrder.getId()))
                .setExpiresAt(expiresAt.toEpochSecond(ZoneOffset.UTC))
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(paymentOrder.getCurrency())
                                                .setUnitAmount(amountInCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("FlashMall Order #" + order.getId())
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(paymentOrder.getIdempotencyKey())
                .build();

        try {
            return Session.create(params, requestOptions);
        } catch (StripeException e) {
            throw new RuntimeException("创建 Stripe Checkout Session 失败: " + e.getMessage(), e);
        }
    }

    private CreateStripeCheckoutResponse buildResponse(Order order, PaymentOrder paymentOrder, String message) {
        CreateStripeCheckoutResponse response = new CreateStripeCheckoutResponse();
        response.setOrderId(order.getId());
        response.setPaymentOrderId(paymentOrder.getId());
        response.setPaymentStatus(paymentOrder.getStatus());
        response.setCheckoutUrl(paymentOrder.getCheckoutUrl());
        response.setMessage(message);
        return response;
    }

    private String generateOutTradeNo() {
        return "PO" + Instant.now().toEpochMilli() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String generateIdempotencyKey(String requestId) {
        return "stripe-checkout-" + requestId + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}
