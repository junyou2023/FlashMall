package com.example.demo.mq;

import com.example.demo.config.RabbitMQConfig;
import com.example.demo.service.OrderConsumerService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

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
    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
    public void consumeCreateOrder(OrderCreateMessage message) {
        System.out.println("【MQ消费】收到订单创建消息，requestId = " + message.getRequestId());

        orderConsumerService.createOrder(
                message.getUserId(),
                message.getProductId(),
                message.getQuantity(),
                message.getRequestId()
        );
    }
}