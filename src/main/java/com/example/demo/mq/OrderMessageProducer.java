package com.example.demo.mq;

import com.example.demo.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单消息生产者
 *
 * 当前阶段它只做一件事：
 * 把“创建订单消息”发送到 RabbitMQ。
 *
 * 它不负责校验库存，不负责写数据库。
 * 它的职责只有：发消息。
 */
@Component
public class OrderMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    public OrderMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发送“创建订单”消息
     *
     * 发送到：
     * - exchange: order.exchange
     * - routingKey: order.create
     */
    public void sendCreateOrderMessage(OrderCreateMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_ROUTING_KEY,
                message
        );

        System.out.println("【MQ发送】订单创建消息已发送，requestId = " + message.getRequestId());
    }
}