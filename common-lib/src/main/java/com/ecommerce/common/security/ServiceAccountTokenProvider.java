package com.ecommerce.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

/**
 * Fetches and caches a Client Credentials access token from Keycloak for service-to-service calls.
 *
 * OAuth2 Client Credentials flow:
 * - The service authenticates itself (not on behalf of a user)
 * - Token is cached until 30s before expiry to minimize token endpoint calls
 * - Used by services that need to call other services without user context
 */
@Component
@Slf4j
public class ServiceAccountTokenProvider {

    private final WebClient tokenClient;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUri;

    private String cachedToken;
    private Instant tokenExpiry = Instant.MIN;

    public ServiceAccountTokenProvider(
            @Value("${keycloak.client-id:ecommerce-service}") String clientId,
            @Value("${keycloak.client-secret:service-account-secret}") String clientSecret,
            @Value("${keycloak.token-uri:http://keycloak:8180/realms/ecommerce/protocol/openid-connect/token}") String tokenUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = tokenUri;
        this.tokenClient = WebClient.builder().build();
    }

    @SuppressWarnings("unchecked")
    public synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }

        try {
            Map<String, Object> response = tokenClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters
                            .fromFormData("grant_type", "client_credentials")
                            .with("client_id", clientId)
                            .with("client_secret", clientSecret))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                cachedToken = (String) response.get("access_token");
                int expiresIn = (Integer) response.get("expires_in");
                tokenExpiry = Instant.now().plusSeconds(expiresIn - 30);
                log.debug("Obtained service account token, expires in {}s", expiresIn);
            }

            return cachedToken;
        } catch (Exception e) {
            log.warn("Failed to obtain service account token: {}", e.getMessage());
            return cachedToken; // Return stale token if available
        }
    }
}
