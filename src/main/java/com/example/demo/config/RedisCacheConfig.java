package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisCacheConfig {

    /**
     * 业务缓存通过统一的 JSON 序列化模板读写 Redis，避免不同调用方生成不兼容的数据格式。
     * 这里连接 ProductService 等业务服务与底层连接工厂，不改变缓存一致性策略。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 【新增】缓存链路第 5 环：明确缓存“工程规则”
     *
     * 你可以把它理解为：
     * 这不是在“操作 Redis”，
     * 而是在“告诉 Spring Cache 用 Redis 时应遵守什么默认规则”。
     */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {

        return RedisCacheConfiguration.defaultCacheConfig()

                // 【新增】Key 序列化：用纯字符串，方便你用 redis-cli 直接读懂 key
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()
                        )
                )

                // 【新增】Value 序列化：用 JSON
                // 好处：可读性强、跨语言友好
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                )

                // 【新增】默认 TTL：30 分钟
                // 这能让你建立“缓存不是永久真相，只是短期加速层”的第一性认知
                .entryTtl(Duration.ofMinutes(30))

                // 【新增】避免缓存 null
                // 防止“缓存穿透”演示阶段的误解
                .disableCachingNullValues();
    }
}

