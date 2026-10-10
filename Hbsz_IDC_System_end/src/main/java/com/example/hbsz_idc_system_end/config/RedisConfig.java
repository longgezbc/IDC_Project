package com.example.hbsz_idc_system_end.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 缓存配置类
 * <p>
 * 统一定制 Redis 序列化策略与 Spring Cache 缓存管理器，保证缓存数据可读、可还原类型，
 * 并为注解式缓存（{@code @Cacheable} 等）提供默认过期时间与 Key 前缀。
 * 通过 {@code @EnableCaching} 开启 Spring 缓存注解支持。
 *
 * @author hbsz
 */
@EnableCaching
@Configuration
public class RedisConfig {

    /**
     * 自定义 RedisTemplate。
     * <p>
     * Key/HashKey 使用 {@link StringRedisSerializer} 保证可读性；
     * Value/HashValue 使用 {@link GenericJackson2JsonRedisSerializer}，
     * 序列化时写入类型信息，反序列化时可还原为原始对象类型。
     *
     * @param connectionFactory Redis 连接工厂，由 Spring Boot 自动装配
     * @return 配置完成的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key 使用 String 序列化
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 使用 Jackson JSON 序列化（带类型信息，反序列化时可还原为原对象）
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Spring Cache 缓存管理器配置。
     * <p>
     * 默认 TTL 30 分钟；Key 统一添加 "hbsz:" 前缀；不缓存 null 值以避免缓存穿透占位；
     * Key 用 String 序列化、Value 用 JSON 序列化；开启事务感知（transactionAware），
     * 使缓存写入与当前事务保持一致（事务回滚时不生效）。
     *
     * @param connectionFactory Redis 连接工厂，由 Spring Boot 自动装配
     * @return Redis 缓存管理器
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // 默认过期时间 30 分钟
                .entryTtl(Duration.ofMinutes(30))
                // 设置缓存前缀
                .prefixCacheNameWith("hbsz:")
                // 不缓存 null 值
                .disableCachingNullValues()
                // Key 使用 String 序列化
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                // Value 使用 JSON 序列化
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        // 性能优化 #29：为工作台 dashboardSummary 缓存单独配置 60 秒 TTL，
        // 在缓存命中与数据新鲜度之间取得平衡（默认 TTL 30 分钟过长，工作台数据需较实时）
        // 重构：为不同缓存空间配置分层 TTL，既匹配数据变更频率，又天然错峰过期降低缓存雪崩风险。
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        // 工作台概览：数据需较实时，60 秒
        cacheConfigurations.put("dashboardSummary", config.entryTtl(Duration.ofSeconds(60)));
        // 字典数据：变化少，1 小时
        cacheConfigurations.put("dictData", config.entryTtl(Duration.ofHours(1)));
        // 权限树：变更少但比字典频繁，10 分钟
        cacheConfigurations.put("permissionTree", config.entryTtl(Duration.ofMinutes(10)));
        // 用户信息：15 分钟
        cacheConfigurations.put("userProfile", config.entryTtl(Duration.ofMinutes(15)));
        // 注：systemConfig 沿用默认 30 分钟 TTL，无需单独配置

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withInitialCacheConfigurations(cacheConfigurations)
                // 事务感知
                .transactionAware()
                .build();
    }

}
