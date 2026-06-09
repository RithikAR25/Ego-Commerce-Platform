package com.ego.raw_ego.common.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Cloudinary SDK bean configuration.
 *
 * <p>Credentials are injected from environment variables via {@code application.properties}.
 * Never hardcoded here. Docker-compatible via the compose {@code environment:} block.
 *
 * <p>The single {@link Cloudinary} bean is thread-safe and shared across the application.
 * CloudinaryService injects it via constructor injection.
 *
 * <p>Env vars required:
 * <ul>
 *   <li>{@code CLOUDINARY_CLOUD_NAME} — e.g. {@code dvoggc3xp}</li>
 *   <li>{@code CLOUDINARY_API_KEY}    — numeric key</li>
 *   <li>{@code CLOUDINARY_API_SECRET} — secret string</li>
 * </ul>
 */
@Configuration
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    /**
     * Constructs the Cloudinary SDK client from injected credentials.
     *
     * <p>{@code secure = true} ensures all generated URLs use HTTPS.
     */
    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(Map.of(
                "cloud_name", cloudName,
                "api_key",    apiKey,
                "api_secret", apiSecret,
                "secure",     true
        ));
    }
}
