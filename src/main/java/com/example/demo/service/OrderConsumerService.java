package com.example.demo.service;

import com.example.demo.mapper.OrderItemMapper;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.Product;
import com.example.demo.mq.OrderMessageProducer;
import com.example.demo.mq.OrderTimeoutCancelMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * 订单消费者落库服务
 *
 * 这里才是真正的“创建订单”逻辑。
 * 它运行在 MQ 消费线程里，而不是用户请求线程里。
 */
@Service
public class OrderConsumerService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final RedisStockService redisStockService;
    private final RedisOrderGuardService redisOrderGuardService;
    private final ProductCacheService productCacheService;
    private final AsyncDeleteService asyncDeleteService;
    private final OrderMessageProducer orderMessageProducer;

    public OrderConsumerService(OrderMapper orderMapper,
                                OrderItemMapper orderItemMapper,
                                ProductMapper productMapper,
                                RedisStockService redisStockService,
                                RedisOrderGuardService redisOrderGuardService,
                                ProductCacheService productCacheService,
                                AsyncDeleteService asyncDeleteService,
                                OrderMessageProducer orderMessageProducer) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.redisStockService = redisStockService;
        this.redisOrderGuardService = redisOrderGuardService;
        this.productCacheService = productCacheService;
        this.asyncDeleteService = asyncDeleteService;
        this.orderMessageProducer = orderMessageProducer;
    }

    /**
     * 真正异步创建订单
     *
     * 这是一个 MySQL 事务。
     */
    @Transactional
    public void createOrder(Long userId, Long productId, int quantity, String requestId) {

        try {
            // ===== 手动 ACK 本轮新增开始 =====

            /**
             * 第 0 步：消费幂等短路
             *
             * 为什么这一轮要补这一步？
             *
             * 因为手动 ACK 只保证“至少一次投递”，
             * 不保证“只消费一次”。
             *
             * 典型风险场景：
             * - MySQL 其实已经提交成功
             * - 但消费者在 ACK 之前宕机
             * - RabbitMQ 认为你没确认，于是再次投递
             *
             * 如果没有这一步，消费者可能重复创建订单。
             *
             * 所以现在先查 requestId 的幂等状态：
             * - 如果已经是 DONE，直接短路返回
             * - 等外层消费者去 ACK
             */
            String idempotentStatus = redisOrderGuardService.getIdempotentStatus(requestId);
            if ("DONE".equals(idempotentStatus)) {
                System.out.println("【消费幂等短路】requestId 已处理完成，直接返回，requestId = " + requestId);
                return;
            }

            // ===== 手动 ACK 本轮新增结束 =====

            /**
             * 第 1 步：查商品（拿价格、校验存在）
             */
            Product product = productMapper.findById(productId);
            if (product == null) {
                throw new RuntimeException("商品不存在");
            }

            /**
             * 第 2 步：MySQL 最终真相裁决
             *
             * 即使 Redis 预扣成功，这里仍然要让 MySQL 做最终扣减。
             * 因为 MySQL 仍然是库存真相源。
             */
            int affected = productMapper.deductStock(productId, quantity);
            if (affected == 0) {
                throw new RuntimeException("库存不足（MySQL 最终裁决失败）");
            }

            /**
             * 第 3 步：写订单主表
             */
            double totalPrice = product.getPrice() * quantity;

            Order order = new Order();
            order.setUserId(userId);
            order.setTotalPrice(totalPrice);
            order.setStatus("CREATED");
            order.setCreatedAt(LocalDateTime.now());

            int inserted = orderMapper.insert(order);
            if (inserted != 1) {
                throw new RuntimeException("创建订单失败");
            }

            /**
             * 第 4 步：写订单明细
             */
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(productId);
            item.setQuantity(quantity);
            item.setPrice(product.getPrice());

            int insertedItem = orderItemMapper.insert(item);
            if (insertedItem != 1) {
                throw new RuntimeException("创建订单明细失败");
            }

            /**
             * 第 5 步：事务提交后做善后动作
             *
             * 为什么放 afterCommit？
             * 因为只有事务真正提交成功，
             * 这笔订单才算真正“成立”。
             */
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 5.1 一人一单状态改成 SUCCESS
                    redisOrderGuardService.markSuccess(userId, productId);

                    // 5.2 幂等状态改成 DONE
                    // 【这一步在手动 ACK 之后变得更关键】
                    // 因为后续如果消息再次投递，消费者会先读取这个 DONE 状态来短路
                    redisOrderGuardService.markIdempotentDone(requestId);

                    // 5.3 删除商品缓存（库存已经变化）
                    productCacheService.evictAfterProductChanged(productId);

                    // 5.4 延迟双删补刀
                    asyncDeleteService.delayedDeleteProductCache(productId, 200);

                    /**
                     * 5.5 发送“未来触发取消”的延迟消息
                     *
                     * 为什么放在 afterCommit？
                     * 因为只有订单真正落库成功，才有资格进入“超时取消”链路。
                     */
                    orderMessageProducer.sendOrderTimeoutCancelDelayMessage(
                            new OrderTimeoutCancelMessage(userId, productId, quantity, requestId)
                    );
                }
            });

        } catch (RuntimeException e) {
            /**
             * 第 6 步：失败补偿
             *
             * 这里非常关键。
             *
             * 因为入口线程已经做过：
             * - Redis 预扣库存
             * - 写 PENDING
             * - 写幂等 PROCESSING
             *
             * 如果消费者最终失败，不补偿就会出脏状态。
             */
            redisStockService.rollbackStock(productId, quantity);
            redisOrderGuardService.clearIdempotent(requestId);
            redisOrderGuardService.clearOrderStatus(userId, productId);

            throw e;
        }
    }
}
