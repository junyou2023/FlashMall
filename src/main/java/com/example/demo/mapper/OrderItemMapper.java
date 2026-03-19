// src/main/java/com/example/demo/mapper/OrderItemMapper.java
package com.example.demo.mapper;

import com.example.demo.model.OrderItem;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrderItemMapper {

    int insert(OrderItem item);

    List<OrderItem> findByOrderId(Long orderId);
}
