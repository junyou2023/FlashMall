/*
 * 整体定位：DTO 是 HTTP JSON 与 Java 方法之间的边界对象，不是数据库实体，也不承载模型实现细节。
 * 第一性原因：先把外部输入收敛成稳定契约，下游 Service 才能只处理“已经合法的问题”。
 */
package com.flashmall.ai.api.dto; // api.dto 表明它只属于接口层，避免被误当成领域模型复用。

import jakarta.validation.constraints.NotBlank; // Jakarta Validation 的声明式约束，由 WebFlux 在方法执行前触发。

/**
 * HTTP 请求进入 AI 链路后的最小输入契约。
 *
 * Jackson 把 {"message":"..."} 反序列化成该 record；Controller 的 @Valid 再执行字段约束。
 * 第一阶段只有单条问题，不提前加入 conversationId，避免尚未实现的多轮记忆污染接口语义。
 */
public record ChatRequest( // record 自动生成不可变字段、构造器和 message()，DTO 无需可变 setter。
        @NotBlank(message = "message 不能为空") String message // 同时拒绝 null、空串和纯空白，阻止无意义模型调用与费用消耗。
) { // 空 record 体表示数据结构只有声明出的 message，不隐藏额外业务行为。
}
