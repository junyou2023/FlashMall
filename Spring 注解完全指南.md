# 🎯 Spring 注解完全指南（从@Autowired 开始）

## 🔍 第一部分：@Autowired 到底是什么？

### 1.1 本质定义

**@Autowired = "自动按类型装配"**

Spring 看到你这个注解，就会去容器里找："有没有这个类型的 Bean？有的话就塞给你！"

---

### 1.2 三种用法（必须全部掌握）

#### **用法 1：字段注入（不推荐但最常见）**

```java
@Service
public class ProductService {
    
    @Autowired  // ← Spring 看到这个注解
    private ProductMapper productMapper;  // ← 就去容器里找 ProductMapper 类型的 Bean
    
    public List<Product> getAllProducts() {
        return productMapper.findAll();  // ← 直接能用，不用 new
    }
}
```

**发生了什么？**
```
Spring 启动时：
1. 扫描到 ProductService 有@Service → 创建这个 Bean
2. 发现有个字段有@Autowired → 准备注入
3. 去容器里找 ProductMapper 类型的 Bean
4. 找到后，用反射设置：productService.productMapper = (ProductMapper) 找到的对象
```

**❌ 为什么不推荐？**
- 依赖隐藏了（不看注解不知道它依赖谁）
- 无法保证 final（字段可以被修改）
- 单元测试不方便（不能直接 new）

---

#### **用法 2：构造函数注入（✅ 官方推荐）**

```java
@RestController
public class ProductController {
    
    private final ProductService productService;  // ← final 修饰，保证不被修改
    
    // Spring 看到这个构造函数，自动注入
    public ProductController(ProductService productService) {
        this.productService = productService;  // ← 依赖关系清晰
    }
}
```

**为什么推荐？**
- ✅ 依赖关系一目了然（看构造函数就知道依赖谁）
- ✅ 可以用 final 修饰（更安全）
- ✅ 方便单元测试（可以直接 new Controller 传入 mock 对象）
- ✅ 避免循环依赖

**你的项目里用的就是这种！** 👍

---

#### **用法 3：Setter 方法注入（较少用）**

```java
@Service
public class OrderService {
    
    private ProductMapper productMapper;
    
    @Autowired  // Spring 会调用这个 setter 方法注入
    public void setProductMapper(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }
}
```

**使用场景：**
- 可选依赖（不是必须的）
- 需要在运行时动态切换依赖

---

### 1.3 @Autowired 的工作原理

```
┌─────────────────────────────────────────────────────────┐
│ Spring 启动时的处理流程                                  │
└─────────────────────────────────────────────────────────┘

第 1 步：扫描所有带@Component/@Service/@Controller 的类
       ↓
第 2 步：创建这些类的 Bean（对象）
       ↓
第 3 步：把 Bean 放入容器（ApplicationContext）
       ↓
第 4 步：处理@Autowired
       ├→ 看到字段/构造器/setter 有@Autowired
       ├→ 去容器里找对应的 Bean
       ├→ 找到了 → 注入
       └→ 没找到 → 抛异常（默认行为）
```

---

### 1.4 如果容器里有多个同类型的 Bean 怎么办？

**问题场景：**
```java
// 你有两个 ProductMapper 的实现
@Service
public class MySQLProductMapper implements ProductMapper { ... }

@Service
public class RedisProductMapper implements ProductMapper { ... }

// 现在你想注入
@Service
public class ProductService {
    
    @Autowired
    private ProductMapper productMapper;  // ← 报错！有两个实现，不知道该注入哪个
}
```

**解决方案 1：@Qualifier 指定名字**

```java
@Autowired
@Qualifier("mySQLProductMapper")  // ← 明确指定要哪个
private ProductMapper productMapper;
```

**解决方案 2：@Primary 标记首选**

```java
@Service
@Primary  // ← 标记这个是默认的
public class MySQLProductMapper implements ProductMapper { ... }
```

**解决方案 3：@Resource 按名字注入（JDK 提供的）**

```java
@Resource(name = "mySQLProductMapper")  // ← 按名字找，不是按类型
private ProductMapper productMapper;
```

---

## 📚 第二部分：Spring 核心注解体系

### 2.1 Bean 定义注解（告诉 Spring"我是个 Bean"）

