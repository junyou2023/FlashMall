package com.flashmall.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Copilot 独立应用入口。
 *
 * 当前阶段它只装配模型流式对话链路，不扫描也不依赖 FlashMall 核心交易应用的 Bean。
 * 后续 RAG、记忆和工具调用仍在本应用内扩展，并通过稳定 HTTP 接口访问核心业务。
 */
@SpringBootApplication
public class FlashMallAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashMallAiApplication.class, args);
    }
}
