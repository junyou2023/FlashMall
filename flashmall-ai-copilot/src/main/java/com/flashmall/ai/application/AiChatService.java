/*
 * 整体定位：application 层编排“如何问模型”，上接 Controller 的合法问题，下接 Spring AI 的模型抽象。
 * 第一性原因：HTTP 协议与模型协议变化速度不同，分层后两边可以独立演进，核心交易系统也无需依赖 Spring AI。
 */
package com.flashmall.ai.application; // application 表示用例编排层：决定调用顺序，不负责 HTTP 编解码。

import org.slf4j.Logger; // 日志门面接口，让业务代码不绑定 Logback 等具体实现。
import org.slf4j.LoggerFactory; // 按当前类创建日志器，日志才能带上准确来源。
import org.springframework.ai.chat.client.ChatClient; // Spring AI 的高层对话客户端，屏蔽供应商请求/响应对象差异。
import org.springframework.stereotype.Service; // 把该类注册为 Spring Bean，供 Controller 通过构造器注入。
import reactor.core.publisher.Flux; // 表示 0..N 个异步元素；此处每个元素是一段模型文本。

import java.util.Objects; // 提供 Objects::nonNull 方法引用，过滤供应商可能返回的 null 片段。

/**
 * 模型调用链路位于 HTTP 适配层和 Spring AI 之间，集中维护 Prompt 与 ChatClient 调用。
 *
 * 三层流的关系：模型在远端增量生成 → Flux 在 JVM 内传递文本 → Controller 编码为 SSE 网络事件。
 * 当前只打通模型流；后续 RAG、记忆和 Tool Calling 在 application/adapter 扩展，不侵入交易链路。
 */
@Service // 语义上标记“应用服务”，技术上使组件扫描创建单例 Bean。
public class AiChatService { // 类只输出 Flux<String>，因此它不知道浏览器、SSE 或 HTTP 状态码。

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class); // static final 保证全类共享且不可替换，不为每次请求重复创建。

    private static final String SYSTEM_PROMPT = "你是 FlashMall 的智能助手。回答要准确、简洁。不知道的信息必须明确说明不知道，不得编造商品、库存和订单数据。"; // 所有请求共享的最高层防幻觉约束；没有查询能力时宁可说不知道。

    private final ChatClient chatClient; // final 保证构造完成后依赖不变；Service 本身无请求级可变状态，可安全复用。

    public AiChatService(ChatClient.Builder chatClientBuilder) { // Spring AI 自动配置根据 application.yml 创建 Builder 并由 Spring 注入。
        this.chatClient = chatClientBuilder.build(); // build 只创建客户端门面；真正网络请求要等 Flux 被订阅时才发生。
    }

    /**
     * 把已校验的用户问题交给模型，并将模型的增量输出保持为纯文本流。
     * Reactor 默认是惰性的：Controller 返回 Flux 后，客户端订阅响应才会沿链路向上触发模型调用。
     */
    public Flux<String> streamChat(String message) { // 输入来自通过 @NotBlank 的 DTO，输出只表达文本片段，不混入 SSE 元数据。
        return chatClient.prompt() // 为本次请求创建独立 Prompt 规格，避免不同用户请求共享可变上下文。
                .system(SYSTEM_PROMPT) // 先设置系统规则，定义助手身份、回答风格和禁止编造的安全边界。
                .user(message) // 再放入本次用户问题；它是数据而不是系统规则，优先级低于 system。
                .stream() // 选择流式模型接口；若用 call()，必须等完整答案生成后才能返回。
                .content() // 从包含角色、元数据和用量信息的响应中，只投影出 Controller 需要的文本。
                .filter(Objects::nonNull) // 防御性丢弃 null，避免后续 SSE 编码器收到非法 data。
                .filter(content -> !content.isBlank()) // 丢弃空串和纯空白事件，减少无意义网络帧；非空片段的前导空格仍保留。
                .doOnSubscribe(subscription -> log.info("开始调用 AI 流式对话")) // 订阅才代表链路真正启动；这里不记录问题内容和 API Key。
                .doOnComplete(() -> log.info("AI 流式对话完成")) // 只有上游正常结束才触发，随后 Controller 才有资格发送 done。
                .doOnError(error -> log.error("AI 流式对话失败", error)); // 记录技术堆栈并继续传播错误，由 Controller 转成用户友好 error 事件。
    }
}
