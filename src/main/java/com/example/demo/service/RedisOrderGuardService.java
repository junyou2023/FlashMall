package com.example.demo.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 订单守门服务
 *
 * 这个类统一管理两类 Redis 状态：
 *
 * 1. 一人一单状态
 *    例如：
 *    - PENDING：订单正在异步处理中
 *    - SUCCESS：订单已成功创建
 *
 * 2. requestId 幂等状态
 *    例如：
 *    - PROCESSING：这次请求已经进入处理流程
 *    - DONE：这次请求对应的订单已经成功处理完成
 *
 * 你可以把它理解成：
 * 它是“下单链路的 Redis 状态中枢”。
 */
@Service
public class RedisOrderGuardService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * requestId 幂等状态的过期时间
     */
    private static final long IDEMPOTENT_TTL_MINUTES = 10;

    /**
     * PENDING 状态的过期时间
     */
    private static final long PENDING_TTL_MINUTES = 15;

    public RedisOrderGuardService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 构造“一人一单状态”的 Redis key
     *
     * 例子：
     * order:once:1001:2001
     */
    public String buildUserOrderOnceKey(Long userId, Long productId) {
        return "order:once:" + userId + ":" + productId;
    }

    /**
     * 构造 requestId 幂等状态的 Redis key
     *
     * 例子：
     * order:idempotent:req-30001
     */
    public String buildIdempotentKey(String requestId) {
        return "order:idempotent:" + requestId;
    }

    /**
     * 查询某个用户对某个商品当前的订单状态
     *
     * 可能返回：
     * - null
     * - PENDING
     * - SUCCESS
     */
    public String getOrderStatus(Long userId, Long productId) {
        return stringRedisTemplate.opsForValue().get(buildUserOrderOnceKey(userId, productId));
    }

    /**
     * 【本轮新增的核心方法】
     * 查询 requestId 当前的幂等状态
     *
     * 为什么这轮要新增它？
     * 因为进入 Manual ACK 后，
     * 消费端也需要判断：
     * “这条消息是不是其实已经成功处理过了，只是 ACK 前宕机导致又被投递回来？”
     *
     * 可能返回：
     * - null
     * - PROCESSING
     * - DONE
     *
     * 其中最关键的是：
     * 如果返回 DONE，
     * 消费端就可以做“消费幂等短路”，避免重复创建订单。
     */
    public String getIdempotentStatus(String requestId) {
        return stringRedisTemplate.opsForValue().get(buildIdempotentKey(requestId));
    }

    /**
     * 尝试把 requestId 标记为 PROCESSING
     *
     * 只有第一次成功，
     * 重复请求会失败，从而达到请求幂等效果。
     */
    public boolean tryMarkIdempotent(String requestId) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                buildIdempotentKey(requestId),
                "PROCESSING",
                Duration.ofMinutes(IDEMPOTENT_TTL_MINUTES)
        );
        return Boolean.TRUE.equals(success);
    }

    /**
     * 把 requestId 标记为 DONE
     *
     * 含义：
     * 这次请求对应的业务已经真正处理完成。
     *
     * 这个状态后面会被消费端拿来做“幂等短路”。
     */
    public void markIdempotentDone(String requestId) {
        stringRedisTemplate.opsForValue().set(
                buildIdempotentKey(requestId),
                "DONE",
                Duration.ofMinutes(IDEMPOTENT_TTL_MINUTES)
        );
    }

    /**
     * 清理 requestId 幂等状态
     *
     * 一般在失败补偿时调用，
     * 避免一次失败请求一直占着幂等标记。
     */
    public void clearIdempotent(String requestId) {
        stringRedisTemplate.delete(buildIdempotentKey(requestId));
    }

    /**
     * 把订单状态标记为 PENDING
     *
     * 含义：
     * Redis 预扣成功了，
     * 订单已经进入异步处理链路，
     * 但最终结果还没有落定。
     */
    public void markPending(Long userId, Long productId) {
        stringRedisTemplate.opsForValue().set(
                buildUserOrderOnceKey(userId, productId),
                "PENDING",
                Duration.ofMinutes(PENDING_TTL_MINUTES)
        );
    }

    /**
     * 把订单状态标记为 SUCCESS
     *
     * 含义：
     * 订单已经真正创建成功，
     * 后续一人一单检查会据此拦截重复购买。
     */
    public void markSuccess(Long userId, Long productId) {
        stringRedisTemplate.opsForValue().set(
                buildUserOrderOnceKey(userId, productId),
                "SUCCESS"
        );
    }

    /**
     * 清理订单状态
     *
     * 一般在失败补偿时调用：
     * 例如 MQ 发送失败、消费失败回滚等场景。
     */
    public void clearOrderStatus(Long userId, Long productId) {
        stringRedisTemplate.delete(buildUserOrderOnceKey(userId, productId));
    }
}