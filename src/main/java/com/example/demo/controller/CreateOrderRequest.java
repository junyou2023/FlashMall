// src/main/java/com/example/demo/controller/CreateOrderRequest.java
package com.example.demo.controller;

import lombok.Data;

/**
 * 专门用于接收创建订单请求的 DTO (Data Transfer Object)
 *
 * Q: 为什么要单独创建一个类，而不是直接用 Map<String, Object>？
 * A: 1. 类型安全：明确知道有 productId 和 quantity 这两个字段，且类型是 Long/int。
 *    2. 代码清晰：见名知意，一看就知道这是创建订单的请求。
 *    3. 方便校验：后续可以很方便地在这个类上添加 @NotNull, @Min(1) 等校验注解。
 *    4. 易于扩展：如果以后下单需要传优惠券 ID 等新字段，直接在这里加一个 private 字段即可。
 */
@Data // Lombok 注解，自动生成 getter, setter, toString 等方法
public class CreateOrderRequest {
    private Long productId;
    private int quantity;
}
