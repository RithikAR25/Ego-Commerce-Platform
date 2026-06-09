package com.ego.raw_ego.payment.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Constructs the singleton {@link RazorpayClient} bean used by {@link com.ego.raw_ego.payment.service.PaymentService}.
 *
 * <p>Keys are injected from environment variables ({@code RAZORPAY_KEY_ID} and
 * {@code RAZORPAY_KEY_SECRET}). Never hardcode credentials here.
 *
 * <p>The client is thread-safe and should be shared as a singleton.
 */
@Configuration
@Slf4j
public class RazorpayConfig {

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    /**
     * Creates a configured {@link RazorpayClient} ready to call the Razorpay API.
     *
     * @throws IllegalStateException if the SDK fails to initialize (bad credentials, network error)
     */
    @Bean
    public RazorpayClient razorpayClient() {
        try {
            log.info("Initializing Razorpay client for key-id={}", keyId);
            return new RazorpayClient(keyId, keySecret);
        } catch (RazorpayException e) {
            throw new IllegalStateException("Failed to initialize Razorpay client: " + e.getMessage(), e);
        }
    }
}
