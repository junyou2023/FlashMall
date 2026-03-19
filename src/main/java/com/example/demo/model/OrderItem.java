// src/main/java/com/example/demo/model/OrderItem.java
package com.example.demo.model;

public class OrderItem {

    private Long id;         // 明细ID
    private Long orderId;    // 所属订单ID
    private Long productId;  // 商品ID
    private int quantity;    // 购买数量
    private double price;    // 下单时锁定的单价

    public OrderItem() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }
}
