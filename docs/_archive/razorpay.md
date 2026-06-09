Critical implementation details for Razorpay:
Step 2 is where most implementations fail. You must lock inventory before creating the Razorpay order — not after. If you create the Razorpay order first and then discover the item is out of stock, you have an orphaned payment. The lock uses SETNX razorpay:lock:{variantId} {orderId} EX 600 — a 10-minute Redis lock. If the lock is already held (someone else is mid-checkout for the last unit), return a 409 Conflict immediately.
Step 8 is the webhook handler. Razorpay will call your /api/v1/payments/webhook endpoint. This endpoint must:

Be excluded from JWT authentication (it's called by Razorpay, not your user)
Validate the X-Razorpay-Signature header: HMAC-SHA256(razorpayOrderId + "|" + razorpayPaymentId, webhookSecret)
Check for idempotency: look up the idempotency_key in payment_transactions — if a row already exists with status SUCCESS, return 200 immediately without re-processing
Only then transition the order from PENDING → CONFIRMED

The frontend also calls your backend after modal success (step 10 arrow) as a belt-and-suspenders check, but your order confirmation must always be driven by the webhook. Never trust the frontend callback as the source of truth.
