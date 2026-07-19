package com.flashmall.ai.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

/**
 * HTTP 请求进入业务链路前的统一异常出口。
 *
 * 参数和报文错误在这里转成稳定的 4xx 响应；模型流启动后的异常由 Controller 转成 SSE error 事件，
 * 因为响应开始后已经不能再可靠地切换 HTTP 状态码。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleValidationException(WebExchangeBindException exception) {
        String detail = exception.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "请求参数不合法" : error.getDefaultMessage())
                .orElse("请求参数不合法");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("请求参数校验失败");
        return problem;
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ProblemDetail handleWebInputException(ServerWebInputException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "请求体格式不正确");
        problem.setTitle("请求解析失败");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception exception) {
        log.error("处理 AI HTTP 请求时发生未预期异常", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "服务暂时不可用，请稍后重试"
        );
        problem.setTitle("服务内部错误");
        return problem;
    }
}
