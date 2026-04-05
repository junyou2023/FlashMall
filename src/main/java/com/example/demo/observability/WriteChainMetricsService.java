package com.example.demo.observability;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 写链路最小指标聚合服务（内存版）
 *
 * 【本轮改动】
 * 为了让“准生产高并发模拟”形成闭环，这里补一组轻量计数器：
 * - 下单入口受理/失败
 * - Redis 预扣成功/失败
 * - MQ 发送成功/失败
 * - MQ 消费成功 / NACK 重入队 / REJECT 丢弃
 *
 * 【注意】
 * 1) 这是单实例内存计数器，适合当前阶段压测观察，不等同于生产级全局监控。
 * 2) 当前目标是“最小可观测性闭环”，不是重型指标平台。
 */
@Service
public class WriteChainMetricsService {

    private final AtomicLong orderGatewayAccepted = new AtomicLong(0);
    private final AtomicLong orderGatewayRejected = new AtomicLong(0);

    private final AtomicLong redisPreDeductSuccess = new AtomicLong(0);
    private final AtomicLong redisPreDeductInsufficient = new AtomicLong(0);
    private final AtomicLong redisPreDeductNotPreheated = new AtomicLong(0);

    private final AtomicLong mqSendSuccess = new AtomicLong(0);
    private final AtomicLong mqSendFail = new AtomicLong(0);
    private final AtomicLong mqConfirmFail = new AtomicLong(0);
    private final AtomicLong mqReturnFail = new AtomicLong(0);

    private final AtomicLong consumeSuccess = new AtomicLong(0);
    private final AtomicLong consumeNackRequeue = new AtomicLong(0);
    private final AtomicLong consumeRejectDiscard = new AtomicLong(0);

    public void recordOrderGatewayAccepted() {
        orderGatewayAccepted.incrementAndGet();
    }

    public void recordOrderGatewayRejected() {
        orderGatewayRejected.incrementAndGet();
    }

    public void recordRedisPreDeductSuccess() {
        redisPreDeductSuccess.incrementAndGet();
    }

    public void recordRedisPreDeductInsufficient() {
        redisPreDeductInsufficient.incrementAndGet();
    }

    public void recordRedisPreDeductNotPreheated() {
        redisPreDeductNotPreheated.incrementAndGet();
    }

    public void recordMqSendSuccess() {
        mqSendSuccess.incrementAndGet();
    }

    public void recordMqSendFail() {
        mqSendFail.incrementAndGet();
    }

    public void recordMqConfirmFail() {
        mqConfirmFail.incrementAndGet();
    }

    public void recordMqReturnFail() {
        mqReturnFail.incrementAndGet();
    }

    public void recordConsumeSuccess() {
        consumeSuccess.incrementAndGet();
    }

    public void recordConsumeNackRequeue() {
        consumeNackRequeue.incrementAndGet();
    }

    public void recordConsumeRejectDiscard() {
        consumeRejectDiscard.incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderGatewayAccepted", orderGatewayAccepted.get());
        payload.put("orderGatewayRejected", orderGatewayRejected.get());

        payload.put("redisPreDeductSuccess", redisPreDeductSuccess.get());
        payload.put("redisPreDeductInsufficient", redisPreDeductInsufficient.get());
        payload.put("redisPreDeductNotPreheated", redisPreDeductNotPreheated.get());

        payload.put("mqSendSuccess", mqSendSuccess.get());
        payload.put("mqSendFail", mqSendFail.get());
        payload.put("mqConfirmFail", mqConfirmFail.get());
        payload.put("mqReturnFail", mqReturnFail.get());

        payload.put("consumeSuccess", consumeSuccess.get());
        payload.put("consumeNackRequeue", consumeNackRequeue.get());
        payload.put("consumeRejectDiscard", consumeRejectDiscard.get());
        return payload;
    }
}
