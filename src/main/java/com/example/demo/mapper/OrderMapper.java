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

    long countAllOrders();

    long countDistinctUsersByProduct(@Param("productId") Long productId);

    LocalDateTime latestOrderCreatedAt();
}