| 注解 | 作用 | 使用层级 | 特殊功能 |
|------|------|----------|----------|
| **@Component** | 通用组件 | 任何类 | 最基础的注解 |
| **@Service** | 业务逻辑层 | Service 类 | 语义化，无特殊功能 |
| **@Repository** | 数据访问层 | DAO/Mapper 类 | 会把数据库异常转成 Spring 异常 |
| **@Controller** | Web 控制器 | Controller 类 | 配合@RequestMapping 使用 |
| **@RestController** | RESTful 控制器 | Controller 类 | = @Controller + @ResponseBody |
| **@Configuration** | 配置类 | 配置类 | 配合@Bean 使用 |

**示例：**
```java
@Component          // 通用组件
public class MyUtils { ... }

@Service            // 业务逻辑
public class UserService { ... }

@Repository         // 数据访问（有异常转换）
public class UserDAO { ... }

@Controller         // Web 控制器（返回视图）
public class UserController { 
    @RequestMapping("/users")
    public String list() { return "userList"; }  // 跳转页面
}

@RestController     // RESTful 接口（返回 JSON）
public class ProductController {
    @GetMapping("/products")
    public List<Product> list() { ... }  // 返回 JSON
}

@Configuration      // 配置类
public class RedisConfig {
    @Bean           // 声明一个 Bean
    public RedisTemplate redisTemplate() {
        return new RedisTemplate();
    }
}
```

---

### 2.2 依赖注入注解（告诉 Spring"我需要依赖"）

| 注解 | 来源 | 注入方式 | 特点 |
|------|------|----------|------|
| **@Autowired** | Spring | 按类型 | Spring 提供的，功能最强 |
| **@Resource** | JDK | 按名字 | JSR-250 标准，更规范 |
| **@Inject** | CDI | 按类型 | Java EE 标准，类似@Autowired |

**对比：**
```java
// @Autowired（Spring）- 按类型注入
@Autowired
private ProductService productService;  // 找 ProductService 类型的 Bean

// @Resource（JDK）- 默认按名字，可以指定
@Resource
private ProductService productService;  // 找名字叫"productService"的 Bean

@Resource(name = "myProductService")
private ProductService productService;  // 找名字叫"myProductService"的 Bean

// @Inject（Java EE）- 按类型，类似@Autowired
@Inject
private ProductService productService;
```

**推荐：优先用构造函数注入（不需要@Autowired）！**

---

### 2.3 配置相关注解

#### **@Value - 读取配置文件**

```java
@Service
public class ProductService {
    
    // 读取 application.yml 中的值
    @Value("${spring.datasource.url}")
    private String dbUrl;
    
    @Value("${server.port:8080}")  // :8080 是默认值
    private int port;
    
    @Value("${feature.enabled:false}")  // 默认 false
    private boolean featureEnabled;
}
```

**对应配置文件：**
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/demo
server:
  port: 8080
feature:
  enabled: true
```

---

#### **@Configuration + @Bean - 手动配置 Bean**

```java
@Configuration  // 标记这是一个配置类
public class RedisConfig {
    
    @Bean  // 声明一个 Bean，Spring 会自动创建并管理
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory factory) {
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
    
    // 这个方法会被 Spring 调用
    // 返回值会被放入 Spring 容器
    // 其他类需要时可以@Autowired
}
```

**使用：**
```java
@Service
public class ProductService {
    
    @Autowired  // 可以注入上面配置的 RedisTemplate
    private RedisTemplate<String, Object> redisTemplate;
}
```

---

### 2.4 AOP 相关注解（面向切面编程）

#### **@Cacheable - 缓存**

```java
@Service
public class ProductService {
    
    @Cacheable(cacheNames = "product:detail", key = "#id")
    public Product getProductById(Long id) {
        // 第一次调用：查 DB → 写缓存
        // 第二次调用：读缓存 → 不执行这里
        return productMapper.findById(id);
    }
}
```

#### **@CacheEvict - 清除缓存**

```java
@Service
public class ProductService {
    
    @CacheEvict(cacheNames = "product:detail", key = "#id")
    public void evictCache(Long id) {
        // 方法执行后，删除缓存
    }
}
```

#### **@Transactional - 事务**

```java
@Service
public class OrderService {
    
