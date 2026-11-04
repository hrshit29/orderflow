# OrderFlow — Event-Driven Microservices (Phases 1–3)

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
| `api-gateway` | Port 8080, the single entry point. Verifies JWTs, enforces roles (admin-only routes), strips spoofed `X-User-*` headers and forwards the caller's id/role, routes to each service, serves one Swagger UI for all services |
| `common-events` | Event records, topic names, shared `PageResponse` — the contract between services |
| `user-service` | Register / login (issues a JWT: `sub`=user id, `role`=CUSTOMER/ADMIN, 1 h) / `GET /me` / get / update / delete / paginated search. BCrypt passwords. Publishes `user-registered` |
| `order-service` | Create / get / cancel / paginated list filtered by user, status, date range. Publishes `order-created`, `order-cancelled`. Consumes `stock-*` and `payment-*` (ready for Phase 2) |
| `inventory-service` | Products + stock. Consumes `order-created` → reserves every line or nothing (row lock, `SELECT … FOR UPDATE`) → publishes `stock-reserved` / `stock-insufficient`. Consumes `order-cancelled` → puts reserved stock back. Seeds demo products 7, 101, 205 |
| `payment-service` | Consumes `stock-reserved` → charges a simulated gateway (declines above Rs. 10,000; outage switch for demos) → publishes `payment-success` / `payment-failed`. Consumes `order-cancelled` → refunds a charged order, or never charges one cancelled first |
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
- **All-or-nothing stock reservation** — Inventory locks every product row in the order (`PESSIMISTIC_WRITE`, in id order to avoid deadlocks), checks all lines, then reserves. Two orders racing for the last unit are serialised by the lock, so stock never goes negative.
- **Compensation** — a cancelled order (by the user, or after `stock-insufficient` / `payment-failed`) publishes `order-cancelled`; Inventory returns the held stock. One `stock_reservations` row per order (RESERVED / REJECTED / RELEASED) makes this idempotent.
- **Out-of-order events** — `order-created` and `order-cancelled` are different topics, so a cancel can arrive first. Inventory records a RELEASED tombstone and ignores the late `order-created`.
- **Choreographed saga** — `order-created → stock-reserved → payment-success` ends in `PAID`; `stock-insufficient` or `payment-failed` cancels the order, and `order-cancelled` makes Inventory return the stock. No central orchestrator.
- **Gateway call outside the DB transaction** — Payment writes a `PENDING` row (unique per order), calls the gateway with no locks held, then records the outcome. A crash or timeout leaves `PENDING`; the Kafka retry reuses the same idempotency key (`order-{id}`), so a customer is never charged twice.
- **Cancel vs. charge races** — cancel before charge: tombstone, never charged. Cancel mid-charge: row flips to `CANCELLED` and the completed charge is refunded instead of publishing success. Cancel after charge: refunded.
- **Saga never stalls** — if the gateway stays down through all retries (1s/2s/4s), the recoverer fails the payment and publishes `payment-failed`, so the order is cancelled and stock released instead of sitting in `CONFIRMED`. Known gap: a timed-out charge may have succeeded at the gateway; production adds a reconciliation job.
- **Saga timeout** — a scheduled job in Order Service cancels any order stuck in `PENDING`/`CONFIRMED` for 15 min (lost event, a service down longer than Kafka's 7-day retention). The cancel publishes `order-cancelled`, so stock and payment are compensated as usual.
- **Authentication at the edge, ownership in the service** — the gateway verifies the JWT (signature, expiry, issuer) and handles role rules (e.g. only ADMIN can add products). It then forwards `X-User-Id`/`X-User-Role`, after deleting any the client sent. Each service enforces ownership with the shared `Caller` type: customers only see their own orders, payments and notifications; another user's order returns 404, not 403, so ids can't be probed.
- **User id from the token, never the body** — `POST /orders` ignores `userId` in the payload when called through the gateway.
- **HS256 shared secret** — simple and fine for one issuer. Upgrade path: RS256 + JWKS so the gateway holds only the public key.
- **No Eureka** — Docker Compose DNS (and Kubernetes Services in Phase 4) already resolve `order-service:8082`; a registry would be one more thing to run for no gain.
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
| http://localhost:8080/swagger-ui.html | **Gateway — Swagger UI for every service** (pick one top-right; click Authorize and paste a token) |
| http://localhost:8081/swagger-ui.html | User Service API |
| http://localhost:8082/swagger-ui.html | Order Service API |
| http://localhost:8083/swagger-ui.html | Inventory Service API |
| http://localhost:8084/swagger-ui.html | Payment Service API |
| http://localhost:8085/swagger-ui.html | Notification Service API |
| http://localhost:8090 | Kafka UI — topics, messages, consumer groups, DLQ |
| http://localhost:8025 | Mailpit — emails that were "sent" |

## Try it through the gateway (Phase 3)

```powershell
# log in (admin is seeded: admin@orderflow.local / admin12345), or register then log in as a customer
$t = (Invoke-RestMethod -Method Post http://localhost:8080/api/v1/users/login -ContentType 'application/json' -Body '{"email":"saurabh@example.com","password":"password123"}').accessToken
$h = @{ Authorization = "Bearer $t" }

Invoke-RestMethod http://localhost:8080/api/v1/users/me -Headers $h
Invoke-RestMethod -Method Post http://localhost:8080/api/v1/orders -Headers $h -ContentType 'application/json' -Body '{"items":[{"productId":7,"quantity":1,"unitPrice":10}]}'
Invoke-RestMethod http://localhost:8080/api/v1/orders -Headers $h          # only your orders
Invoke-RestMethod http://localhost:8080/api/v1/orders                      # 401: no token
Invoke-RestMethod http://localhost:8080/api/v1/users -Headers $h           # 403: admin only
```

## Try the flow (calling services directly, bypassing the gateway)

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

**Inventory in action:** place an order for 6 keyboards (`productId` 205, only 5 in stock) → order goes `CANCELLED` with reason *Insufficient stock*. Place one for 2 mice (101) → `CONFIRMED`; `GET localhost:8083/api/v1/products/101` shows 2 reserved; cancel it → back to 0 reserved.

**Payment in action:** a small order ends `PAID` (`GET localhost:8084/api/v1/payments/{orderId}` → `SUCCEEDED`). An order over Rs. 10,000 (e.g. 3 × productId 205) is declined → order `CANCELLED`, stock returned, two emails. Gateway outage: `POST localhost:8084/api/v1/payments/gateway/outage?enabled=true`, place an order, watch `docker compose logs -f payment-service` retry 3 times then fail the payment; turn it off with `enabled=false`.

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

## Run it on Kubernetes (Phase 4)

`docker compose build && docker compose down && kubectl apply -k k8s` — details in [k8s/README.md](k8s/README.md). Only the gateway is exposed (`localhost:8080`); every other service is cluster-internal.

Or with Helm, where one template renders all six services from `values.yaml`:
`helm upgrade --install orderflow helm/orderflow -n orderflow --create-namespace`.
[`values-prod.example.yaml`](helm/orderflow/values-prod.example.yaml) shows a production setup: managed MySQL and Kafka, a registry image with a fixed version tag, an externally managed secret, and an Ingress.

## Tests
```bash
mvn test
```
Unit tests cover: password hashing + event publishing, duplicate email, order total calculation, illegal cancel, idempotent cancel on redelivered events, duplicate-notification suppression, retry propagation, unknown-user skip.

## Roadmap

- [x] **Phase 1** — User, Order, Notification services · Kafka · retry/DLQ · pagination · Swagger · Docker Compose
- [x] **Phase 2** — Inventory + Payment services (full saga: `order-created → stock-reserved → payment-success/failed`), compensation (restock on cancel)
- [x] **Phase 3** — Spring Cloud Gateway + JWT auth, role checks, per-user data ownership, aggregated Swagger (Eureka deliberately skipped, see above)
- [x] **Phase 4a** — Kubernetes manifests (Deployments, StatefulSets + PVCs, Services, ConfigMap/Secret, Ingress, HPA, probes, init containers)
- [x] **Phase 4b** — Helm chart (one templated Deployment/Service/HPA for every service, toggleable bundled infra, per-environment values)
- [ ] **Phase 5** — Flyway migrations, Testcontainers integration tests, transactional outbox, Prometheus + Grafana, keyset pagination
