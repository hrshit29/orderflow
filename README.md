# OrderFlow — Event-Driven Microservices (Phase 1)

Spring Boot 3.4 · Java 21 · Kafka (KRaft) · MySQL 8 · Docker Compose · Springdoc OpenAPI

```
client ──► user-service (8081) ──► user-registered ─────────┐
       ──► order-service (8082) ──► order-created ──────────┤
                    ▲               order-cancelled ────────┤──► notification-service (8085) ──► SMTP (Mailpit)
                    │                                       │         │ retries 1s/2s/4s
     stock-reserved / stock-insufficient / payment-*        │         └──► notification-dlq
     (Phase 2: inventory + payment services)                │
```

## Modules

| Module | What it does |
|---|---|
| `common-events` | Event records, topic names, shared `PageResponse` — the contract between services |
| `user-service` | Register / login / get / update / delete / paginated search. BCrypt passwords. Publishes `user-registered` |
| `order-service` | Create / get / cancel / paginated list filtered by user, status, date range. Publishes `order-created`, `order-cancelled`. Consumes `stock-*` and `payment-*` (ready for Phase 2) |
| `notification-service` | Consumes events, sends email, logs every notification to `notification_log`, `GET /api/v1/notifications` history |

## Design decisions worth talking about in interviews

- **Events published after commit** — `@TransactionalEventListener(AFTER_COMMIT)`: a rolled-back DB write never emits an event. Next step: transactional outbox for guaranteed delivery.
- **Events keyed by entity id** — all events for one order land on one partition, so they're consumed in order.
- **Idempotent consumers** — Kafka is at-least-once. Order state transitions check current status; notifications use a unique `eventKey` (`order-created:42`) so a redelivered event never sends a second email.
- **Optimistic locking** (`@Version`) on `Order` — a user cancel and a Kafka consumer can't silently overwrite each other.
- **Retry + DLQ** — `DefaultErrorHandler` retries with exponential backoff (1s, 2s, 4s), then `DeadLetterRecoverer` writes a `FAILED` row and publishes the record (with exception headers) to `notification-dlq`. Poison messages skip retries.
- **Event-carried state transfer** — order events carry only `userId`. Notification Service keeps its own `user_contacts` table built from `user-registered`, instead of calling User Service synchronously.
- **Database per service** — `user_db`, `order_db`, `notification_db`.
- **Explicit `PageResponse`** instead of serialising Spring's `Page` — a stable API contract; `max-page-size: 100` caps abuse.
- **Kubernetes-ready probes** — `/actuator/health/liveness` and `/readiness` are enabled.

## Run it

**Option A — everything in Docker**
```bash
docker compose up -d --build
```

**Option B — infra in Docker, services from IntelliJ (best for development)**
```bash
docker compose up -d mysql kafka kafka-ui mailpit
mvn clean install
# then run UserServiceApplication, OrderServiceApplication, NotificationServiceApplication
# (add NOTIFICATION_CHANNEL=email to notification-service to send real mail to Mailpit)
```

| URL | |
|---|---|
| http://localhost:8081/swagger-ui.html | User Service API |
| http://localhost:8082/swagger-ui.html | Order Service API |
| http://localhost:8085/swagger-ui.html | Notification Service API |
| http://localhost:8090 | Kafka UI — topics, messages, consumer groups, DLQ |
| http://localhost:8025 | Mailpit — emails that were "sent" |

## Try the flow

```bash
# 1. Register -> welcome email appears in Mailpit
curl -s -X POST localhost:8081/api/v1/users/register -H 'Content-Type: application/json' \
  -d '{"email":"saurabh@example.com","fullName":"Saurabh","phone":"9876543210","password":"password123"}'

# 2. Place an order -> "Order #1 received" email
curl -s -X POST localhost:8082/api/v1/orders -H 'Content-Type: application/json' \
  -d '{"userId":1,"items":[{"productId":101,"quantity":2,"unitPrice":499.00},{"productId":205,"quantity":1,"unitPrice":1299.00}]}'

# 3. Paginated + filtered list
curl -s 'localhost:8082/api/v1/orders?userId=1&status=PENDING&page=0&size=10&sort=createdAt,desc'

# 4. Cancel -> order-cancelled event -> cancellation email
curl -s -X POST localhost:8082/api/v1/orders/1/cancel -H 'Content-Type: application/json' -d '{"reason":"Changed my mind"}'

# 5. Notification history
curl -s 'localhost:8085/api/v1/notifications?userId=1'
```

**Simulate the Phase-2 Inventory Service by hand:** in Kafka UI → Topics → `stock-reserved` → Produce Message, set key `2`, header `{"__TypeId__":"com.orderflow.events.StockReservedEvent"}`, value `{"orderId":2,"userId":1,"totalAmount":10,"occurredAt":"2026-10-09T10:00:00Z"}`. Order 2 moves `PENDING → CONFIRMED`.

### See the DLQ work
```bash
docker compose stop mailpit                       # SMTP is now down
curl -s -X POST localhost:8082/api/v1/orders -H 'Content-Type: application/json' \
  -d '{"userId":1,"items":[{"productId":7,"quantity":1,"unitPrice":10}]}'
docker compose logs -f notification-service       # 3 retries over ~7s, then "Giving up ... sending to notification-dlq"
curl -s 'localhost:8085/api/v1/notifications?status=FAILED'
# Kafka UI -> Topics -> notification-dlq: the original event + kafka_dlt-exception-* headers
docker compose start mailpit
```

## Tests
```bash
mvn test
```
Unit tests cover: password hashing + event publishing, duplicate email, order total calculation, illegal cancel, idempotent cancel on redelivered events, duplicate-notification suppression, retry propagation, unknown-user skip.

## Roadmap

- [x] **Phase 1** — User, Order, Notification services · Kafka · retry/DLQ · pagination · Swagger · Docker Compose
- [ ] **Phase 2** — Inventory + Payment services (full saga: `order-created → stock-reserved → payment-success/failed`), compensation (restock on cancel)
- [ ] **Phase 3** — Spring Cloud Gateway + JWT auth, Eureka, aggregated Swagger
- [ ] **Phase 4** — Kubernetes manifests (Deployments, Services, ConfigMaps/Secrets, Ingress, HPA, probes), then Helm
- [ ] **Phase 5** — Flyway migrations, Testcontainers integration tests, transactional outbox, Prometheus + Grafana, keyset pagination