    @Transactional  // 这个方法里的所有数据库操作在一个事务里
    public Long placeOrder(Long userId, Long productId) {
        // 1. 扣减库存
        // 2. 创建订单
        // 3. 创建订单项
        
        // 任何一步抛异常 → 全部回滚
        return orderId;
    }
}
```

#### **@Async - 异步执行**

```java
@Service
public class EmailService {
    
    @Async  // 异步执行，不阻塞主线程
    public void sendEmail(String to, String content) {
        // 发送邮件（耗时操作）
        // 调用者不会等待这个方法执行完
    }
}
```

**启用异步：**
```java
@SpringBootApplication
@EnableAsync  // ← 开启异步支持
public class DemoApplication { ... }
```

---

### 2.5 Web 相关注解

#### **请求映射**

```java
@RestController
@RequestMapping("/products")  // 基础路径
public class ProductController {
    
    @GetMapping              // GET /products
    public List<Product> list() { ... }
    
    @GetMapping("/{id}")     // GET /products/1
    public Product get(@PathVariable Long id) { ... }
    
    @PostMapping             // POST /products
    public Product create(@RequestBody Product product) { ... }
    
    @PutMapping("/{id}")     // PUT /products/1
    public Product update(@PathVariable Long id, 
                         @RequestBody Product product) { ... }
    
    @DeleteMapping("/{id}")  // DELETE /products/1
    public void delete(@PathVariable Long id) { ... }
}
```

#### **参数绑定**

```java
@GetMapping("/{id}")
public Product get(
    @PathVariable Long id,           // URL 路径参数
    @RequestParam String name,       // 查询参数 ?name=xxx
    @RequestParam(defaultValue = "1") int page,  // 带默认值
    @RequestBody Product product,    // HTTP 请求体（JSON → 对象）
    HttpServletRequest request,      // 原始请求对象
    HttpServletResponse response     // 原始响应对象
) { ... }
```

---

### 2.6 Spring Boot 专用注解

#### **@SpringBootApplication - 启动类**

```java
@SpringBootApplication  // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

**拆解：**
- `@Configuration`：标记这是一个配置类
- `@ComponentScan`：扫描当前包及子包的所有组件
- `@EnableAutoConfiguration`：开启自动配置（核心！）

---

#### **@Enable 开头的一系列注解**

```java
@SpringBootApplication
@EnableCaching      // 开启缓存支持
@EnableAsync        // 开启异步支持
@EnableScheduling   // 开启定时任务支持
@EnableFeignClients // 开启 Feign 客户端（微服务）
public class DemoApplication { ... }
```

---

## 🎯 第三部分：你的项目中注解的实际应用

### 3.1 启动类

```java
@SpringBootApplication  // 核心：自动配置 + 组件扫描
@EnableCaching          // 开启缓存（没有它@Cacheable 不生效）
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

**面试考点：**
- Q: @SpringBootApplication 包含哪三个注解？
- A: @Configuration + @EnableAutoConfiguration + @ComponentScan

---

### 3.2 Controller 层

```java
@RestController  // = @Controller + @ResponseBody（返回 JSON）
public class ProductController {
    
    private final ProductService productService;
    private final RedisDataTypeService redisDataTypeService;
    
    // ✅ 构造函数注入（推荐）
    public ProductController(ProductService productService, 
                            RedisDataTypeService redisDataTypeService) {
        this.productService = productService;
        this.redisDataTypeService = redisDataTypeService;
    }
    
    @GetMapping("/products")  // 映射 GET 请求
    public List<Product> listProducts() {
        return productService.getAllProducts();
    }
    
    @GetMapping("/products/{id}")
    public Product getProduct(
        @PathVariable Long id,  // URL 路径参数
        @RequestParam(defaultValue = "1") Long userId  // 查询参数
    ) {
        Product product = productService.getProductById(id);
        if (product != null) {
            redisDataTypeService.recordProductView(userId, product);
        }
        return product;
    }
}
```

**面试考点：**
- Q: @RestController 和@Controller 有什么区别？
- A: @RestController 返回 JSON，@Controller 返回视图（页面）

---

### 3.3 Service 层

```java
@Service  // 标记这是一个 Service Bean
public class ProductService {
    
    private final ProductMapper productMapper;
    
