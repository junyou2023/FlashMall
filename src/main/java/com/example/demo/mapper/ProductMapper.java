// src/main/java/com/example/demo/mapper/ProductMapper.java
package com.example.demo.mapper;

import com.example.demo.model.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {

    // 查询全部商品
    List<Product> findAll();

    // 根据 id 查询单个商品
    Product findById(@Param("id") Long id);

    /**
     * 新方法：原子扣减库存（推荐用于下单）
     *
     * 返回值：
     *  - 1：扣减成功
     *  - 0：扣减失败（库存不足，或者并发下已被别人先扣掉）
     *
     * 这个方法的核心意义：
     * 让“库存是否足够 + 扣库存”这件事，在数据库一条 SQL 中完成，
     * 而不是先在 Java 里看旧库存再做决定。
     */
    int deductStock(@Param("id") Long id,
                    @Param("quantity") int quantity);

    /**
     * 订单取消后的库存回补
     */
    int increaseStock(@Param("id") Long id,
                      @Param("quantity") int quantity);

    /**
     * 聚合查询链路：统计商品总数
     */
    long countAllProducts();

    /**
     * 聚合查询链路：统计所有商品剩余库存总和
     */
    long sumAllStock();

}
