package com.example.demo.observability.aspect;

import com.example.demo.observability.MinimalAccessLogService;
import com.example.demo.observability.annotation.BizTrace;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BizTrace 注解切面
 *
 * 设计意图：
 * 1) 统一打“最小可观测性”日志，减少 Controller 重复模板代码
 * 2) 保留现有 MinimalAccessLogService，不重造日志底座
 * 3) 让项目里有一个可讲清楚的“自定义注解 + AOP”落地点
 */
@Aspect
@Component
public class BizTraceAspect {

    private final MinimalAccessLogService minimalAccessLogService;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public BizTraceAspect(MinimalAccessLogService minimalAccessLogService) {
        this.minimalAccessLogService = minimalAccessLogService;
    }

    @Around("@annotation(bizTrace)")
    public Object around(ProceedingJoinPoint joinPoint, BizTrace bizTrace) throws Throwable {
        long start = System.currentTimeMillis();
        String traceId = minimalAccessLogService.newTraceId();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        StandardEvaluationContext context = buildContext(method, joinPoint.getArgs());

        String requestId = evalAsString(bizTrace.requestIdSpel(), context);
        if (requestId == null || requestId.isBlank()) {
            requestId = traceId;
        }

        Map<String, Object> bizTags = evalAsMap(bizTrace.bizTagSpel(), context);
        bizTags.put("scene", bizTrace.scene());

        try {
            Object result = joinPoint.proceed();
            minimalAccessLogService.logAccess(
                    traceId,
                    requestId,
                    bizTrace.path(),
                    bizTrace.method(),
                    System.currentTimeMillis() - start,
                    true,
                    bizTags
            );
            return result;
        } catch (Throwable throwable) {
            bizTags.put("error", throwable.getMessage());
            minimalAccessLogService.logAccess(
                    traceId,
                    requestId,
                    bizTrace.path(),
                    bizTrace.method(),
                    System.currentTimeMillis() - start,
                    false,
                    bizTags
            );
            throw throwable;
        }
    }

    private StandardEvaluationContext buildContext(Method method, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        if (paramNames == null) {
            return context;
        }
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
        return context;
    }

    private String evalAsString(String spel, StandardEvaluationContext context) {
        if (spel == null || spel.isBlank()) {
            return null;
        }
        Expression expression = expressionParser.parseExpression(spel);
        Object value = expression.getValue(context);
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> evalAsMap(String spel, StandardEvaluationContext context) {
        if (spel == null || spel.isBlank()) {
            return new LinkedHashMap<>();
        }
        Expression expression = expressionParser.parseExpression(spel);
        Object value = expression.getValue(context);
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> map = new LinkedHashMap<>();
            mapValue.forEach((k, v) -> map.put(String.valueOf(k), v));
            return map;
        }
        return new LinkedHashMap<>();
    }
}
