/*
 * 整体定位：这是 HTTP 边界的可重复规格，证明请求如何进入、事件如何流出、异常在哪一层被截住。
 * 第一性原因：真实模型既慢、收费又不确定；协议测试必须隔离外部随机性，才能稳定发现本地回归。
 */
package com.flashmall.ai.api; // 测试放在被测 Controller 同包，命名直接对应生产边界。

import com.flashmall.ai.application.AiChatService; // Service 将被 Mockito 替换，测试不创建真实 ChatClient 和网络请求。
import org.junit.jupiter.api.Test; // 标记可由 JUnit 5 独立执行的测试方法。
import org.springframework.beans.factory.annotation.Autowired; // 从测试 Spring 容器取出已经配置好的 WebTestClient。
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest; // 只加载 WebFlux/Controller 切片，不启动完整生产应用。
import org.springframework.core.ParameterizedTypeReference; // 在运行时保留 ServerSentEvent<String> 泛型，指导响应解码器。
import org.springframework.http.MediaType; // 提供 JSON 请求和 SSE 响应的标准 Content-Type 常量。
import org.springframework.http.codec.ServerSentEvent; // 让断言直接读取解析后的 event/data，而不是手工切割文本行。
import org.springframework.test.context.bean.override.mockito.MockitoBean; // 用 Mockito mock 覆盖测试容器中的 AiChatService Bean。
import org.springframework.test.web.reactive.server.FluxExchangeResult; // 保存响应状态、头和仍可逐项订阅的响应体 Flux。
import org.springframework.test.web.reactive.server.WebTestClient; // 在内存中经过真实 WebFlux 路由/编解码链发送测试请求。
import reactor.core.publisher.Flux; // 构造确定的文本片段流或错误流，代替不可预测的模型输出。
import reactor.test.StepVerifier; // 按时间顺序消费 Publisher，并断言每个 next/error/complete 信号。

import static org.assertj.core.api.Assertions.assertThat; // AssertJ 提供可读的值断言和失败信息。
import static org.mockito.Mockito.verifyNoInteractions; // 证明校验失败在 Service 前被截断，模型完全没被调用。
import static org.mockito.Mockito.when; // 为 mock 指定“给定调用返回什么 Flux”。

/**
 * Controller 切片测试覆盖三条协议分支：正常 message→done、参数错误 400、模型异常 error 且无 done。
 * 它测试真实 JSON、校验、路由、SSE Writer/Reader，只 mock 网络边界前的 AiChatService。
 */
@WebFluxTest(AiChatController.class) // 限定只装配该 Controller 及相关 Web 组件，使测试快且故障定位明确。
class AiChatControllerTest { // 包级可见即可被 JUnit 5 发现，不扩大生产 API。

    @Autowired // 由 @WebFluxTest 创建，绑定当前内存 ApplicationContext 而非真实 8081 端口。
    private WebTestClient webTestClient; // 所有测试通过它走完整 HTTP 编解码链。

    @MockitoBean // 在 Spring 容器中注册 mock，Controller 构造器拿到的就是这个对象。
    private AiChatService aiChatService; // 控制模型上游信号，确保测试零费用、无网络且结果确定。

    @Test // 正常链路规格：三个增量片段必须保持顺序，随后出现且只出现一次 done。
    void shouldStreamMessageEventsInOrderAndFinishWithDone() { // 方法名本身描述 Given/When/Then 的预期结果。
        when(aiChatService.streamChat("请介绍一下 FlashMall")) // 只有参数完全相同的调用才命中该桩，顺便验证 DTO 传值。
                .thenReturn(Flux.just("FlashMall", " 是一个", " AI 电商助手")); // Flux.just 同步依次发出三段文本后 complete。

        FluxExchangeResult<ServerSentEvent<String>> result = webTestClient.post() // 创建 POST 请求并声明响应体将持续产生 SSE 事件。
                .uri("/api/ai/chat/stream") // 经过类级 /api/ai/chat 与方法级 /stream 合成的真实路由。
                .contentType(MediaType.APPLICATION_JSON) // 告诉服务端用 Jackson 把请求体解码成 ChatRequest。
                .bodyValue("{\"message\":\"请介绍一下 FlashMall\"}") // 发送真实 JSON 字符串，而不是绕过 HTTP 直接构造 DTO。
                .exchange() // 执行请求；此刻 WebFlux 开始订阅 Controller 返回的 SSE Flux。
                .expectStatus().isOk() // 流建立成功时响应头是 200，后续内容通过事件表达。
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM) // 允许 charset 等参数，但核心 MIME 必须是 SSE。
                .returnResult(new ParameterizedTypeReference<>() { // 匿名子类保留 String 泛型，避免类型擦除后 data 被当成 Object。
                }); // 不一次性聚合无限可能的流，而是返回可交给 StepVerifier 的响应体。

