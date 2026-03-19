package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * 【Spring Boot 入门第 1 课】启动类 - 整个应用的入口
 * 
 * ============================================================================
 * 问题 1：这个类是干什么的？
 * ============================================================================
 * 简单说：这就是一个 Java 程序的 main 方法入口，类似 C 语言的 main()
 * 
 * 传统 Java Web 项目（SSM）：
 *   - 需要 web.xml 配置 DispatcherServlet
 *   - 需要 spring-mvc.xml 配置 Bean
 *   - 需要 jdbc.properties 配置数据库
 *   - 至少 5 个配置文件，麻烦！
 * 
 * Spring Boot 项目：
 *   - 只要这一个启动类
 *   - 内嵌 Tomcat，直接 java -jar 运行
 *   - 约定优于配置（默认帮你配好 80% 的东西）
 * 
 * ============================================================================
 * 问题 2：@SpringBootApplication 是什么？
 * ============================================================================
 * 这是一个"组合注解"，等于三个注解加起来：
 * 
 * ① @SpringBootConfiguration
 *    → 标记这是一个配置类（相当于@Configuration）
 * 
 * ② @EnableAutoConfiguration（⭐核心中的核心）
 *    → 开启自动配置，Spring Boot 的黑科技
 *    → 原理：扫描 classpath 下的所有 starter 依赖
 *      比如发现你有 spring-boot-starter-data-redis
 *      就自动创建 RedisTemplate、StringRedisTemplate 这些 Bean
 * 
 * ③ @ComponentScan
 *    → 自动扫描当前包及子包下的所有@Component、@Service、@Controller
 *    → 所以你写的 Service 不用手动 new，直接@Autowired 就能用
 * 
 * ============================================================================
 * 问题 3：@EnableCaching 是干什么的？
 * ============================================================================
 * 作用：开启基于注解的缓存支持
 * 
 * 没有它：你写的@Cacheable、@CacheEvict 不会生效，就是普通方法
 * 有了它：Spring 会用 AOP 拦截这些注解，自动执行缓存逻辑
 * 
 * 底层原理（面试必考）：
 * 1. Spring 启动时扫描所有带@Cacheable 的方法
 * 2. 生成一个代理对象（Proxy）
 * 3. 当你调用这个方法时，实际调用的是代理对象
 * 4. 代理先查缓存，有就返回，没有才执行原方法
 * 
 * ============================================================================
 * 问题 4：main 方法里那行代码在干嘛？
 * ============================================================================
 * SpringApplication.run(DemoApplication.class, args)
 * 
 * 这行代码做了 4 件事：
 * 1. 创建 Spring 应用上下文（ApplicationContext）
 * 2. 扫描所有配置类和组件
 * 3. 创建所有 Bean（包括 Controller、Service、Mapper）
 * 4. 启动内嵌 Tomcat（默认 8080 端口）
 * 
 * 所以启动后你才能访问 http://localhost:8080/products
 */
@SpringBootApplication
@EnableCaching // 【重要】开启缓存注解支持，没有它@Cacheable 不生效

public class DemoApplication {

    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        // 返回值是一个 ApplicationContext，代表整个 Spring 容器
        SpringApplication.run(DemoApplication.class, args);
        
        // 【思考题】启动成功后你能看到什么日志？
        // 答案：Tomcat started on port(s): 8080 (http)
        //       Started DemoApplication in X seconds
    }

}
