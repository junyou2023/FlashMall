/*
 * 整体定位：这是 AI 进程的唯一启动入口；它和 8080 核心交易进程分别编译、启动、失败和扩容。
 * 第一性原因：只有先隔离进程与依赖，AI SDK 的变化或模型故障才不会把订单、库存链路一起拖垮。
 */
package com.flashmall.ai; // 根包决定默认组件扫描边界：只扫描 com.flashmall.ai，不扫描 com.example.demo。

import org.springframework.boot.SpringApplication; // 提供“创建并启动 Spring 容器”的统一入口。
import org.springframework.boot.autoconfigure.SpringBootApplication; // 把配置类、自动配置、组件扫描三个能力合成一个注解。

/**
 * AI Copilot 独立应用入口。
 *
 * 上游是操作系统或 IDEA 启动 Java 进程，下游是 Spring 容器、Netty Web 服务器和 AI Bean。
 * 当前只装配流式对话；未来 RAG、记忆和工具调用继续留在本应用，通过 HTTP 访问 FlashMall。
 */
@SpringBootApplication // 等价于 @Configuration + @EnableAutoConfiguration + @ComponentScan。
public class FlashMallAiApplication { // public 让 JVM 和 Spring Boot 启动器都能定位这个入口类型。

    public static void main(String[] args) { // JVM 只认识这个固定签名；args 承载 --server.port 等启动参数。
        SpringApplication.run(FlashMallAiApplication.class, args); // 创建 Environment 和 ApplicationContext，装配 Bean，再启动 8081 服务。
    }
}