        StepVerifier.create(result.getResponseBody()) // 订阅已被 SSE 解码器还原的事件流，从客户端视角验证协议。
                .assertNext(event -> assertEvent(event, "message", "FlashMall")) // 第 1 段证明首个模型片段立即到达。
                .assertNext(event -> assertEvent(event, "message", " 是一个")) // 第 2 段保留前导空格，验证 SSE 分隔空格修复。
                .assertNext(event -> assertEvent(event, "message", " AI 电商助手")) // 第 3 段证明上游顺序未被异步链打乱。
                .assertNext(event -> assertEvent(event, "done", "[DONE]")) // 上游 complete 后，Controller 才追加协议终止标记。
                .verifyComplete(); // done 事件后 Publisher 必须真正完成，不能悬挂连接或再发额外事件。
    }

    @Test // 输入边界规格：纯空白既没有业务意义，也不应产生模型调用成本。
    void shouldReturnBadRequestWhenMessageIsBlank() { // 方法名明确非法输入应在 HTTP 边界被拒绝。
        webTestClient.post() // 创建与正常链路相同的 POST，唯一变化是非法 message。
                .uri("/api/ai/chat/stream") // 仍命中同一路由，确保失败来自校验而不是 404。
                .contentType(MediaType.APPLICATION_JSON) // JSON 语法合法，因此能够形成 ChatRequest 并进入 Bean Validation。
                .bodyValue("{\"message\":\"   \"}") // 三个空格证明 @NotBlank 不只拦截空串，也拦截纯空白。
                .exchange() // 执行后 @Valid 在 Controller 方法体之前抛出 WebExchangeBindException。
                .expectStatus().isBadRequest() // GlobalExceptionHandler 将校验异常翻译为 HTTP 400。
                .expectBody() // 400 是有限 JSON 响应，不是 SSE 流，因此可一次性聚合响应体。
                .jsonPath("$.title").isEqualTo("请求参数校验失败") // 验证稳定错误类别，便于前端展示与监控聚合。
                .jsonPath("$.detail").isEqualTo("message 不能为空"); // 验证字段约束信息确实到达客户端。

        verifyNoInteractions(aiChatService); // 最关键的边界断言：非法输入没有越过 Controller，更没有访问模型。
    }

    @Test // 异步失败规格：HTTP 头已发出后不能改成 500，只能在 SSE 协议内发送 error。
    void shouldReturnErrorEventWithoutDoneWhenModelStreamFails() { // 方法名强调 error 与 done 在协议上互斥。
        when(aiChatService.streamChat("触发异常")) // 对合法输入模拟模型流在订阅后失败。
                .thenReturn(Flux.error(new IllegalStateException("模拟模型不可用"))); // Flux.error 不发文本，直接发送 onError 信号。

        FluxExchangeResult<ServerSentEvent<String>> result = webTestClient.post() // 与正常请求使用同一 HTTP/SSE 客户端链路。
                .uri("/api/ai/chat/stream") // 命中流式接口，确保异常由 onErrorResume 处理。
                .contentType(MediaType.APPLICATION_JSON) // 请求体仍是合法 JSON，排除解析和校验因素。
                .bodyValue("{\"message\":\"触发异常\"}") // 合法 JSON 和 message 让调用能够进入 mock Service。
                .exchange() // 订阅时上游发出 error，Controller 将其替换为友好 SSE 事件。
                .expectStatus().isOk() // 流式响应一旦建立就是 200，错误语义由 event:error 承载。
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM) // 即使模型失败，响应协议仍必须保持 SSE。
                .returnResult(new ParameterizedTypeReference<>() { // 保留 ServerSentEvent<String> 类型供 SSE Reader 解码。
                }); // 返回异步响应体，不阻塞等待一个本来可能很长的模型答案。

        StepVerifier.create(result.getResponseBody()) // 从客户端观察最终事件，而不是断言内部抛出的 Java 异常。
                .assertNext(event -> assertEvent(event, "error", "AI 服务暂时不可用，请稍后重试。")) // 技术异常被安全文案替代。
                .verifyComplete(); // error 事件后直接完成；若错误地追加 done，这里会因出现额外事件而失败。
    }

    private static void assertEvent( // 抽取重复断言，让三个片段测试强调协议顺序而不是样板代码。
            ServerSentEvent<String> event, // WebFlux 已解析完成的单个客户端事件。
            String name, // 期望的 event 字段：message、done 或 error。
            String data // 期望的 data 字段，必须保留模型原始文本细节。
    ) { // 三个参数共同定义一个完整 SSE 事件断言：对象、类型和负载。
        assertThat(event.event()).isEqualTo(name); // 先确认客户端可按正确事件类型分发处理逻辑。
        assertThat(event.data()).isEqualTo(data); // 再确认事件负载没有被编码、空格规则或流操作改变。
    }
}
