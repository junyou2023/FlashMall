package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
    // 当前阶段不需要复杂线程池配置
    // 先开启 @Async 功能就够你理解“延迟双删”的落地方式
}
