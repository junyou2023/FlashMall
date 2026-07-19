package com.flashmall.ai.api;

import com.flashmall.ai.application.AiChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Controller 切片测试只验证 HTTP、校验和 SSE 协议适配，不连接真实模型。
 * mock Service 返回确定文本流，使测试可以精确证明 message 顺序和最终 done 事件。
 */
@WebFluxTest(AiChatController.class)
class AiChatControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AiChatService aiChatService;

    @Test
    void shouldStreamMessageEventsInOrderAndFinishWithDone() {
        when(aiChatService.streamChat("请介绍一下 FlashMall"))
                .thenReturn(Flux.just("FlashMall", " 是一个", " AI 电商助手"));

        FluxExchangeResult<ServerSentEvent<String>> result = webTestClient.post()
                .uri("/api/ai/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"message":"请介绍一下 FlashMall"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(new ParameterizedTypeReference<>() {
                });

        StepVerifier.create(result.getResponseBody())
                .assertNext(event -> assertEvent(event, "message", "FlashMall"))
                .assertNext(event -> assertEvent(event, "message", " 是一个"))
                .assertNext(event -> assertEvent(event, "message", " AI 电商助手"))
                .assertNext(event -> assertEvent(event, "done", "[DONE]"))
                .verifyComplete();
    }

    @Test
    void shouldReturnBadRequestWhenMessageIsBlank() {
        webTestClient.post()
                .uri("/api/ai/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"message":"   "}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.title").isEqualTo("请求参数校验失败")
                .jsonPath("$.detail").isEqualTo("message 不能为空");

        verifyNoInteractions(aiChatService);
    }

    @Test
    void shouldReturnErrorEventWithoutDoneWhenModelStreamFails() {
        when(aiChatService.streamChat("触发异常"))
                .thenReturn(Flux.error(new IllegalStateException("模拟模型不可用")));

        FluxExchangeResult<ServerSentEvent<String>> result = webTestClient.post()
                .uri("/api/ai/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"message":"触发异常"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(new ParameterizedTypeReference<>() {
                });

        StepVerifier.create(result.getResponseBody())
                .assertNext(event -> assertEvent(event, "error", "AI 服务暂时不可用，请稍后重试。"))
                .verifyComplete();
    }

    private static void assertEvent(ServerSentEvent<String> event, String name, String data) {
        assertThat(event.event()).isEqualTo(name);
        assertThat(event.data()).isEqualTo(data);
    }
}
