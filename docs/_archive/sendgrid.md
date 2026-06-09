# SendGrid Integration — EGO Platform

This document covers the SendGrid transactional email integration for the EGO platform (Phase 8).

---

## 1. Overview

EGO uses SendGrid to send event-driven, asynchronous transactional emails to customers at critical commerce lifecycle moments. The integration is designed to be:

- **Non-blocking** — email dispatch never delays the order/payment HTTP response
- **Resilient** — SendGrid failures do NOT roll back orders or payments
- **Auditable** — every send attempt is logged to `notification_logs`
- **Idempotent** — duplicate events (e.g. double webhooks) never send duplicate emails

---

## 2. Environment Configuration

### `.env` (local development)
```env
SENDGRID_API_KEY=SG.your_api_key_here
SENDGRID_FROM_EMAIL=dev-email@ego.com
```

### `application.properties` bindings
```properties
sendgrid.api-key=${SENDGRID_API_KEY:SG.placeholder}
sendgrid.from-email=${SENDGRID_FROM_EMAIL:noreply@ego.com}
sendgrid.from-name=EGO
```

### Getting a SendGrid API key
1. Sign in at [app.sendgrid.com](https://app.sendgrid.com)
2. Navigate to **Settings → API Keys → Create API Key**
3. Choose **Restricted Access** → grant **Mail Send → Full Access**
4. Copy the key and set it as `SENDGRID_API_KEY` in `.env`

### Sender Authentication
The `from-email` address **must** be verified in SendGrid:
- **Settings → Sender Authentication → Single Sender Verification**
- Or use **Domain Authentication** for production (`@ego.com`)

---

## 3. Architecture

```
OrderService.checkout()
      │
      ├─► saveAndFlush(order)                 ← order committed to DB
      └─► publishEvent(OrderPlacedEvent)      ← Spring ApplicationEvent

PaymentService.confirmOrder()
      │
      ├─► saveAndFlush(order)                 ← CONFIRMED + razorpay_payment_id committed
      └─► publishEvent(PaymentConfirmedEvent) ← Spring ApplicationEvent

                    ▼ (Spring async thread pool: ego-async-*)

NotificationEventListener
  @Async @EventListener
      │
      └─► NotificationService.send*()
                │
                ├─► idempotency check (NotificationLogService.alreadySent)
                ├─► build inline HTML email
                ├─► SendGrid.api(request)
                └─► NotificationLogService.logSuccess() / .logFailure()
                              │
                              └─► notification_logs (REQUIRES_NEW transaction)
```

---

## 4. Email Trigger Matrix

| Event | Status Transition | Email Sent | Subject |
|---|---|---|---|
| `ORDER_PLACED` | Cart → `PENDING_PAYMENT` | Order confirmation | `Your EGO order #N has been received!` |
| `PAYMENT_CONFIRMED` | `PENDING_PAYMENT` → `CONFIRMED` | Payment success | `Payment confirmed — EGO order #N is being prepared` |

---

## 5. Notification Log Schema

Table: `notification_logs`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT UNSIGNED | Auto-increment PK |
| `order_id` | BIGINT UNSIGNED | Nullable — references EGO order |
| `recipient_email` | VARCHAR(254) | Destination address |
| `event_type` | VARCHAR(30) | `ORDER_PLACED` or `PAYMENT_CONFIRMED` |
| `status` | VARCHAR(10) | `SUCCESS` or `FAILED` |
| `error_message` | TEXT | Null on success; SendGrid error or exception detail on failure |
| `message_id` | VARCHAR(255) | SendGrid `X-Message-Id` header — use for Activity Feed lookup |
| `created_at` | TIMESTAMP(6) | Auto-set |

---

## 6. Module Structure

```
com.ego.raw_ego.notification/
├── config/
│   └── SendGridConfig.java          ← @Bean for SendGrid client
├── entity/
│   └── NotificationLog.java         ← JPA entity → notification_logs table
├── enums/
│   ├── NotificationEventType.java   ← ORDER_PLACED, PAYMENT_CONFIRMED
│   └── NotificationStatus.java      ← SUCCESS, FAILED
├── event/
│   ├── OrderPlacedEvent.java        ← Spring ApplicationEvent (checkout trigger)
│   └── PaymentConfirmedEvent.java   ← Spring ApplicationEvent (webhook trigger)
├── listener/
│   └── NotificationEventListener.java ← @Async @EventListener dispatcher
├── repository/
│   └── NotificationLogRepository.java ← JPA repo + idempotency query
└── service/
    ├── NotificationLogService.java  ← Log persistence (REQUIRES_NEW transaction)
    └── NotificationService.java     ← Email builder + SendGrid dispatch
```

---

## 7. Idempotency Guarantee

Before every send, `NotificationService` calls `NotificationLogService.alreadySent(orderId, eventType)`, which queries:

```sql
SELECT COUNT(*) > 0 FROM notification_logs
WHERE order_id = ? AND event_type = ? AND status = 'SUCCESS'
```

If a SUCCESS row already exists, the email is silently skipped. This prevents duplicate emails on:
- Razorpay duplicate webhook deliveries
- Any other event replay scenarios

---

## 8. Error Handling

| Scenario | Behaviour |
|---|---|
| SendGrid returns 4xx/5xx | `FAILED` row logged; exception NOT thrown; order unaffected |
| SendGrid SDK throws IOException | `FAILED` row logged; exception caught internally |
| Order not found at notification time | `FAILED` row logged with "Order not found in DB" message |
| API key is placeholder `SG.placeholder` | Warning logged at startup; `FAILED` rows on all sends |

---

## 9. Async Thread Pool Config

```properties
spring.task.execution.pool.core-size=2
spring.task.execution.pool.max-size=5
spring.task.execution.pool.queue-capacity=50
spring.task.execution.thread-name-prefix=ego-async-
```

Threads are named `ego-async-1`, `ego-async-2`, etc. — easily identifiable in logs.

---

## 10. Monitoring

```sql
-- All recent notifications
SELECT * FROM notification_logs ORDER BY created_at DESC LIMIT 20;

-- Failed notifications (for investigation/retry)
SELECT * FROM notification_logs WHERE status = 'FAILED' ORDER BY created_at DESC;

-- Success rate by event type
SELECT event_type, status, COUNT(*) as count
FROM notification_logs
GROUP BY event_type, status;

-- Notifications for a specific order
SELECT * FROM notification_logs WHERE order_id = 5 ORDER BY created_at;
```
