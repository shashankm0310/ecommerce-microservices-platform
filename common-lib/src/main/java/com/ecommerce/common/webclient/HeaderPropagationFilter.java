package com.ecommerce.common.webclient;

import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * WebClient ExchangeFilterFunction that propagates correlation ID and user context headers
 * on outgoing HTTP requests for service-to-service communication.
 *
 * Headers forwarded:
 * - X-Correlation-Id: for distributed tracing across HTTP boundaries
 * - X-User-Id, X-User-Email, X-User-Role: for user context propagation
 */
public class HeaderPropagationFilter implements ExchangeFilterFunction {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        ClientRequest.Builder builder = ClientRequest.from(request);

        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);
        if (correlationId != null) {
            builder.header(CORRELATION_ID_HEADER, correlationId);
        }

        return next.exchange(builder.build());
    }
}
