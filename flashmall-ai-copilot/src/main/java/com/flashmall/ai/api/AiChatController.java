package com.flashmall.ai.api;

import com.flashmall.ai.api.dto.ChatRequest;
import com.flashmall.ai.application.AiChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AI 对话链路的 HTTP/SSE 适配器。
 *
 * Controller 只把 HTTP 请求翻译成 Java 调用，并把文本 Flux 翻译成 SSE；
 * Prompt、RAG 和订单逻辑都不属于这一层，因而不会侵入原交易链路。
 */
@RestController
@RequestMapping("/api/ai/chat")
public class AiChatController {

    private static final String STREAM_ERROR_MESSAGE = "AI 服务暂时不可用，请稍后重试。";

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    /**
     * Flux 不是 SSE：Flux 承载 Java 内部文本片段，SSE 负责通过 HTTP 逐段推送给客户端。
     * 正常结束追加 done；上游失败则只发送友好 error，避免把失败链路标记为已完成。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ChatRequest request) {
        return aiChatService.streamChat(request.message())
                .map(content -> buildEvent("message", content))
                .concatWith(Mono.just(buildEvent("done", "[DONE]")))
                .onErrorResume(error -> Mono.just(buildEvent("error", STREAM_ERROR_MESSAGE)));
    }

    /**
     * SSE 解析器会把 data 冒号后的第一个空格视为协议分隔符并移除。
     * 适配层统一补充分隔空格，才能让模型片段原本的前导空格在客户端解析后仍被保留。
     */
    private ServerSentEvent<String> buildEvent(String event, String data) {
        return ServerSentEvent.builder(" " + data)
                .event(event)
                .build();
    }
}
