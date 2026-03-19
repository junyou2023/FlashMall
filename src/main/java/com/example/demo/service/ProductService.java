// src/main/java/com/example/demo/service/ProductService.java
package com.example.demo.service;

import com.example.demo.mapper.ProductMapper;
import com.example.demo.model.Product;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductMapper productMapper;

    // 构造函数注入 mapper
    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /**
     * 【扩展】缓存链路第 4 环：声明“我要缓存商品列表”
     *
     * 运行逻辑：
     * 1) 第一次调用：
     *    - Spring Cache 发现 key 不存在
     *    - 执行方法体 → 查 MySQL
     *    - 把返回值写入 Redis
     *
     * 2) 第二次调用：
     *    - Spring Cache 命中 key
     *    - 直接从 Redis 返回
     *    - 方法体不会再执行（不会打 DB）
     */
    @Cacheable(cacheNames = "product:list")
    public List<Product> getAllProducts() {
        return productMapper.findAll();
    }

    /**
     * 【扩展】缓存链路第 4 环：声明“我要缓存单个商品详情”
     *
     * key = "#id" 表示：
     * - 缓存键由参数 id 决定
     * - 不同 id 会产生不同缓存
     */
    @Cacheable(cacheNames = "product:detail", key = "#id")
    public Product getProductById(Long id) {
        System.out.println("【真正执行方法体】getProductById 被调用，准备访问 DB, id = " + id);
        return productMapper.findById(id);
    }

    /**
     * 只负责“清理某个商品相关的缓存”，用于模拟“商品被更新后，缓存失效”。
     *
     * @CacheEvict：
     *   - cacheNames = "product:detail"    表示清商品详情缓存
     *   - key = "#id"                      表示只清这个 id 对应的 key："product:detail::1"
     *
     * allEntries = true 通常用来清整个 cacheNames 下的所有 key，
     * 这里我们只演示单个 id，就先不用。
     */
    @CacheEvict(cacheNames = "product:detail", key = "#id")
    public void evictProductCache(Long id) {
        System.out.println("【清缓存】evictProductCache 被调用，准备删除缓存，id = " + id);
        // 这里可以什么都不做，只靠 @CacheEvict 注解完成删除操作
    }

}
