// src/main/java/com/example/demo/controller/ProductController.java
package com.example.demo.controller;

import com.example.demo.model.Product;
import com.example.demo.service.RedisDataTypeService;
import com.example.demo.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 【Spring Boot 入门第 2 课】Controller 层 - 负责接收 HTTP 请求
 * 
 * ============================================================================
 * 问题 1：@RestController 是什么？
 * ============================================================================
 * 这是一个组合注解，等于 @Controller + @ResponseBody
 * 
 * @Controller：标记这是一个控制器类，处理 HTTP 请求
 * @ResponseBody：方法返回值直接作为 HTTP 响应体（而不是跳转页面）
 * 
 * 例如：返回 List<Product>，Spring 自动转成 JSON 格式
 * [
 *   {"id":1, "name":"iPhone", "price":899, "stock":100},
 *   {"id":2, "name":"MacBook", "price":1299, "stock":50}
 * ]
 * 
 * ============================================================================
 * 问题 2：为什么用构造函数注入而不是@Autowired？
 * ============================================================================
 * 这是 Spring 官方推荐的方式！
 * 
 * ❌ 错误写法（字段注入）：
 *   @Autowired
 *   private ProductService productService;
 * 
 * ✅ 正确写法（构造函数注入）：
 *   private final ProductService productService;
 *   public ProductController(ProductService productService) {
 *       this.productService = productService;
 *   }
 * 
 * 好处：
 * 1. 保证依赖不能为 null（final 修饰）
 * 2. 方便单元测试（可以直接 new Controller 传入 mock 对象）
 * 3. 避免循环依赖
 * 
 * ============================================================================
 * 问题 3：@GetMapping 和@PostMapping 有什么区别？
 * ============================================================================
 * @GetMapping("/products") = @RequestMapping(method = RequestMethod.GET)
 * @PostMapping("/evict") = @RequestMapping(method = RequestMethod.POST)
 * 
 * GET：获取资源（查询），应该幂等（多次调用结果相同）
 * POST：创建/修改资源（操作），不一定幂等
 * 
 * ============================================================================
 * 问题 4：@PathVariable 和@RequestParam 有什么区别？
 * ============================================================================
 * @PathVariable：从 URL 路径中取参数
 *   例：GET /products/1 → id=1
 * 
 * @RequestParam：从查询字符串取参数
 *   例：GET /products/1?userId=5 → userId=5
 * 
 * defaultValue = "1"：如果没传参数，给默认值
 */
@RestController
public class ProductController {

    // 成员变量：持有 Service 的引用
    // 注意：用 final 修饰，保证不会为 null，且只能被赋值一次
    private final ProductService productService;
    private final RedisDataTypeService redisDataTypeService;

    /**
     * 【重要】构造函数注入
     * 
     * Spring 启动时会扫描这个类，发现它有构造函数
     * 就会自动从容器中取出 ProductService 和 RedisDataTypeService
     * 然后调用这个构造函数，完成依赖注入
     * 
     * 面试考点：
     * Q: Spring 是如何注入依赖的？
     * A: 通过反射调用构造函数
     */
    public ProductController(ProductService productService, RedisDataTypeService redisDataTypeService) {
        this.productService = productService;
        this.redisDataTypeService = redisDataTypeService;
    }

    /**
     * 【接口 1】商品列表接口
     * 
     * 请求方式：GET http://localhost:8080/products
     * 返回类型：List<Product> → Spring 自动转成 JSON 数组
     * 
     * 调用链：
     * Controller → Service → Mapper → MySQL
     * 
     * 面试考点：
     * Q: Spring 如何把 List<Product> 转成 JSON 的？
     * A: 使用 HttpMessageConverter（默认是 Jackson）
     */
    @GetMapping("/products")
    public List<Product> listProducts() {
        // 直接返回 Service 层的查询结果
        return productService.getAllProducts();
    }

    /**
     * 【接口 2】商品详情接口
     * 
     * 请求方式：GET http://localhost:8080/products/1?userId=5
     * 路径参数：id = 1
     * 查询参数：userId = 5（默认值是 1）
     * 
     * 业务逻辑：
     * 1. 先查商品（可能来自 Redis 缓存，也可能来自 MySQL）
     * 2. 记录用户浏览行为（用 Redis 的 4 种数据类型）
     * 
     * 面试考点：
     * Q: 为什么要记录浏览行为？
     * A: 这是业务需求，用于推荐系统、热门排行等
     */
    @GetMapping("/products/{id}")
    public Product getProduct(
            @PathVariable Long id,           // 从 URL 路径获取商品 ID
            @RequestParam(name = "userId", defaultValue = "1") Long userId  // 从查询字符串获取用户 ID
    ) {
        // Step 1: 查询商品信息（带缓存）
        Product product = productService.getProductById(id);

        // Step 2: 记录用户浏览行为（无论是否命中缓存都要记录）
        // 为什么？因为“用户访问了商品”是业务事实，要记录下来
        if (product != null) {
            redisDataTypeService.recordProductView(userId, product);
        }

        // Step 3: 返回商品详情（JSON 格式）
        return product;
    }

    /**
     * 【接口 3】清除商品缓存（测试用）
     * 
     * 请求方式：POST http://localhost:8080/products/1/evict
     * 作用：删除 Redis 中该商品的缓存，下次查询会重新从 DB 加载
     * 
     * 使用场景：
     * - 后台管理系统修改了商品信息
     * - 需要验证缓存是否真的生效
     * 
     * 面试考点：
     * Q: 为什么不直接更新缓存而是删除？
     * A: 更新缓存有并发覆盖问题，删除更简单可靠
     */
    @PostMapping("/{id}/evict")
    public void evictProductCache(@PathVariable Long id) {
        productService.evictProductCache(id);
    }

    /**
     * 【兼容接口】清除商品缓存（另一种路径）
     * 
     * 作用和上面一样，只是 URL 路径更符合直觉
     * 两个接口可以同时存在，Spring 会根据路径匹配
     */
    @PostMapping("/products/{id}/evict")
    public void evictProductCache2(@PathVariable Long id) {
        productService.evictProductCache(id);
    }
}
