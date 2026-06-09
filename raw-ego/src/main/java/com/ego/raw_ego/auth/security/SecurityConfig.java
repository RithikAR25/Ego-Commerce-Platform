package com.ego.raw_ego.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 7 filter chain configuration.
 *
 * <p>Architecture decisions documented here:
 *
 * <p><b>CSRF</b>: Disabled. Rationale: this API is stateless (no session cookies).
 * JWT in the Authorization header is not susceptible to CSRF because browsers do not
 * automatically attach custom headers on cross-site requests.
 * When the frontend moves to httpOnly cookie-based refresh tokens, CSRF protection
 * MUST be re-enabled (or SameSite=Strict cookies used instead).
 *
 * <p><b>Sessions</b>: STATELESS. No HttpSession is created or used.
 * Spring Security never stores SecurityContext between requests.
 *
 * <p><b>CORS</b>: Configured to allow localhost for development + ego.com production domain.
 * Update {@code allowedOriginPatterns} via environment variable when deploying.
 *
 * <p><b>Security headers</b>: Applied at the Spring Security layer for all API responses:
 * <ul>
 *   <li>X-Frame-Options: DENY — prevents clickjacking</li>
 *   <li>X-Content-Type-Options: nosniff — prevents MIME sniffing</li>
 *   <li>Strict-Transport-Security: max-age=31536000; includeSubDomains — enforces HTTPS</li>
 *   <li>Referrer-Policy: strict-origin-when-cross-origin</li>
 * </ul>
 * Note: Content-Security-Policy is NOT set here. The React SPA requires
 * Vite-generated hashes that must be configured at the Nginx/Vercel layer.
 *
 * <p><b>Route protection</b>:
 * <ul>
 *   <li>Public:    /api/v1/auth/**, Swagger, actuator/health</li>
 *   <li>Admin:     /api/v1/admin/** → requires ROLE_ADMIN</li>
 *   <li>All else:  requires authenticated() (any valid JWT)</li>
 * </ul>
 *
 * <p><b>Method security</b>: Enabled via {@code @EnableMethodSecurity}.
 * Use {@code @PreAuthorize("hasRole('ADMIN')")} on service methods for
 * fine-grained control beyond route matching.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter    jwtAuthenticationFilter;
    private final RateLimitFilter            rateLimitFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler     accessDeniedHandler;
    private final UserDetailsService         userDetailsService;

    /** Endpoints accessible without a JWT token. */
    private static final String[] PUBLIC_MATCHERS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            // Email verification — token is embedded in link; user is not yet authenticated
            "/api/v1/auth/verify-email",
            // Password reset — user cannot authenticate because they've lost their password
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            // ── Catalog — public storefront ───────────────────────────────
            "/api/v1/categories",
            "/api/v1/categories/**",
            "/api/v1/products",
            "/api/v1/products/**",
            // ── Search — public storefront (Elasticsearch-powered) ────────
            // Faceted search and autocomplete require no auth — they are the
            // product listing and search bar used by all visitors.
            // NOTE: /api/v1/admin/search/** is NOT listed here and requires ADMIN.
            "/api/v1/search",
            "/api/v1/search/autocomplete",
            // ── Coupon validation preview (public — no side effects) ────────
            "/api/v1/coupons/validate",
            // ── Payment webhooks — secured by HMAC-SHA256, NOT by JWT ─────
            // Razorpay server POSTs here; it has no JWT. Security is enforced
            // by verifying X-Razorpay-Signature in PaymentService.handleWebhook().
            "/api/v1/webhooks/**",
            // ── Docs / infra ──────────────────────────────────────────────
            "/docs",
            "/docs/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health"
    };


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // ── CSRF ─────────────────────────────────────────────────────────
                .csrf(AbstractHttpConfigurer::disable)

                // ── CORS ─────────────────────────────────────────────────────────
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ── Session ───────────────────────────────────────────────────────
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ── Security response headers ─────────────────────────────────────
                // Applied to all API responses for OWASP baseline compliance.
                // X-Frame-Options:         Prevents clickjacking attacks.
                // X-Content-Type-Options:  Prevents MIME-type sniffing attacks.
                // HSTS:                    Instructs clients to only use HTTPS for 1 year.
                //                         preload is intentionally omitted — requires HSTS
                //                         preload list submission which is irreversible.
                // Referrer-Policy:         Limits referrer info sent on cross-origin requests.
                // Note: Content-Security-Policy must be set at the Nginx/Vercel layer
                //       because the React SPA requires Vite-generated script hashes.
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000L))
                        .referrerPolicy(referrer -> referrer
                                .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                )

                // ── Custom error handlers ─────────────────────────────────────────
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                // ── Route authorization ───────────────────────────────────────────
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_MATCHERS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // CORS preflight
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )

                // ── Authentication provider ───────────────────────────────────────
                .authenticationProvider(authenticationProvider())

                // ── Rate limit filter (runs first — blocks brute force before JWT work) ─
                // Both filters anchor to UsernamePasswordAuthenticationFilter (a registered
                // Spring Security filter). Spring Security 7 requires the reference class to
                // have a known position in its filter order registry. Custom @Component filters
                // like JwtAuthenticationFilter are NOT in that registry.
                // RateLimitFilter is added before JwtAuthenticationFilter: Spring Security
                // preserves insertion order for filters anchored to the same class,
                // so RateLimitFilter → JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter.
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)

                // ── JWT filter (runs before Spring's username/password filter) ─────
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    /**
     * DaoAuthenticationProvider backed by our UserDetailsService and BCrypt encoder.
     * Used internally by Spring Security for credential validation.
     *
     * Spring Security 7: UserDetailsService is now passed via constructor (not setter).
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * BCrypt with strength 12 (~250ms on modern hardware).
     * Strength 10 = ~60ms (acceptable), 12 = production-grade.
     * Never go below 10.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * CORS configuration.
     *
     * <p>In production, replace allowedOriginPatterns with your actual frontend domain(s).
     * The wildcard pattern here is safe because credentialed requests still require
     * an explicit matching origin (no "*" on Access-Control-Allow-Origin with credentials).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",          // local development
                "https://*.ego.com",           // production subdomains
                "https://ego.com",             // apex domain
                "https://*.vercel.app"         // Vercel preview deployments
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Explicit header list — do not use ["*"]; wildcard is overly permissive
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "Idempotency-Key"
        ));
        config.setExposedHeaders(List.of("Authorization", "X-Request-ID"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // Cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // ── FilterRegistrationBean disablers ─────────────────────────────────────
    //
    // Both RateLimitFilter and JwtAuthenticationFilter are @Component beans.
    // Spring Boot auto-configuration detects any @Component that implements Filter
    // and registers it as a plain servlet filter — outside the Security filter chain.
    // This would cause DOUBLE execution: once unordered in the servlet container,
    // and once correctly ordered inside the Security chain via addFilterBefore().
    //
    // Solution: register a FilterRegistrationBean with enabled=false for each.
    // This tells Spring Boot "I know about this filter — don't auto-register it."
    // The filter still runs exactly once, in the correct position, inside the
    // Spring Security filter chain.

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false); // Managed by Spring Security filter chain only
        return bean;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false); // Managed by Spring Security filter chain only
        return bean;
    }
}
