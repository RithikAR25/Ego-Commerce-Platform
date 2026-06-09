# SendGrid Notifications — E2E Testing Guide

**Phase:** 8 — Notifications (SendGrid)  
**Date:** May 26, 2026  
**Prerequisites:** Phase 7 (Razorpay) complete and E2E verified. Spring Boot running on `:8080`. MySQL and Redis running.

---

## Pre-Test Setup

### 1. Verify application started with SendGrid wiring
In the Spring Boot startup logs, look for:
```
[SendGrid] Client initialized with real API key.
```
(Or the placeholder warning if using `SG.placeholder`.)

Also verify the async pool:
```
INFO  --- [main] o.s.s.c.ThreadPoolTaskExecutor : Initializing ExecutorService 'applicationTaskExecutor'
```

### 2. Verify the notification_logs table exists
```sql
USE rawego;
DESCRIBE notification_logs;
```
Expected: table with columns `id, order_id, recipient_email, event_type, status, error_message, message_id, created_at`.

With `ddl-auto=update`, Hibernate auto-creates this on first startup.

---

## Section A — Order Placed Notification (ORDER_PLACED)

### A-1: Place an order and check for notification log

**Step 1:** Ensure user has items in cart. If not, add one:
```
POST /api/v1/cart/add
Body: { "variantId": 1, "quantity": 1 }
```

**Step 2:** Checkout
```
POST /api/v1/orders/checkout
Body: { "shippingAddress": "123 MG Road, Bengaluru, Karnataka 560001" }
```
Expected: `201 Created` with order detail response.

**Step 3:** Note the returned `id` (e.g. orderId = `6`)

**Step 4:** Check notification_logs (wait ~2 seconds for async thread):
```sql
SELECT * FROM notification_logs WHERE order_id = 6 ORDER BY created_at DESC;
```
Expected row:
```
event_type = ORDER_PLACED
status     = SUCCESS  (or FAILED if SendGrid key not configured)
recipient_email = <your test user's email>
message_id = <non-empty string>   (on SUCCESS)
```

**Step 5 (if real API key):** Check your inbox for order confirmation email.
Expected subject: `Your EGO order #6 has been received!`
Expected content: EGO branded dark-theme HTML email with order items table, grand total, shipping address.

---

### A-2: Verify checkout response is NOT delayed

The order `201` response must arrive immediately — the email dispatch is async.
**Check:** Response time for the checkout call should be `< 300ms` regardless of SendGrid latency.

In Spring logs, you should see:
```
INFO  [http-nio-8080-exec-1] OrderService     : Order placed: orderId=6 userId=1 grandTotal=...
INFO  [ego-async-1] NotificationEventListener : [NotificationListener] ORDER_PLACED received: orderId=6 userId=1
INFO  [ego-async-1] NotificationService       : [Notification] Email sent: orderId=6 eventType=ORDER_PLACED ...
```

Note the different thread names (`http-nio-*` for the HTTP response, `ego-async-*` for the email send).

---

## Section B — Payment Confirmed Notification (PAYMENT_CONFIRMED)

### B-1: Create Razorpay payment order
```
POST /api/v1/payments/razorpay/create
Body: { "orderId": 6 }
```
Expected: `200 OK` with `razorpayOrderId`.

### B-2: Simulate webhook to trigger PAYMENT_CONFIRMED notification

