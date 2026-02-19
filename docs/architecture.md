# Architecture Decision Records

## ADR-001: Microservices Architecture

**Status:** Accepted

**Context:** We need a scalable, maintainable e-commerce platform that allows independent deployment and scaling of business domains.

**Decision:** Decompose the system into 10 Spring Boot microservices organized by business domain (User, Product, Order, Inventory, Payment, Notification) plus infrastructure services (Config Server, Service Registry, API Gateway, Common Library).

**Consequences:**
- (+) Independent deployment and scaling per service
- (+) Technology diversity (PostgreSQL, MongoDB, Redis per service need)
- (-) Increased operational complexity (distributed transactions, network calls)
- (-) Data consistency requires eventual consistency patterns (sagas)

---

## ADR-002: Choreography vs Orchestration Sagas

**Status:** Accepted

**Context:** Distributed transactions across Order, Inventory, Payment, and Notification services require coordination.

**Decision:** Implement **both** saga patterns for educational contrast:
- **Choreography** (order creation): each service listens to events and reacts independently. Order → Inventory → Payment → Notification via Kafka topics.
- **Orchestration** (return/refund): a central `ReturnSagaOrchestrator` in order-service drives the flow step-by-step via command/reply topics.

**Trade-offs:**
| Aspect | Choreography | Orchestration |
|--------|-------------|---------------|
| Coupling | Low (event-driven) | Medium (orchestrator knows steps) |
| Visibility | Hard to trace full flow | Easy — single state machine |
| Complexity | Grows with participants | Centralized in orchestrator |
| Failure handling | Each service compensates itself | Orchestrator drives compensation |

---

## ADR-003: Polyglot Persistence

**Status:** Accepted

**Context:** Different services have different data access patterns.

**Decision:**
- **PostgreSQL** for User, Order, Inventory, Payment, Notification (relational, ACID transactions)
- **MongoDB** for Product (flexible schema for product attributes, text search)
- **Redis** for distributed caching (product, user, inventory reads)

**Rationale:** Product data has variable attributes (electronics vs clothing), making document storage natural. Transactional services need ACID guarantees.

---

## ADR-004: API Gateway Pattern

**Status:** Accepted

**Context:** Clients need a single entry point with cross-cutting concerns (auth, routing, rate limiting).

**Decision:** Spring Cloud Gateway (reactive/WebFlux) as the single entry point. Gateway handles:
- JWT validation (Keycloak)
- Route-based load balancing via Eureka
- Correlation ID generation
- Circuit breaking

---

## ADR-005: OpenTelemetry for Distributed Tracing

**Status:** Accepted (migrated from Brave/Zipkin)

**Context:** Need end-to-end request tracing across all services.

**Decision:** Migrate from Brave+Zipkin to OpenTelemetry via Micrometer bridge with Grafana Tempo as the trace backend. Services export OTLP to an OTel Collector, which fans out to Tempo (traces via OTLP) and Prometheus (metrics).

**Rationale:** OpenTelemetry is the industry standard, vendor-neutral, and supports traces + metrics + logs in a unified SDK. Grafana Tempo is a cost-effective, scalable trace backend that integrates natively with Grafana for visualization and uses object storage (no indexing required). The OTel Collector provides a pipeline for processing/routing telemetry data.

---

## ADR-006: Event-Driven Communication via Kafka

**Status:** Accepted

**Context:** Services need asynchronous, resilient communication for saga flows and event notification.

**Decision:** Apache Kafka as the event backbone with:
- Topic-per-aggregate pattern (`order-events`, `inventory-events`, `payment-events`)
- Outbox pattern for transactional event publishing
- Dead Letter Queue (DLQ) with exponential backoff for failed messages
- Kafka Streams in notification-service for CQRS read model

---

## ADR-007: Strategy Pattern for Extensibility

**Status:** Accepted

**Context:** Payment methods and notification channels need to be extensible without modifying core logic.

**Decision:**
- `PaymentStrategy` interface with `CreditCardPaymentStrategy`, `WalletPaymentStrategy` — resolved via `PaymentStrategyFactory`
- `NotificationStrategy` interface with `EmailNotificationStrategy`, `SmsNotificationStrategy` — resolved via `NotificationStrategyFactory`

Adding a new payment method or notification channel only requires a new `@Component` — no factory or service changes needed (Open/Closed Principle).
