package com.ego.raw_ego.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Explicit Jackson ObjectMapper configuration.
 *
 * <p>Spring Boot 4 with {@code spring-boot-starter-webmvc} does not automatically
 * expose {@code ObjectMapper} as a Spring bean. Declaring it explicitly here makes
 * it injectable into {@link com.ego.raw_ego.auth.security.JwtAuthenticationEntryPoint}
 * and {@link com.ego.raw_ego.auth.security.JwtAccessDeniedHandler}.
 *
 * <p>Spring Boot 4 ships Jackson 3.x ({@code tools.jackson.*}), which:
 * <ul>
 *   <li>Removed {@code WRITE_DATES_AS_TIMESTAMPS} from {@code SerializationFeature}
 *       — do NOT set it via properties or code</li>
 *   <li>Serializes {@code java.time.Instant} as ISO-8601 strings by default
 *       when {@code JavaTimeModule} is registered</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // JavaTimeModule handles java.time.* types (Instant, LocalDateTime, etc.)
        // Jackson 3.x default: ISO-8601 string output for Instant — no extra config needed
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