    // 构造函数注入
    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }
    
    @Cacheable(cacheNames = "product:list")  // 缓存商品列表
    public List<Product> getAllProducts() {
        return productMapper.findAll();
    }
    
    @Cacheable(cacheNames = "product:detail", key = "#id")  // 缓存商品详情
    public Product getProductById(Long id) {
        return productMapper.findById(id);
    }
    
    @CacheEvict(cacheNames = "product:detail", key = "#id")  // 删除缓存
    public void evictProductCache(Long id) {
        // 什么都不用做，注解自动删除缓存
    }
}
```

**面试考点：**
- Q: @Cacheable 底层如何实现？
- A: AOP 动态代理，在方法前后加缓存逻辑

---

### 3.4 Mapper 层

```java
@Mapper  // MyBatis 会为这个接口生成动态代理
public interface ProductMapper {
    
    List<Product> findAll();
    
    Product findById(@Param("id") Long id);  // @Param 给参数起名字
    
    int updateStock(@Param("id") Long id, @Param("stock") int stock);
}
```

**面试考点：**
- Q: @Mapper 接口为什么不用写实现？
- A: MyBatis 在运行时生成 JDK 动态代理

---

## 💡 第四部分：一张表看懂所有 Spring 注解

| 分类 | 注解 | 作用 | 使用频率 | 重要程度 |
|------|------|------|----------|----------|
| **Bean 定义** | @Component | 通用组件 | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| | @Service | 业务逻辑层 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| | @Repository | 数据访问层 | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| | @Controller | Web 控制器 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| | @RestController | RESTful 控制器 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| | @Configuration | 配置类 | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **依赖注入** | @Autowired | 按类型注入 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| | @Resource | 按名字注入 | ⭐⭐ | ⭐⭐⭐ |
| | @Qualifier | 配合@Autowired 指定名字 | ⭐⭐ | ⭐⭐⭐ |
| **配置** | @Value | 读取配置文件 | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| | @Bean | 声明 Bean | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **AOP** | @Cacheable | 缓存 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| | @CacheEvict | 清除缓存 | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| | @Transactional | 事务 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| | @Async | 异步 | ⭐⭐ | ⭐⭐⭐ |
| **Web** | @GetMapping | GET 请求映射 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| | @PostMapping | POST 请求映射 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| | @PathVariable | URL 路径参数 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| | @RequestParam | 查询参数 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| | @RequestBody | JSON → 对象 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Spring Boot** | @SpringBootApplication | 启动类 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| | @EnableCaching | 开启缓存 | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| | @EnableAsync | 开启异步 | ⭐⭐ | ⭐⭐⭐ |

---

## ✅ 第五部分：自测题（盖住答案）

### 基础题（必须会）

1. **@Autowired 是按什么注入的？**
   <details><summary>点击查看答案</summary>
   按类型注入。如果有多个同类型 Bean，需要用@Qualifier 指定名字。
   </details>

2. **@Service 和@Component 有什么区别？**
   <details><summary>点击查看答案</summary>
   功能一样，只是语义不同。@Service 用于业务逻辑层，@Component 用于通用组件。
   </details>

3. **@RestController 和@Controller 有什么区别？**
   <details><summary>点击查看答案</summary>
   @RestController = @Controller + @ResponseBody，返回 JSON 而不是视图。
   </details>

4. **@SpringBootApplication 包含哪三个注解？**
   <details><summary>点击查看答案</summary>
   @Configuration + @EnableAutoConfiguration + @ComponentScan
   </details>

5. **@Cacheable 底层如何实现？**
   <details><summary>点击查看答案</summary>
   AOP 动态代理。Spring 生成代理类，在方法前后加缓存逻辑。
   </details>

---

### 进阶题（拉开差距）

6. **构造函数注入和@Autowired 字段注入有什么区别？**
   <details><summary>点击查看答案</summary>
   
   构造函数注入（推荐）：
   - 依赖关系清晰
   - 可以用 final
   - 方便单元测试
   - 避免循环依赖
   
   字段注入（不推荐）：
   - 依赖隐藏
   - 不能用 final
   - 测试不方便
   </details>

7. **如果容器里有多个同类型 Bean，@Autowired 怎么办？**
   <details><summary>点击查看答案</summary>
   
   三种解决方案：
   1. @Qualifier("beanName") 指定名字
   2. @Primary 标记首选
   3. @Resource(name="beanName") 按名字注入
   </details>

8. **@Bean 和@Component 有什么区别？**
   <details><summary>点击查看答案</summary>
   
   @Component：用在类上，Spring 自动扫描并创建 Bean
   @Bean：用在方法上，手动创建 Bean 并返回
   
   使用场景：
   - 自己的类用@Component/@Service
   - 第三方库的类用@Configuration + @Bean
   </details>

9. **@Transactional 在什么情况下会失效？**
   <details><summary>点击查看答案</summary>
   
   - 方法不是 public
   - 同类中方法互相调用（代理失效）
   - 异常被 catch 住了
   - 数据库引擎不支持事务（如 MyISAM）
   </details>

10. **Spring Boot 自动装配原理是什么？**
    <details><summary>点击查看答案</summary>
    
    1. 扫描 classpath 下所有 spring.factories 文件
    2. 加载所有自动配置类
    3. 根据条件注解（@ConditionalOnClass）判断是否生效
    4. 创建需要的 Bean
    
    例如：检测到有 Redis 依赖 → 创建 RedisTemplate
    </details>

---

## 🎯 第六部分：实战建议

### ✅ 正确使用方式

```java
// Controller 层
@RestController
@RequestMapping("/products")
public class ProductController {
    
