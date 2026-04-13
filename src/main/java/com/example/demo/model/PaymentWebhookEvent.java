package com.example.demo.model;

import java.time.LocalDateTime;

/**
 * 支付 webhook 事件落地记录
 *
 * 为什么要有这张表：
 * 1. Stripe webhook 是“至少一次投递”，天然可能重复推送
 * 2. 我们需要一个本地去重锚点，确保同一个 provider_event_id 只推进一次业务状态
 * 3. 后续排障时，也能回看原始 payload
 */
public class PaymentWebhookEvent {

    private Long id;
    private String provider;
    private String providerEventId;
    private String eventType;
    private String payloadJson;
    private Integer processed;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public void setProviderEventId(String providerEventId) {
        this.providerEventId = providerEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Integer getProcessed() {
        return processed;
    }

    public void setProcessed(Integer processed) {
        this.processed = processed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
