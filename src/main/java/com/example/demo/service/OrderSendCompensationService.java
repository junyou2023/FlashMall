package com.example.demo.service;

import com.example.demo.mq.OrderCreateMessage;
import org.springframework.stereotype.Service;

/**
 * MQ 发送失败补偿服务
 *
 * 当订单消息没有真正送到 Broker / Queue 时，
 * 把入口线程已经改过的 Redis 状态补回去。
 */
@Service
public class OrderSendCompensationService {

    private final RedisStockService redisStockService;
    private final RedisOrderGuardService redisOrderGuardService;

    public OrderSendCompensationService(RedisStockService redisStockService,
                                        RedisOrderGuardService redisOrderGuardService) {
        this.redisStockService = redisStockService;
        this.redisOrderGuardService = redisOrderGuardService;
    }

    public void compensateOnSendFail(OrderCreateMessage message, String reason) {
        System.err.println("【MQ发送失败补偿开始】requestId = "
                + message.getRequestId() + "，原因 = " + reason);

        // 1. 回补 Redis 预扣库存
        redisStockService.rollbackStock(message.getProductId(), message.getQuantity());

        // 2. 清理 requestId 幂等标记
        redisOrderGuardService.clearIdempotent(message.getRequestId());

        // 3. 清理订单处理中状态
        redisOrderGuardService.clearOrderStatus(message.getUserId(), message.getProductId());

        System.err.println("【MQ发送失败补偿完成】requestId = "
                + message.getRequestId());
    }
}