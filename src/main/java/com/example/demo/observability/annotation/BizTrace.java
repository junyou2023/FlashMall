package com.example.demo.observability.annotation;

import java.lang.annotation.*;

/**
 * 业务追踪注解（第一站）
 *
 * 目标：
 * - 先把“统一耗时 + 成功失败 + 基础业务标签”沉到 AOP
 * - 不破坏现有主链路业务逻辑
 * - 让注解/AOP 能直接落在当前电商真实接口上
 *
 * 说明：
 * 当前阶段先做“最小可落地版本”：
 * - requestIdSpel：从入参提取 requestId
 * - bizTagSpel：从入参提取基础业务标签（Map）
 *
 * 下一层再接更复杂能力（比如返回值标签、异常分类、统一错误码）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BizTrace {

    /**
     * 业务场景名
     *
     * 例子：
     * - order.create.entry
     * - product.detail.read
     */
    String scene();

    /**
     * 日志里的 path 标签（允许和真实 URL 保持一致）
     */
    String path();

    /**
     * 日志里的 method 标签
     */
    String method();

    /**
     * SpEL：提取 requestId。
     * 例如 "#request.requestId"。
     */
    String requestIdSpel() default "";

    /**
     * SpEL：提取业务标签 Map。
     * 例如 "{'userId': #request.userId, 'productId': #request.productId}"。
     */
    String bizTagSpel() default "";
}
