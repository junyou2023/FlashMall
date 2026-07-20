/*
 * 整体定位：Controller 是系统边界翻译器，把外部 HTTP/JSON 翻译为内部 Java 调用，再把 Flux 翻译为 SSE。
 * 第一性原因：边界层只做协议适配，Prompt 和业务规则才能在脱离 HTTP 的测试或其他入口中继续复用。
 */
package com.flashmall.ai.api; // api 包只处理外部协议，不允许向内泄漏 WebFlux 类型以外的业务实现细节。

import com.flashmall.ai.api.dto.ChatRequest; // 输入 DTO 定义客户端必须遵守的 JSON 契约。
import com.flashmall.ai.application.AiChatService; // application 层提供“流式问模型”用例，Controller 只负责调用。
import jakarta.validation.Valid; // 触发 ChatRequest 字段上的 Jakarta Validation 约束。
import org.springframework.http.MediaType; // 提供标准 MIME 类型常量，避免手写 text/event-stream 拼错。
import org.springframework.http.codec.ServerSentEvent; // WebFlux 对单个 SSE 事件的结构化表示：event、data 等字段。
import org.springframework.web.bind.annotation.PostMapping; // 把方法绑定到 HTTP POST，问题放请求体而不是 URL。
import org.springframework.web.bind.annotation.RequestBody; // 指示 Jackson 从 JSON 请求体创建 ChatRequest。
import org.springframework.web.bind.annotation.RequestMapping; // 为类内接口声明统一 URL 前缀。
import org.springframework.web.bind.annotation.RestController; // 等价于 @Controller + @ResponseBody，返回值直接写入 HTTP 响应。
import reactor.core.publisher.Flux; // 返回多个 SSE 事件，并在事件到达时逐个向客户端写出。
import reactor.core.publisher.Mono; // 表示 0..1 个异步元素；这里承载单个 done 或 error 事件。

/**
 * AI 对话链路的 HTTP/SSE 适配器。
 *
 * 上游是浏览器/curl，下游是 AiChatService；Controller 不拼 Prompt、不查询订单，也不直接认识模型供应商。
 * 当响应尚未开始时，校验异常走 GlobalExceptionHandler；开始流式响应后，模型异常只能变成 SSE error。
 */
@RestController // 让 Spring 扫描并注册该 HTTP 控制器，同时自动序列化其返回值。
@RequestMapping("/api/ai/chat") // 稳定资源前缀；具体动作由方法级 /stream 补全。
public class AiChatController { // 类中没有请求状态，单例 Controller 可以并发服务多个用户。

    private static final String STREAM_ERROR_MESSAGE = "AI 服务暂时不可用，请稍后重试。"; // 对外隐藏供应商异常细节，避免泄漏内部地址或鉴权信息。

    private final AiChatService aiChatService; // Controller 唯一依赖是应用用例，而不是 ChatClient 或核心交易组件。

    public AiChatController(AiChatService aiChatService) { // 构造器注入使依赖显式、不可为空，也方便测试用 mock 替换。
        this.aiChatService = aiChatService; // 保存 Spring 容器传入的单例 Service，后续每次请求复用它。
    }

    /**
     * Flux 不是 SSE：Flux 是 JVM 内的数据流抽象，SSE 是 HTTP 线上可被浏览器理解的帧格式。
     * 运算符顺序就是错误语义：先 concat done，再在最外层兜底 error，因此异常时 done 不会被发送。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE) // 完整路径为 POST /api/ai/chat/stream，响应声明为 SSE。
    public Flux<ServerSentEvent<String>> streamChat( // 返回 Flux 后 WebFlux 会订阅它，并按背压逐个编码事件。
            @Valid @RequestBody ChatRequest request // 先 JSON 反序列化，再校验；失败时方法体不会执行，也不会产生模型费用。
    ) { // 参数成功解码并通过校验后才进入方法体，此时开始组装惰性的响应流。
        return aiChatService.streamChat(request.message()) // 从不可变 DTO 取出问题，进入不含 HTTP 细节的模型文本流。
                .map(content -> buildEvent("message", content)) // 每到一段文本就同步包装为 message 事件，保持原始顺序。
                .concatWith(Mono.just(buildEvent("done", "[DONE]"))) // 只有前面的 Flux 正常 complete，才串接唯一 done 事件。
                .onErrorResume(error -> Mono.just(buildEvent("error", STREAM_ERROR_MESSAGE))); // 任一上游异常改为单个 error 后正常结束，连接不暴露堆栈。
    }

    /**
     * SSE 的一行本质是“字段名:字段值”；规范允许冒号后有一个分隔空格，客户端解析时会移除它。
     * 因此这里主动补一个协议空格，模型片段自己原有的前导空格才能在跨网络后保持不变。
     */
    private ServerSentEvent<String> buildEvent(String event, String data) { // 集中构造可确保 message/done/error 使用完全一致的编码规则。
        return ServerSentEvent.builder(" " + data) // 第一个空格属于 SSE 分隔语法；解析后客户端得到的仍是原始 data。
                .event(event) // 写出 event:message 等事件名，客户端可按类型分别监听而非猜测 data。
                .build(); // 将可变 Builder 固化成不可变事件对象，交给 WebFlux SSE Writer 编码。
    }
}