> ⚠️ **Tooling note:** PowerShell may silently alter JSON payload bytes when passing through `curl.exe`, causing HMAC mismatches even with correct computation. This is a **local tooling issue only** — production Razorpay webhooks are unaffected. **Use the approach below** (backend debug log `computed_sig`) instead of computing the HMAC yourself in PowerShell. See the [Appendix in the Razorpay E2E guide](./razorpay-payment-e2e-testing-guide.md#appendix--powershellcurl-hmac-mismatch-explained) for full details.

#### Step 1 — Send with dummy signature (to extract the backend's correct HMAC)

```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/webhooks/razorpay" `
  -Method POST `
  -ContentType "application/json" `
  -Headers @{"X-Razorpay-Signature" = "dummy"} `
  -Body '{"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_TestPaymentId123","order_id":"<razorpayOrderId>"}}}}'
```

This call will be rejected, but the Spring Boot console will log:
```
[Webhook HMAC Debug] computed_sig=<COPY THIS VALUE>
```

#### Step 2 — Resend with the correct computed_sig (copy-paste the same body string exactly)

```powershell
$correctSig = "<paste computed_sig from Spring Boot log>"

Invoke-WebRequest -Uri "http://localhost:8080/api/v1/webhooks/razorpay" `
  -Method POST `
  -ContentType "application/json" `
  -Headers @{"X-Razorpay-Signature" = $correctSig} `
  -Body '{"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_TestPaymentId123","order_id":"<razorpayOrderId>"}}}}'
```

Expected: `200 OK`. Spring Boot log: `[Webhook HMAC Debug] match=true`

### B-3: Check notification_logs
```sql
SELECT * FROM notification_logs WHERE order_id = 6 ORDER BY created_at DESC;
```
Expected: **2 rows** — one `ORDER_PLACED` and one `PAYMENT_CONFIRMED`, both `SUCCESS`.

### B-4 (if real API key): Check inbox
Expected subject: `Payment confirmed — EGO order #6 is being prepared`
Expected content: Green "Payment Confirmed" banner, payment ID, order summary, "We're preparing your order" message.

---

## Section C — Idempotency (No Duplicate Emails or Order Changes)

### C-1: Fire the same webhook twice
Reuse the exact same webhook body + `$correctSig` from B-2 and POST again:
```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/webhooks/razorpay" `
  -Method POST `
  -ContentType "application/json" `
  -Headers @{"X-Razorpay-Signature" = $correctSig} `
  -Body '{"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_TestPaymentId123","order_id":"<razorpayOrderId>"}}}}'
```

**Expected: `200 OK`** — the handler detects the order is already `CONFIRMED`, skips all side effects, and returns success.

Spring Boot log will show:
```
[Webhook] Duplicate delivery detected (idempotent skip): razorpayOrderId=order_XXX orderId=N — already CONFIRMED, returning 200 OK
```

> **Why 200, not 409?** Razorpay retries webhook delivery until it receives a 2xx response. Returning anything other than 2xx for a valid duplicate would cause infinite re-deliveries. The production contract is: valid signature + already-processed order = silent 200 OK.

- [ ] `200 OK`
- [ ] Spring Boot log shows `Duplicate delivery detected (idempotent skip)`

### C-2: Verify no duplicate notification log
```sql
SELECT event_type, status, COUNT(*) FROM notification_logs
WHERE order_id = 6
GROUP BY event_type, status;
```
Expected:
```
ORDER_PLACED      | SUCCESS | 1
PAYMENT_CONFIRMED | SUCCESS | 1
```
No duplicates. The `alreadySent()` check in `NotificationService` prevents double-send even on duplicate webhook delivery.



---

## Section D — Error Resilience (SendGrid Failure)

### D-1: Test with placeholder API key

**Setup:** Temporarily change `SENDGRID_API_KEY` in `.env` to `SG.placeholder` and restart the server.

**Action:** POST checkout to create a new order.

**Expected:**
- `201 Created` response (order created successfully — email error does NOT affect this)
- Spring log: `[Notification] SendGrid non-2xx: ... status=401`
- DB: notification_log row with `status=FAILED`, `error_message` contains "SendGrid HTTP 401"

```sql
SELECT status, error_message FROM notification_logs ORDER BY created_at DESC LIMIT 1;
```

**Restore:** Set the real API key back and restart.

---

## Section E — DB Final State Verification

After running Sections A–D, run:
```sql
-- Full log view
SELECT
    id,
    order_id,
    recipient_email,
    event_type,
    status,
    SUBSTRING(error_message, 1, 80) AS error_snippet,
    message_id,
    created_at
FROM notification_logs
ORDER BY created_at DESC;

-- Counts by status
SELECT status, COUNT(*) FROM notification_logs GROUP BY status;

-- Counts by event type
SELECT event_type, COUNT(*) FROM notification_logs GROUP BY event_type;
```

---

## Common Issues

| Issue | Cause | Fix |
|---|---|---|
| No log row after checkout | Async thread didn't run yet | Wait 2 seconds; check Spring logs |
| `status = FAILED`, error = "401 Unauthorized" | SendGrid API key invalid or placeholder | Set real key in `.env`, restart |
| `status = FAILED`, error = "Order not found" | Race condition — notification fired before flush visible | Should not happen (saveAndFlush used). Check logs. |
| Email not received | From-email not verified in SendGrid | Verify sender in SendGrid dashboard |
| Email in spam | SendGrid reputation low / no SPF/DKIM | Configure Domain Authentication in SendGrid |
| No `ego-async-*` thread in logs | `@EnableAsync` not active | Verify `@EnableAsync` on `RawEgoApplication.java` |
