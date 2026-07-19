package com.flashmall.ai.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * HTTP 请求进入 AI 链路后的最小输入契约。
 *
 * 第一阶段只接收单条问题，参数校验在 Controller 边界完成，不把无效输入传给模型层。
 */
public record ChatRequest(
        @NotBlank(message = "message 不能为空") String message
) {
}
