// src/main/java/com/example/demo/mapper/OrderMapper.java
package com.example.demo.mapper;

import com.example.demo.model.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface OrderMapper {

    // 插入一条订单（使用自增主键）
    int insert(Order order);

    // 根据 ID 查询订单
    Order findById(Long id);

    // 根据 requestId 查询订单（支付阶段用于把“前端请求”映射回“异步已创建订单”）
    Order findByRequestId(@Param("requestId") String requestId);

    long countAllOrders();

    long countDistinctUsersByProduct(@Param("productId") Long productId);

    LocalDateTime latestOrderCreatedAt();

    // 只有 webhook 确认成功后，才允许把订单从 CREATED 推进为 PAID
    int markPaid(@Param("orderId") Long orderId);
}
