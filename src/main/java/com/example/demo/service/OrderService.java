package com.example.demo.service;

import com.example.demo.mapper.OrderItemMapper;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final ProductCacheService productCacheService;
    private final AsyncDeleteService asyncDeleteService;

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        ProductMapper productMapper,
                        ProductCacheService productCacheService,
                        AsyncDeleteService asyncDeleteService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.productCacheService = productCacheService;
        this.asyncDeleteService = asyncDeleteService;
    }

    @Transactional
    public Long placeOrder(Long userId, Long productId, int quantity) {

        // 1. 查询商品
        Product product = productMapper.findById(productId);
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }

        // 2. 原子扣减库存
        int affected = productMapper.deductStock(productId, quantity);
        if (affected == 0) {
            throw new RuntimeException("库存不足");
        }

        // 3. 计算总价
        double totalPrice = product.getPrice() * quantity;

        // 4. 写订单主表
        Order order = new Order();
        order.setUserId(userId);
        order.setTotalPrice(totalPrice);
        order.setStatus("CREATED");
        order.setCreatedAt(LocalDateTime.now());

        int inserted = orderMapper.insert(order);
        if (inserted != 1) {
            throw new RuntimeException("创建订单失败");
        }

        // 5. 写订单明细
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
         * 6. 这一层的关键升级：事务提交后删缓存
         *
         * 为什么不是这里直接删？
         * 因为当前方法还处于事务内部。
         * 更严谨的做法是：等事务真正提交成功后，再删缓存。
         *
         * 这样：
         * - MySQL 真相已经落地成功
         * - 再去删 Redis 副本
         * 逻辑更稳。
         */
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 6.1 提交后立即删一次
                productCacheService.evictAfterProductChanged(productId);

                // 6.2 再延迟删一次（轻量版延迟双删）
                asyncDeleteService.delayedDeleteProductCache(productId, 200);
            }
        });

        // 7. 返回订单 id
        return order.getId();
    }
}