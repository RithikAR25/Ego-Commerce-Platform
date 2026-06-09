package com.ego.raw_ego.notification.config;

import com.sendgrid.SendGrid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the SendGrid Java client.
 *
 * <p>Exposes a single {@link SendGrid} bean scoped to the application context.
 * The API key is resolved from the {@code SENDGRID_API_KEY} environment variable
 * (via {@code sendgrid.api-key} in {@code application.properties}).
 *
 * <p>If the key is the placeholder value ({@code SG.placeholder}), SendGrid
 * API calls will fail with a 401. The {@code NotificationService} handles these
 * errors gracefully — they are logged as {@code FAILED} rows in
 * {@code notification_logs} and never propagate to the caller.
 */
@Configuration
@Slf4j
public class SendGridConfig {

    @Value("${sendgrid.api-key}")
    private String apiKey;

    @Bean
    public SendGrid sendGrid() {
        if (apiKey.startsWith("SG.placeholder")) {
            log.warn("[SendGrid] API key is placeholder — emails will not be delivered. " +
                     "Set SENDGRID_API_KEY in .env to enable real email sending.");
        } else {
            log.info("[SendGrid] Client initialized with real API key.");
        }
        return new SendGrid(apiKey);
    }
}
