package com.ego.raw_ego.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc / OpenAPI 3 configuration.
 *
 * Declares the global "bearerAuth" security scheme so Swagger UI renders
 * the "Authorize" padlock button and all @SecurityRequirement endpoints
 * accept Bearer tokens in the UI.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title       = "EGO E-Commerce API",
                version     = "1.0",
                description = "Production-grade clothing e-commerce platform — inspired by Myntra, Zara, Bonkers Corner.",
                contact     = @Contact(name = "EGO Engineering", email = "tech@ego.com")
        )
)
@SecurityScheme(
        name         = "bearerAuth",
        type         = SecuritySchemeType.HTTP,
        scheme       = "bearer",
        bearerFormat = "JWT",
        description  = "Paste your access token here. Prefix 'Bearer ' is added automatically."
)
public class OpenApiConfig {
    // Bean-less — all configuration is driven by annotations.
}
