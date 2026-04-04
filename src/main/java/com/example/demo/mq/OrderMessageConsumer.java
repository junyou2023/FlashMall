package com.example.demo.mq;

import com.example.demo.service.OrderConsumerService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单消息消费者
 *
 * 它负责监听 RabbitMQ 队列。
 * 一旦收到“创建订单消息”，就交给 OrderConsumerService 去真正落库。
 *
 * 你可以把它理解成：
 * - MQ 层的入口
 * - 真正业务处理的触发器
 */
@Component
public class OrderMessageConsumer {

    private final OrderConsumerService orderConsumerService;

    public OrderMessageConsumer(OrderConsumerService orderConsumerService) {
        this.orderConsumerService = orderConsumerService;
    }

    /**
     * 监听订单创建队列
     *
     * RabbitMQ 一旦有消息进来，就会调用这个方法。
     */
    // ===== 手动 ACK 本轮新增开始 =====
    /**
     * 和之前最大的区别：
     *
     * 1. 指定使用 manualAckRabbitListenerContainerFactory
     *    这样这个监听方法就进入“手动 ACK 模式”
     *
     * 【本轮改动】
     * 队列名改成从配置读取（app.mq.order.queue-name）。
     * 这样 dev / perf 可以监听不同队列，实现第二层资源隔离。
     *
     * 2. 方法参数里多拿两个东西：
     *    - Message amqpMessage：拿 deliveryTag、redelivered 等元信息
     *    - Channel channel：手动 basicAck / basicNack / basicReject
     *
     * 这就是“消费者真正拿回确认权”。
     */
    @RabbitListener(
            queues = "${app.mq.order.queue-name:order.create.queue}",
            containerFactory = "manualAckRabbitListenerContainerFactory"
    )
    public void consumeCreateOrder(OrderCreateMessage message, Message amqpMessage, Channel channel) throws IOException {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();

        try {
            System.out.println("【MQ消费】收到订单创建消息，requestId = " + message.getRequestId());

            orderConsumerService.createOrder(
                    message.getUserId(),
                    message.getProductId(),
                    message.getQuantity(),
                    message.getRequestId()
            );

            /**
             * 只有真正执行成功，才 ACK
             *
             * 这和自动 ACK 最大的区别就在这里：
             * 现在不是“消息一到就算成功”，
             * 而是“业务跑完了才算成功”。
             */
            channel.basicAck(deliveryTag, false);
            System.out.println("【MQ ACK】订单消息已确认，requestId = " + message.getRequestId());

        } catch (Exception e) {
            boolean redelivered = Boolean.TRUE.equals(amqpMessage.getMessageProperties().getRedelivered());

            System.out.println("【MQ异常】消费失败，requestId = " + message.getRequestId()
                    + "，error = " + e.getMessage());

            /**
             * 这里先做一个“最小可学习版”策略：
             *
             * 第一次失败：
             * - basicNack(requeue = true)
             * - 让消息先回队列，再试一次
             *
             * 如果已经是重投后的消息还失败：
             * - basicReject(requeue = false)
             * - 当前先直接丢弃
             *
             * 为什么先这样做？
             * 因为这一层我们的目标只是先把“手动 ACK 的控制权”学明白。
             *
             * 真正更完整的版本，下一层应该继续接：
             * - DLQ（死信队列）
             * - 延迟重试
             * - 失败告警
             */
            if (!redelivered) {
                channel.basicNack(deliveryTag, false, true);
                System.out.println("【MQ NACK】首次失败，消息重新入队，requestId = " + message.getRequestId());
            } else {
                channel.basicReject(deliveryTag, false);
                System.out.println("【MQ REJECT】重复投递后仍失败，消息丢弃，requestId = " + message.getRequestId());
            }
        }
    }
    // ===== 手动 ACK 本轮新增结束 =====
}
