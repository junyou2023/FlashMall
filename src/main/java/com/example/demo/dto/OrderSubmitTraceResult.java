package com.example.demo.dto;

/**
 * 下单入口最小观测结果
 *
 * 【本轮改动】
 * 这个对象只服务“最小可观测性闭环”，
 * 不改变原有主链路职责边界。
 *
 * 这里记录的标签用于回答：
 * - Redis 预扣是否成功
 * - MQ 是否发送成功
 * - 请求线程是否进入“异步已受理”状态
 */
public class OrderSubmitTraceResult {

    private final String message;
    private final String preDeductStatus;
    private final String mqSendStatus;
    private final boolean asyncAccepted;

    public OrderSubmitTraceResult(String message, String preDeductStatus, String mqSendStatus, boolean asyncAccepted) {
        this.message = message;
        this.preDeductStatus = preDeductStatus;
        this.mqSendStatus = mqSendStatus;
        this.asyncAccepted = asyncAccepted;
    }

    public String getMessage() {
        return message;
    }

    public String getPreDeductStatus() {
        return preDeductStatus;
    }

    public String getMqSendStatus() {
        return mqSendStatus;
    }

    public boolean isAsyncAccepted() {
        return asyncAccepted;
    }
}
