package com.example.demo.mq;

import com.example.demo.service.OrderSendCompensationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 订单消息生产者
 *
 * 这一轮升级后，它不再只是“把消息发出去”。
 * 它还要负责：
 * 1. 显式设置消息持久化
 * 2. Confirm：确认消息是否到达 Broker
 * 3. Return：确认消息是否成功路由到 Queue
 * 4. 失败时执行补偿
 */
@Component
public class OrderMessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final OrderSendCompensationService compensationService;

    /**
     * 【本轮改动】从配置读取 exchange / routingKey
     *
     * 【为什么改】
     * 配合 dev/perf 不同命名空间，让生产者天然发送到各自环境的 MQ 资源。
     */
    private final String orderExchangeName;
    private final String orderRoutingKey;

    /**
     * 待确认消息表
     *
     * key = requestId
     * value = 待确认消息状态
     *
     * 当前阶段先用内存做最小闭环。
     * 真正生产环境下一层会升级成本地消息表 / Outbox。
     */
    private final Map<String, PendingMessageRecord> pendingMessageMap = new ConcurrentHashMap<>();

    /**
     * 用一个很轻量的延迟清理线程，给 ReturnCallback 留窗口。
     *
     * 为什么需要它？
     * 因为：
     * - Confirm 关注 Producer -> Broker
     * - Return 关注 Exchange -> Queue
     *
     * 在消息路由失败时，Confirm 可能 ack=true，
     * 但 Return 仍会告诉你“没有队列接住”。
     *
     * 所以 ack=true 时不能立刻把 pending 删除，
     * 要稍微等一下 Return 回调有没有来。
     */
    private final ScheduledExecutorService cleanupScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r);
                t.setName("mq-confirm-cleaner");
                t.setDaemon(true);
                return t;
            });

    public OrderMessageProducer(RabbitTemplate rabbitTemplate,
                                OrderSendCompensationService compensationService,
                                @Value("${app.mq.order.exchange-name:order.exchange}") String orderExchangeName,
                                @Value("${app.mq.order.routing-key:order.create}") String orderRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.compensationService = compensationService;
        this.orderExchangeName = orderExchangeName;
        this.orderRoutingKey = orderRoutingKey;
    }

    @PostConstruct
    public void init() {
        // 再显式设一遍 mandatory，和 yml 对齐，避免遗漏
        rabbitTemplate.setMandatory(true);

        /**
         * ConfirmCallback：
         * 只回答一个问题：
         * “消息有没有到 Broker？”
         */
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData == null) {
                return;
            }

            String requestId = correlationData.getId();
            PendingMessageRecord record = pendingMessageMap.get(requestId);
            if (record == null) {
                return;
            }

            if (!ack) {
                // Broker 没确认收到
                PendingMessageRecord removed = pendingMessageMap.remove(requestId);
                if (removed != null && removed.tryMarkCompensated()) {
                    System.err.println("【MQ Confirm失败】Broker 未确认收到消息，requestId = "
                            + requestId + "，cause = " + cause);

                    compensationService.compensateOnSendFail(
                            removed.getMessage(),
                            "Broker 未确认收到消息，cause = " + cause
                    );
                }
                return;
            }

            // ack = true：消息到了 Broker，但不代表一定到 Queue
            record.markBrokerConfirmed();

            // 给 ReturnCallback 留一个小窗口
            cleanupScheduler.schedule(() -> {
                PendingMessageRecord current = pendingMessageMap.remove(requestId);
                if (current != null && !current.isReturned()) {
                    System.out.println("【MQ Confirm成功】Broker 已确认收到消息，requestId = " + requestId);
                }
            }, 1000, TimeUnit.MILLISECONDS);
        });

        /**
         * ReturnsCallback：
         * 只回答一个问题：
         * “消息到了 Exchange，但有没有被 Queue 接住？”
         */
        rabbitTemplate.setReturnsCallback(returned -> {
            String requestId = returned.getMessage().getMessageProperties().getMessageId();
            if (requestId == null) {
                System.err.println("【MQ Return失败】messageId 为空，无法精确补偿");
                return;
            }

            PendingMessageRecord removed = pendingMessageMap.remove(requestId);
            if (removed != null) {
                removed.markReturned();

                if (removed.tryMarkCompensated()) {
                    System.err.println("【MQ Return触发】消息未成功路由到队列，requestId = "
                            + requestId
                            + "，replyCode = " + returned.getReplyCode()
                            + "，replyText = " + returned.getReplyText()
                            + "，exchange = " + returned.getExchange()
                            + "，routingKey = " + returned.getRoutingKey());

                    compensationService.compensateOnSendFail(
                            removed.getMessage(),
                            "消息到达 Exchange 但未路由到 Queue，replyText = " + returned.getReplyText()
                    );
                }
            }
        });
    }

    /**
     * 发送订单创建消息
     */
    public void sendCreateOrderMessage(OrderCreateMessage message) {
        String requestId = message.getRequestId();

        pendingMessageMap.put(requestId, new PendingMessageRecord(message));

        CorrelationData correlationData = new CorrelationData(requestId);

        try {
            rabbitTemplate.convertAndSend(
                    orderExchangeName,
                    orderRoutingKey,
                    message,
                    msg -> {
                        // 给 Return 回调一个可追踪的 messageId
                        msg.getMessageProperties().setMessageId(requestId);

                        // 显式设置消息持久化
                        msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);

                        return msg;
                    },
                    correlationData
            );

            System.out.println("【MQ发送】订单创建消息已发送，requestId = " + requestId);

        } catch (Exception e) {
            PendingMessageRecord removed = pendingMessageMap.remove(requestId);
            if (removed != null && removed.tryMarkCompensated()) {
                compensationService.compensateOnSendFail(
                        removed.getMessage(),
                        "发送阶段直接异常：" + e.getMessage()
                );
            }

            throw new RuntimeException("发送订单消息失败", e);
        }
    }

    @PreDestroy
    public void destroy() {
        cleanupScheduler.shutdown();
    }

    /**
     * 待确认消息状态对象
     */
    private static class PendingMessageRecord {
        private final OrderCreateMessage message;
        private volatile boolean brokerConfirmed;
        private volatile boolean returned;
        private final AtomicBoolean compensated = new AtomicBoolean(false);

        public PendingMessageRecord(OrderCreateMessage message) {
            this.message = message;
        }

        public OrderCreateMessage getMessage() {
            return message;
        }

        public void markBrokerConfirmed() {
            this.brokerConfirmed = true;
        }

        public void markReturned() {
            this.returned = true;
        }

        public boolean isReturned() {
            return returned;
        }

        public boolean tryMarkCompensated() {
            return compensated.compareAndSet(false, true);
        }
    }
}
