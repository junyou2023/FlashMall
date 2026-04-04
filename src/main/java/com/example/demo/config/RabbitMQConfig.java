package com.example.demo.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
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
     * 【本轮改动】默认 MQ 名称常量
     *
     * 为什么保留默认值？
     * - 保证你本地旧环境不改配置也能跑起来
     * - 同时允许 dev/perf/prod 通过 yml 注入不同命名，实现“第二层资源隔离”
     */
    public static final String DEFAULT_ORDER_EXCHANGE = "order.exchange";
    public static final String DEFAULT_ORDER_QUEUE = "order.create.queue";
    public static final String DEFAULT_ORDER_ROUTING_KEY = "order.create";

    @Value("${app.mq.order.exchange:" + DEFAULT_ORDER_EXCHANGE + "}")
    private String orderExchange;

    @Value("${app.mq.order.queue:" + DEFAULT_ORDER_QUEUE + "}")
    private String orderQueue;

    @Value("${app.mq.order.routing-key:" + DEFAULT_ORDER_ROUTING_KEY + "}")
    private String orderRoutingKey;

    /**
     * 交换机名称
     *
     * 生产者会把消息发到这个交换机。
     */
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(orderExchange, true, false);
    }

    /**
     * 订单创建队列
     *
     * 消费者会监听这个队列。
     */
    @Bean
    public Queue orderCreateQueue() {
        return QueueBuilder.durable(orderQueue).build();
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
                .with(orderRoutingKey);
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

    // ===== 手动 ACK 本轮新增开始 =====

    /**
     * 手动 ACK 监听容器工厂
     *
     * 为什么这一轮要新增它？
     *
     * 因为默认情况下，@RabbitListener 往往是自动 ACK。
     * 自动 ACK 的问题是：
     * 只要消息一投递到消费者，RabbitMQ 就可能认为“这条消息已经处理完了”。
     *
     * 但真实业务里：
     * - 订单还没真正落库
     * - MySQL 事务可能还没提交
     * - Redis 状态可能还没补偿完成
     *
     * 所以这一轮我们把 ACK 时机改成：
     * “业务真的成功后，再由代码显式 ack”
     */
    @Bean
    public SimpleRabbitListenerContainerFactory manualAckRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);

        /**
         * 继续沿用 JSON 消息转换器
         * 这样消费者方法里仍然可以直接拿到 OrderCreateMessage
         */
        factory.setMessageConverter(messageConverter);

        /**
         * 改成手动 ACK
         *
         * 这就是这一轮的核心配置。
         * 后面由消费者代码自己决定：
         * - 什么时候 basicAck
         * - 什么时候 basicNack
         * - 什么时候 basicReject
         */
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);

        /**
         * prefetch = 1
         *
         * 当前阶段先故意保守一点：
         * 一次先让一个消费者拿 1 条未确认消息。
         *
         * 好处：
         * - 便于你观察“收到消息 -> 落库 -> ACK”的完整链路
         * - 避免一下子压很多未确认消息，调试更清晰
         *
         * 后续如果进入“并发消费调优”阶段，再放大。
         */
        factory.setPrefetchCount(1);

        /**
         * 这里先不依赖 Spring 帮你自动重回队列，
         * 因为这一轮我们要把“成功 / 失败 / 重试”时机
         * 显式写在消费者代码里，方便你真正理解 ACK 的控制权。
         */
        factory.setDefaultRequeueRejected(false);

        return factory;
    }

    // ===== 手动 ACK 本轮新增结束 =====
}
