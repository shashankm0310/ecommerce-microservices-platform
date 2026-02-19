# Saga Flow Documentation

## 1. Order Creation — Choreography Saga

Each service independently reacts to events, creating a decentralized workflow.

```
┌──────────┐    order-events     ┌─────────────────┐    inventory-events    ┌─────────────────┐
│  Client   │───────────────────>│  Order Service   │──────────────────────>│Inventory Service │
│           │    POST /orders    │                  │   ORDER_CREATED       │                  │
└──────────┘                     └──────────────────┘                       └──────┬──────────┘
                                                                                   │
                                        ┌──────────────────────────────────────────┘
                                        │ INVENTORY_RESERVED / INVENTORY_RESERVATION_FAILED
                                        ▼
                                 ┌─────────────────┐    payment-events     ┌─────────────────┐
                                 │ Payment Service  │─────────────────────>│  Order Service   │
                                 │                  │  PAYMENT_COMPLETED   │ (updates status) │
                                 └─────────────────┘  / PAYMENT_FAILED    └──────┬──────────┘
                                                                                  │
                                        ┌─────────────────────────────────────────┘
                                        │ notification-events
                                        ▼
                                 ┌─────────────────────┐
                                 │Notification Service  │
                                 │ (sends notification) │
                                 └─────────────────────┘
```

### Happy Path:
1. `Order Service` creates order (PENDING) → publishes `ORDER_CREATED` to `order-events`
2. `Inventory Service` reserves stock → publishes `INVENTORY_RESERVED` to `inventory-events`
3. `Payment Service` processes payment → publishes `PAYMENT_COMPLETED` to `payment-events`
4. `Order Service` updates order to CONFIRMED → publishes notification event
5. `Notification Service` sends confirmation notification

### Compensation (Payment Failed):
1. `Payment Service` publishes `PAYMENT_FAILED`
2. `Order Service` sets status to CANCELLED
3. `Inventory Service` releases reserved stock

---

## 2. Order Return — Orchestration Saga

The `ReturnSagaOrchestrator` centrally controls the flow via command/reply topics.

```
┌──────────┐   POST /orders/{id}/returns   ┌─────────────────────────┐
│  Client   │─────────────────────────────>│  ReturnSagaOrchestrator  │
└──────────┘                               │  (Order Service)         │
                                           └──────────┬──────────────┘
                                                      │
                          ┌───────────────────────────┼───────────────────────────┐
                          │                           │                           │
                Step 1: INITIATE_REFUND    Step 2: RESTORE_INVENTORY   Step 3: SEND_NOTIFICATION
                          │                           │                           │
                          ▼                           ▼                           ▼
                 ┌─────────────────┐       ┌─────────────────┐       ┌─────────────────────┐
                 │ Payment Service  │       │Inventory Service │       │Notification Service  │
                 │ (refunds money)  │       │ (restores stock) │       │ (sends notification) │
                 └────────┬────────┘       └────────┬────────┘       └──────────┬──────────┘
                          │                          │                           │
                   REFUND_COMPLETED          INVENTORY_RESTORED          NOTIFICATION_SENT
                          │                          │                           │
                          └──────────────────────────┴───────────────────────────┘
                                                     │
                                                     ▼
                                           ┌─────────────────────────┐
                                           │  Saga Status: COMPLETED  │
                                           └─────────────────────────┘
```

### State Machine:
```
INITIATED → REFUND_PENDING → REFUND_COMPLETED → INVENTORY_RESTORED → COMPLETED
                  ↓
               FAILED (compensation: no-op since refund already failed)
```

### Kafka Topics:
- `return-saga-commands` — orchestrator → participant services (commands)
- `return-saga-replies` — participant services → orchestrator (replies)

### Step Details:
| Step | Service | Command | Success Reply | Failure Reply |
|------|---------|---------|--------------|---------------|
| 1 | Payment | INITIATE_REFUND | REFUND_COMPLETED | REFUND_FAILED |
| 2 | Inventory | RESTORE_INVENTORY | INVENTORY_RESTORED | INVENTORY_RESTORE_FAILED |
| 3 | Notification | SEND_NOTIFICATION | NOTIFICATION_SENT | (non-critical, always succeeds) |
