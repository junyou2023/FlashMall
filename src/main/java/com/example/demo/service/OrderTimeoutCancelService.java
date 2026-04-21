package com.example.demo.service;

import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.model.Order;
import com.example.demo.mq.OrderTimeoutCancelMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单超时取消服务
 *
 * 这个服务专门负责 TTL 到期后的“最终裁决”：
 * 只有订单还停留在 CREATED，才允许改为 CANCELED 并回补库存。
 */
@Service
public class OrderTimeoutCancelService {

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final RedisStockService redisStockService;
    private final RedisOrderGuardService redisOrderGuardService;
    private final ProductCacheService productCacheService;
    private final AsyncDeleteService asyncDeleteService;

    public OrderTimeoutCancelService(OrderMapper orderMapper,
                                     ProductMapper productMapper,
                                     RedisStockService redisStockService,
                                     RedisOrderGuardService redisOrderGuardService,
                                     ProductCacheService productCacheService,
                                     AsyncDeleteService asyncDeleteService) {
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.redisStockService = redisStockService;
        this.redisOrderGuardService = redisOrderGuardService;
        this.productCacheService = productCacheService;
        this.asyncDeleteService = asyncDeleteService;
    }

    /**
     * 按 requestId 做超时取消
     *
     * 这一步不是为了“把所有超时单都扫掉”，
     * 而是只处理当前这条 TTL 触发的订单，避免改造成全量任务系统。
     */
    @Transactional
    public void cancelIfTimeout(OrderTimeoutCancelMessage message) {
        String requestId = message.getRequestId();

        Order order = orderMapper.findLatestCreatedByUserAndProduct(message.getUserId(), message.getProductId());
        if (order == null) {
            System.out.println("【超时取消跳过】未找到订单，可能已失败回滚，requestId = " + requestId);
            return;
        }

        if (!"CREATED".equals(order.getStatus())) {
            System.out.println("【超时取消跳过】订单已不是 CREATED，requestId = "
                    + requestId + "，currentStatus = " + order.getStatus());
            return;
        }

        /**
         * 为什么这里用“带状态条件的更新”？
         * 因为它可以把并发竞争收敛在一条 SQL：
         * 只有当前仍是 CREATED 的订单才能被改成 CANCELED。
         */
        int updated = orderMapper.cancelOrderIfCreated(order.getId());
        if (updated == 0) {
            System.out.println("【超时取消跳过】并发下状态已变化，requestId = " + requestId);
            return;
        }

        // 订单取消成功后，回补 MySQL 与 Redis 库存，并清理入口守门状态。
        productMapper.increaseStock(message.getProductId(), message.getQuantity());
        redisStockService.rollbackStock(message.getProductId(), message.getQuantity());
        redisOrderGuardService.clearOrderStatus(message.getUserId(), message.getProductId());
        redisOrderGuardService.clearIdempotent(requestId);

        // 库存回补后清缓存，避免读到旧库存。
        productCacheService.evictAfterProductChanged(message.getProductId());
        asyncDeleteService.delayedDeleteProductCache(message.getProductId(), 200);

        System.out.println("【超时取消完成】订单已取消并回补库存，requestId = " + requestId);
    }
}
