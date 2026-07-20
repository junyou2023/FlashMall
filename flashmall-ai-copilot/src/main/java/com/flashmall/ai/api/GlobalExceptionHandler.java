/*
 * 整体定位：这是 HTTP 响应尚未提交时的统一异常翻译器，把 Java 异常转换为稳定、可机器读取的错误契约。
 * 第一性原因：异常是内部控制流，HTTP 状态与 ProblemDetail 是外部协议；二者必须在边界层显式转换。
 */
package com.flashmall.ai.api; // 与 Controller 同属 api 边界，避免 application 层依赖 HTTP 状态码。

import org.slf4j.Logger; // 使用日志门面记录服务端诊断信息，对客户端只返回安全摘要。
import org.slf4j.LoggerFactory; // 为当前异常处理器创建带类名的日志器。
import org.springframework.http.HttpStatus; // 使用标准 400/500 枚举，统一 HTTP 语义。
import org.springframework.http.ProblemDetail; // RFC 7807 风格错误体，固定包含 status、title、detail。
import org.springframework.web.bind.support.WebExchangeBindException; // WebFlux 在 @Valid 校验请求体失败时抛出的绑定异常。
import org.springframework.web.bind.annotation.ExceptionHandler; // 声明“某种异常由哪个方法翻译”。
import org.springframework.web.bind.annotation.RestControllerAdvice; // 让异常处理规则对所有 REST Controller 生效并自动序列化返回值。
import org.springframework.web.server.ServerWebInputException; // JSON 缺失、类型错误或无法解码时的 WebFlux 输入异常。

/**
 * HTTP 请求进入业务链路前的统一异常出口。
 *
 * 参数和报文错误在这里转成 4xx；未知同步错误转成 500；真实堆栈只进入服务端日志。
 * 模型 Flux 启动后的异步异常由 Controller 转成 SSE error，因为响应头发出后不能再改 HTTP 状态。
 */
@RestControllerAdvice // 组件扫描创建全局 Advice，并把返回的 ProblemDetail 写为 JSON。
public class GlobalExceptionHandler { // 不保存请求状态，因此一个实例即可安全处理所有请求异常。

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class); // 仅未知异常需要服务端堆栈，校验错误属于正常客户端错误。

    @ExceptionHandler(WebExchangeBindException.class) // 精确匹配 @Valid 失败，优先于下面的 Exception 总兜底。
    public ProblemDetail handleValidationException(WebExchangeBindException exception) { // 输入异常对象包含所有字段错误及对应提示。
        String detail = exception.getFieldErrors().stream() // 多字段可能同时失败；先转为流，便于选取一个稳定、简洁的首个错误。
                .findFirst() // API 当前只有 message，首个错误足够指导客户端修正请求。
                .map(error -> error.getDefaultMessage() == null ? "请求参数不合法" : error.getDefaultMessage()) // 有注解消息就复用，否则提供兜底文案。
                .orElse("请求参数不合法"); // 理论上绑定异常应含字段错误；该兜底防止异常结构变化导致空响应。

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail); // 400 表示请求本身可修正，detail 给出具体原因。
        problem.setTitle("请求参数校验失败"); // title 是稳定错误类别，客户端展示或监控聚合都可使用。
        return problem; // WebFlux 根据 ProblemDetail.status 设置 HTTP 状态，并把对象序列化为 JSON。
    }

    @ExceptionHandler(ServerWebInputException.class) // 捕获 JSON 语法错误、字段类型不匹配等“尚未形成 DTO”的问题。
    public ProblemDetail handleWebInputException(ServerWebInputException exception) { // 参数保留完整技术原因，但不原样返回以免暴露内部类型。
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "请求体格式不正确"); // 仍是客户端可修正的 400，而非服务器 500。
        problem.setTitle("请求解析失败"); // 与“DTO 已生成但校验失败”区分，便于定位错误发生层级。
        return problem; // 结束同步异常链路，Controller 方法不会被调用。
    }

    @ExceptionHandler(Exception.class) // 最后兜住未预料的同步异常，防止框架默认错误页和内部细节外泄。
    public ProblemDetail handleUnexpectedException(Exception exception) { // 只处理响应尚未提交的异常，异步模型错误不走这里。
        log.error("处理 AI HTTP 请求时发生未预期异常", exception); // 服务端保留完整堆栈用于排障，但日志中不主动打印 API Key。
        ProblemDetail problem = ProblemDetail.forStatusAndDetail( // 创建标准错误体，而不是随意 Map，保证接口长期稳定。
                HttpStatus.INTERNAL_SERVER_ERROR, // 500 表示客户端请求未必有错，需要服务端或依赖恢复。
                "服务暂时不可用，请稍后重试" // 对用户只给可行动建议，不暴露类名、地址或供应商响应。
        ); // 参数分行只是提升可读性；至此 ProblemDetail 已同时拥有 500 状态和安全 detail。
        problem.setTitle("服务内部错误"); // 用稳定标题归类未知服务端异常。
        return problem; // WebFlux 写出 500 JSON 响应，终止本次同步请求链路。
    }
}
