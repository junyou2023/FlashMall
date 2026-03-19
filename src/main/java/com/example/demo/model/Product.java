// src/main/java/com/example/demo/model/Product.java
package com.example.demo.model;

public class Product {

    private Long id;        // 商品ID
    private String name;    // 商品名称
    private double price;   // 价格
    private int stock;      // 库存数量

    public Product() {
    }

    public Product(Long id, String name, double price, int stock) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }
}
