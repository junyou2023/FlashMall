package com.example.demo.service;

import com.example.demo.config.StripeProperties;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.PaymentOrderMapper;
import com.example.demo.mapper.PaymentWebhookEventMapper;
import com.example.demo.model.PaymentOrder;
import com.example.demo.model.PaymentWebhookEvent;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Stripe Webhook 处理服务（支付第二阶段）
 *
 * 这层是“服务端支付最终真相入口”：
 * 1. 先验签，确认事件可信
 * 2. 先去重，避免重复推进状态
 * 3. 再按事件类型更新 payment_order / orders
 *
 * 为什么强调这个顺序：
 * webhook 是至少一次投递，重复是常态。
 * 如果不先去重，状态就可能被同一事件多次推进。
 */
@Service
public class StripeWebhookService {

    private static final String PROVIDER_STRIPE = "STRIPE";

    private final StripeProperties stripeProperties;
    private final PaymentWebhookEventMapper paymentWebhookEventMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final OrderMapper orderMapper;

    public StripeWebhookService(StripeProperties stripeProperties,
                                PaymentWebhookEventMapper paymentWebhookEventMapper,
                                PaymentOrderMapper paymentOrderMapper,
                                OrderMapper orderMapper) {
        this.stripeProperties = stripeProperties;
        this.paymentWebhookEventMapper = paymentWebhookEventMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.orderMapper = orderMapper;
    }

    /**
     * 处理 Stripe webhook 原始报文
     *
     * 注意：
     * 这里必须接收原始 payload + Stripe-Signature，
     * 不能提前反序列化普通 DTO，否则签名校验会失真。
     */
    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        if (payload == null || payload.isBlank()) {
            throw new RuntimeException("webhook payload 不能为空");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new RuntimeException("缺少 Stripe-Signature 请求头");
        }
        if (stripeProperties.getWebhookSecret() == null || stripeProperties.getWebhookSecret().isBlank()) {
            throw new RuntimeException("Stripe webhook secret 未配置，请先设置 app.payment.stripe.webhook-secret");
        }

        /**
         * 第 1 步：验签并解析 Event
         *
         * 验签通过才代表“这是 Stripe 发来的可信事件”。
         */
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, stripeProperties.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            throw new RuntimeException("Stripe webhook 验签失败", e);
        }

        /**
         * 第 2 步：事件去重落库
         *
         * 先 INSERT IGNORE，利用 (provider, provider_event_id) 唯一约束拦重复。
         */
        PaymentWebhookEvent webhookEvent = new PaymentWebhookEvent();
        webhookEvent.setProvider(PROVIDER_STRIPE);
        webhookEvent.setProviderEventId(event.getId());
        webhookEvent.setEventType(event.getType());
        webhookEvent.setPayloadJson(payload);
        webhookEvent.setProcessed(0);
        webhookEvent.setCreatedAt(LocalDateTime.now());

        int inserted = paymentWebhookEventMapper.insertIgnore(webhookEvent);
        if (inserted != 1) {
            // 重复事件直接幂等返回，不再推进业务状态
            return;
        }

        /**
         * 第 3 步：按事件类型推进本地状态
         */
        switch (event.getType()) {
            case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event);
            case "checkout.session.expired" -> handleCheckoutSessionExpired(event);
            default -> {
                // 当前阶段只处理三类核心事件；其它事件先记录不报错
            }
        }

        /**
         * 第 4 步：业务处理完成后，标记事件 processed
         */
        paymentWebhookEventMapper.markProcessed(webhookEvent.getId(), LocalDateTime.now());
    }

    private void handlePaymentIntentSucceeded(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new RuntimeException("payment_intent.succeeded 事件数据解析失败"));

        PaymentOrder paymentOrder = paymentOrderMapper.findByStripePaymentIntentId(paymentIntent.getId());
        if (paymentOrder == null) {
            throw new RuntimeException("未找到 payment_intent 对应的本地支付单，paymentIntentId=" + paymentIntent.getId());
        }

        // 规则 1：支付成功 -> payment_order: SUCCEEDED
        paymentOrderMapper.markPaid(paymentOrder.getId());

        // 规则 1：支付成功 -> orders: CREATED -> PAID（只允许 webhook 推进）
        orderMapper.markPaid(paymentOrder.getOrderId());
    }

    private void handlePaymentIntentFailed(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new RuntimeException("payment_intent.payment_failed 事件数据解析失败"));

        PaymentOrder paymentOrder = paymentOrderMapper.findByStripePaymentIntentId(paymentIntent.getId());
        if (paymentOrder == null) {
            throw new RuntimeException("未找到 payment_intent 对应的本地支付单，paymentIntentId=" + paymentIntent.getId());
        }

        // 规则 2：仅更新 payment_order=FAILED；orders 保持 CREATED
        paymentOrderMapper.markFailed(paymentOrder.getId());
    }

    private void handleCheckoutSessionExpired(Event event) {
        Session session = (Session) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new RuntimeException("checkout.session.expired 事件数据解析失败"));

        PaymentOrder paymentOrder = paymentOrderMapper.findByStripeCheckoutSessionId(session.getId());
        if (paymentOrder == null) {
            throw new RuntimeException("未找到 checkout_session 对应的本地支付单，sessionId=" + session.getId());
        }

        // 规则 3：仅更新 payment_order=EXPIRED；orders 暂不取消（第三阶段再做）
        paymentOrderMapper.markExpired(paymentOrder.getId());
    }
}
