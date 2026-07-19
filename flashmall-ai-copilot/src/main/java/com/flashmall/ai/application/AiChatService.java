package com.flashmall.ai.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * 模型调用链路位于 HTTP 适配层和 Spring AI 之间，集中维护 Prompt 与 ChatClient 调用。
 *
 * 这里返回的 Flux 只是 Java 内部持续到达的文本片段，不携带 SSE 协议细节。
 * 当前阶段只打通模型流；后续 RAG、记忆和 Tool Calling 在这一层及其 adapter 扩展。
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private static final String SYSTEM_PROMPT = """
            你是 FlashMall 的智能助手。回答要准确、简洁。不知道的信息必须明确说明不知道，不得编造商品、库存和订单数据。
            """;

    private final ChatClient chatClient;

    public AiChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 把已校验的用户问题交给模型，并将模型的增量输出保持为纯文本流。
     * 空片段在这里过滤，避免 Controller 产生没有业务内容的 message 事件。
     */
    public Flux<String> streamChat(String message) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .stream()
                .content()
                .filter(Objects::nonNull)
                .filter(content -> !content.isBlank())
                .doOnSubscribe(subscription -> log.info("开始调用 AI 流式对话"))
                .doOnComplete(() -> log.info("AI 流式对话完成"))
                .doOnError(error -> log.error("AI 流式对话失败", error));
    }
}
