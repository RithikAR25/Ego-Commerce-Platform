package com.ego.raw_ego;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EGO E-Commerce Platform — Spring Boot entry point.
 *
 * <p>{@code @EnableAsync} activates Spring's async task executor, enabling
 * {@code @Async} annotation support. Used by the notification event listeners
 * (Phase 8) to dispatch emails on a dedicated thread pool without blocking
 * the order or payment transaction threads.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class RawEgoApplication {

	public static void main(String[] args) {
		SpringApplication.run(RawEgoApplication.class, args);
	}

}