    private final ProductService productService;
    
    // ✅ 构造函数注入（推荐）
    public ProductController(ProductService productService) {
        this.productService = productService;
    }
    
    @GetMapping
    public List<Product> list() {
        return productService.getAllProducts();
    }
}

// Service 层
@Service
public class ProductService {
    
    private final ProductMapper productMapper;
    
    // ✅ 构造函数注入
    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }
    
    @Cacheable(cacheNames = "product:list")
    public List<Product> getAllProducts() {
        return productMapper.findAll();
    }
}

// Mapper 层
@Mapper
public interface ProductMapper {
    List<Product> findAll();
}
```

### ❌ 错误使用方式

```java
// ❌ 字段注入（不推荐）
@Service
public class ProductService {
    
    @Autowired  // 依赖隐藏
    private ProductMapper productMapper;
    
    @Autowired  // 可以随便改，不安全
    private RedisTemplate redisTemplate;
}

// ❌ 在静态方法中使用
@Service
public class ProductService {
    
    @Autowired
    private ProductMapper productMapper;
    
    public static void staticMethod() {
        // ❌ 静态方法不能访问@Autowired 的字段
        productMapper.findAll();  
    }
}

// ❌ 循环依赖
@Service
public class ServiceA {
    @Autowired
    private ServiceB serviceB;  // A 依赖 B
}

@Service
public class ServiceB {
    @Autowired
    private ServiceA serviceA;  // B 依赖 A → 循环依赖，报错！
}
```

---

## 💡 最后的建议

### 学习路线

1. **先掌握这 5 个最常用的：**
   - @SpringBootApplication
   - @Service / @RestController
   - @Autowired（构造函数注入）
   - @GetMapping / @PostMapping
   - @Cacheable / @Transactional

2. **理解底层原理：**
   - IOC（控制反转）：Spring 帮你创建和管理对象
   - DI（依赖注入）：通过构造函数或@Autowired 注入依赖
   - AOP（面向切面）：代理模式，在方法前后加逻辑

3. **多动手实践：**
   - 启动项目，打断点看 Spring 如何创建 Bean
   - 故意制造循环依赖，看 Spring 报什么错
   - 试试字段注入 vs 构造函数注入的区别

### 面试准备

**必背 3 个问题：**
1. Spring IOC 和 AOP 是什么？
2. @Autowired 和@Resource 有什么区别？
3. @SpringBootApplication 原理是什么？

---

**现在，合上这个文档，试着回答：**

**"@Autowired 到底做了什么？"**

如果你能说出来，说明你真正理解了！

**标准答案：**
> "@Autowired 是 Spring 提供的依赖注入注解。Spring 看到后，会去容器里找对应类型的 Bean，然后通过反射注入到字段、构造函数或 setter 方法中。推荐使用构造函数注入，因为依赖关系清晰、可以用 final、方便测试。"
