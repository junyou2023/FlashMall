package com.example.demo.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 基础配置
 *
 * 当前阶段我们只做最小可用的一条订单创建链路：
 * - 一个交换机
 * - 一个队列
 * - 一条 routing key
 *
 * 目标：
 * 让“订单创建消息”有地方可发、有地方可收。
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 交换机名称
     *
     * 生产者会把消息发到这个交换机。
     */
    public static final String ORDER_EXCHANGE = "order.exchange";

    /**
     * 订单创建队列
     *
     * 消费者会监听这个队列。
     */
    public static final String ORDER_QUEUE = "order.create.queue";

    /**
     * 路由键
     *
     * 用于把消息从 exchange 路由到指定队列。
     */
    public static final String ORDER_ROUTING_KEY = "order.create";

    /**
     * 声明一个 TopicExchange
     *
     * durable = true
     * 表示 RabbitMQ 重启后，这个交换机仍然存在。
     */
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE, true, false);
    }

    /**
     * 声明订单创建队列
     *
     * durable = true
     * 表示 RabbitMQ 重启后，这个队列仍然存在。
     */
    @Bean
    public Queue orderCreateQueue() {
        return QueueBuilder.durable(ORDER_QUEUE).build();
    }

    /**
     * 把队列绑定到交换机上
     *
     * 以后生产者只需要往 ORDER_EXCHANGE 发消息，
     * 并带上 ORDER_ROUTING_KEY，
     * RabbitMQ 就会把消息路由到 ORDER_QUEUE。
     */
    @Bean
    public Binding orderCreateBinding() {
        return BindingBuilder
                .bind(orderCreateQueue())
                .to(orderExchange())
                .with(ORDER_ROUTING_KEY);
    }

    /**
     * 消息序列化方式：用 JSON
     *
     * 默认是 Java 二进制序列化，不可读且易报错。
     * 配置成 JSON 后：
     * - 生产者自动把 OrderCreateMessage 转成 JSON 字符串发送
     * - 消费者自动把 JSON 字符串反序列化回 OrderCreateMessage
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
