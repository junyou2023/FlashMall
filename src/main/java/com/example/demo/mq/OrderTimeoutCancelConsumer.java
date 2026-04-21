package com.example.demo.mq;

import com.example.demo.service.OrderTimeoutCancelService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单超时取消消费者
 *
 * 它是 TTL + DLQ 链路的最终执行点：
 * 消息在延迟队列过期后会流到这里，
 * 这里再做“状态校验 + 取消 + 库存回补”。
 */
@Component
public class OrderTimeoutCancelConsumer {

    private final OrderTimeoutCancelService orderTimeoutCancelService;

    public OrderTimeoutCancelConsumer(OrderTimeoutCancelService orderTimeoutCancelService) {
        this.orderTimeoutCancelService = orderTimeoutCancelService;
    }

    /**
     * 为什么继续沿用手动 ACK？
     * 因为取消链路同样涉及 MySQL + Redis 的多步更新，
     * 先处理成功再 ACK，才能跟主链路保持同一套确认语义。
     */
    @RabbitListener(
            queues = "${app.mq.order.cancel.execute-queue:order.cancel.execute.queue}",
            containerFactory = "manualAckRabbitListenerContainerFactory"
    )
    public void consumeTimeoutCancel(OrderTimeoutCancelMessage message, Message amqpMessage, Channel channel) throws IOException {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();

        try {
            orderTimeoutCancelService.cancelIfTimeout(message);
            channel.basicAck(deliveryTag, false);
            System.out.println("【MQ ACK】订单超时取消消息已确认，requestId = " + message.getRequestId());
        } catch (Exception e) {
            /**
             * 当前阶段不做复杂重试编排，先走最小可控策略：
             * 首次失败重回队列一次；重复失败则拒绝，避免无限打爆队列。
             */
            boolean redelivered = Boolean.TRUE.equals(amqpMessage.getMessageProperties().getRedelivered());
            if (!redelivered) {
                channel.basicNack(deliveryTag, false, true);
                System.err.println("【MQ NACK】订单超时取消首次失败，重新入队，requestId = "
                        + message.getRequestId() + "，error = " + e.getMessage());
            } else {
                channel.basicReject(deliveryTag, false);
                System.err.println("【MQ REJECT】订单超时取消重复失败后丢弃，requestId = "
                        + message.getRequestId() + "，error = " + e.getMessage());
            }
        }
    }
}
