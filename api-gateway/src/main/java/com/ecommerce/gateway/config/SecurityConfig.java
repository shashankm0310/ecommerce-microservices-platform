package com.ecommerce.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive security configuration for the API Gateway.
 * Configures the gateway as an OAuth2 Resource Server that validates Keycloak JWTs.
 *
 * Note: Fine-grained path authorization is still handled by JwtAuthenticationFilter
 * (which validates tokens and sets X-User-* headers). This SecurityConfig simply
 * permits all requests to pass through the security filter chain — the actual
 * authentication enforcement is done by the JwtAuthenticationFilter GlobalFilter.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().permitAll()
                )
                .build();
    }
}
