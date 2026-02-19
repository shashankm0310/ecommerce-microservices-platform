# Interview Guide — E-Commerce Microservices Platform

## Design Patterns Implemented

### 1. Saga Pattern (Choreography & Orchestration)
**What:** Manages distributed transactions across services without 2PC.
**Where:** Order creation (choreography), return/refund (orchestration).
**Why both?** Choreography is simpler and more decoupled for straightforward flows. Orchestration is better when the flow is complex, needs visibility, or requires centralized compensation logic.
**2-min answer:** "We use choreography for order creation because it's a well-understood linear flow where each service knows what to do next. For returns, we switched to orchestration because the compensation logic is complex — if a refund fails mid-way, the orchestrator can coordinate rollback. The choreography saga uses event-driven Kafka topics where each service reacts independently. The orchestration saga uses a central state machine that sends commands and processes replies."

### 2. Strategy Pattern
**What:** Defines a family of algorithms, encapsulates each one, makes them interchangeable.
**Where:** `PaymentStrategy` (CreditCard, Wallet), `NotificationStrategy` (Email, SMS).
**Why?** Adding a new payment method (PayPal, Crypto) only requires a new `@Component` — zero changes to `PaymentService` or the factory (Open/Closed Principle).
**2-min answer:** "We use the Strategy pattern for payment processing. The `PaymentStrategyFactory` auto-discovers all `PaymentStrategy` beans via Spring DI and maps them by name. When `processPayment()` is called with a method like 'CREDIT_CARD', the factory resolves the correct strategy. This means adding PayPal support is just creating a new class — no if/else chains, no service modifications."

### 3. Factory Pattern
**What:** Centralizes object creation logic.
**Where:** `SagaStepFactory` (return saga steps), `PaymentStrategyFactory`, `NotificationStrategyFactory`.
**Why?** Decouples the orchestrator from knowing how to create steps. The factory resolves the right step by name.

### 4. Outbox Pattern
**What:** Ensures events are published atomically with database writes.
**Where:** `OutboxPublisher` in order-service writes events to an outbox table in the same transaction, then a scheduled job publishes them to Kafka.
**Why?** Prevents the dual-write problem — without outbox, the DB could commit but Kafka publish could fail, losing the event.

### 5. CQRS (Command Query Responsibility Segregation)
**What:** Separates read and write models.
**Where:** Notification service maintains an `OrderSagaView` (read model) built from Kafka Streams, separate from the order-service's write model.
**Why?** The notification service needs a denormalized view of order status without querying order-service directly.

---

## Architecture Concepts

### API Gateway
**What/Why:** Single entry point for all clients. Handles JWT validation, routing, rate limiting, correlation ID generation. Prevents clients from knowing about individual service locations.
**Tech:** Spring Cloud Gateway (reactive/WebFlux) with Eureka service discovery.

### Service Discovery (Eureka)
**What/Why:** Services register themselves; the gateway and inter-service calls resolve locations dynamically. No hardcoded URLs.
**Trade-off:** Eureka is AP (available + partition-tolerant) in CAP theorem. In K8s, you might use K8s DNS instead.

### Config Server
**What/Why:** Centralized configuration management. All services pull config from one place, making environment-specific configuration easy (dev/staging/prod profiles).

### Circuit Breaker (Resilience4j)
**What/Why:** Prevents cascading failures. If product-service is down, order-service's circuit breaker opens after N failures, returning fallback responses instead of waiting and timing out.
**Config:** Sliding window of 10 calls, 50% failure threshold, 10s open state.

### Distributed Tracing (OpenTelemetry)
**What/Why:** Traces requests across all services end-to-end. Each request gets a trace ID propagated via HTTP headers and Kafka headers. OTel Collector exports traces to Grafana Tempo via OTLP and metrics to Prometheus. Grafana provides unified visualization.
**Key:** Correlation IDs (MDC) + trace IDs give full observability.

### Centralized Logging (Grafana Loki)
**What/Why:** Aggregates logs from all services into a single queryable store. Promtail scrapes Docker container logs and ships them to Loki. Grafana links logs ↔ traces ↔ metrics for full observability.
**Why Loki over ELK?** Loki only indexes labels (service name, correlationId), not full log content — making it far cheaper and simpler to operate than Elasticsearch. It integrates natively with the existing Grafana stack.
**2-min answer:** "We use the Grafana observability stack: Tempo for traces, Loki for logs, Prometheus for metrics — all visualized in Grafana. Promtail runs as a sidecar that scrapes container logs and extracts correlation IDs via pipeline stages. In Grafana, we can jump from a trace to its corresponding logs, or from a log line to its full distributed trace. This gives us complete end-to-end observability without needing a heavy ELK stack."

### Caching (Redis)
**What/Why:** Distributed cache for frequently-read, rarely-written data (products, user profiles, inventory levels). `@Cacheable`/`@CacheEvict` annotations with JSON serialization.
**Trade-off:** Cache invalidation on writes. TTL-based expiry as safety net.

### Polyglot Persistence
**What/Why:** PostgreSQL for relational data (orders, payments, users), MongoDB for flexible schema (products with variable attributes), Redis for caching.
**2-min answer:** "We chose MongoDB for products because product attributes vary wildly — electronics have specs like RAM and storage, clothing has sizes and colors. A document model handles this naturally without alter-table migrations. PostgreSQL is used for transactional services where ACID guarantees matter."

---

## Key Interview Questions & Answers

### Q: How do you handle distributed transactions?
**A:** We avoid 2PC (two-phase commit) because it's blocking and doesn't scale. Instead, we use the Saga pattern with eventual consistency. For order creation, we use choreography — each service publishes events and the next service reacts. For returns, we use orchestration — a central orchestrator drives the flow. Both approaches ensure data consistency across services through compensation on failure.

### Q: What happens if a service goes down mid-saga?
**A:** For choreography: messages stay in Kafka until the service recovers (Kafka retention). For orchestration: the saga state is persisted in the database with optimistic locking. When the service restarts, it picks up from where it left off. DLQ handles poison messages after 3 retries.

### Q: How do you ensure idempotency?
**A:** Multiple levels: (1) Payment checks if a payment already exists for the orderId before processing. (2) Inventory reservation checks for existing reservations. (3) Kafka consumer group offsets prevent reprocessing. (4) Outbox pattern uses event IDs for deduplication.

### Q: Why Kafka over RabbitMQ?
**A:** Kafka provides: (1) log-based retention — replay events for new consumers, (2) high throughput for event-heavy workloads, (3) Kafka Streams for in-service stream processing (CQRS read model), (4) consumer groups for scaling. RabbitMQ is simpler for point-to-point messaging but Kafka fits our event-driven architecture better.

### Q: How would you deploy this to production?
**A:** The K8s manifests define: namespace isolation, Deployments with readiness/liveness probes, Services for discovery, HPAs for auto-scaling (CPU-based), NGINX Ingress for external traffic. CI/CD via GitHub Actions builds JARs, runs tests, builds Docker images, pushes to GHCR. Infrastructure (Postgres, Kafka, Redis) would use managed services (RDS, MSK, ElastiCache) in production instead of StatefulSets.

### Q: How do you handle configuration across environments?
**A:** Spring Cloud Config Server serves configuration from a central location. Each service pulls its config on startup via `spring.config.import`. Environment-specific profiles (dev, staging, prod) override defaults. Secrets are managed via K8s Secrets, not checked into git.
