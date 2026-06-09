package com.ego.raw_ego.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis infrastructure configuration.
 *
 * <p><b>Serialization strategy (LOCKED):</b>
 * Both keys and values are stored as plain UTF-8 strings.
 * Cart item data is serialized to/from JSON manually via {@link ObjectMapper}
 * at the service layer — this keeps Redis values human-readable and debuggable
 * with {@code redis-cli HGETALL cart:42}.
 *
 * <p>The ObjectMapper bean is provided by Spring Boot's auto-configuration
 * (via JacksonConfig). We expose it here only to make the dependency explicit.
 */
@Configuration
public class RedisConfig {

    /**
     * Primary Redis template for cart and lock operations.
     *
     * <ul>
     *   <li>Key serializer:   {@link StringRedisSerializer} — human-readable keys</li>
     *   <li>Value serializer: {@link StringRedisSerializer} — JSON string values</li>
     *   <li>Hash serializers: same (cart items stored as hash fields)</li>
     * </ul>
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
