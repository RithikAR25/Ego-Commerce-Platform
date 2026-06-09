# Checkout Flow

> Source-verified from `OrderService.checkout()`, `InventoryReservationService.commit()`, and `CheckoutPage.tsx`.

---

## End-to-End Checkout Journey

```mermaid
flowchart TD
    A[Customer clicks Checkout] --> B[/checkout route\nProtectedRoute guard]
    B --> C{Authenticated?}
    C -->|No| Login[Redirect to /login]
    C -->|Yes| D[CheckoutPage.tsx\nMUI Stepper]

    subgraph Step 1: Address
        D --> E{Has saved addresses?}
        E -->|Yes| F[Select from address book]
        E -->|No| G[Enter new address]
        F --> H[Continue]
        G --> H
    end

    subgraph Step 2: Review Cart
        H --> I[Display cart items\nlive prices, qty]
        I --> J{Apply coupon?}
        J -->|Yes| K[GET /coupons/validate?code=&subtotal=]
        K --> L{Valid?}
        L -->|Yes| M[Show discount preview]
        L -->|No| N[Show error]
        J -->|No| O[Show subtotal + shipping]
        M --> O
    end

    subgraph Step 3: Payment
        O --> P[Click Pay Now]
        P --> Q[POST /payments/razorpay/create]
        Q --> R[Open Razorpay Checkout.js modal]
        R --> S{Payment outcome}
        S -->|Success| T[Razorpay sends webhook to backend]
        S -->|Failure/Cancel| U[Stay on checkout]
        T --> V[Backend: PENDING_PAYMENT → CONFIRMED]
        V --> W[Poll GET /orders/orderId until CONFIRMED]
        W --> X[Redirect: /checkout/success/orderId]
    end
```

---

## Backend Checkout Pipeline (`@Transactional`)

```mermaid
flowchart LR
    START([POST /orders/checkout]) --> A[Load Redis cart]
    A --> B{Cart empty?}
    B -->|Yes| ERR1[400 Bad Request]
    B -->|No| C[For each item:\nInventoryReservationService.commit]
    C --> D{All commits OK?}
    D -->|409 Insufficient stock| ERR2[409 Conflict\nrollback]
    D -->|OK| E{Coupon code in request?}
    E -->|Yes| F[Validate coupon:\nactive, not expired, min order, uses limit]
    F --> G{Valid?}
    G -->|No| ERR3[400 Bad Request\nrollback]
    G -->|Yes| H[Compute discount\nIncrement coupon.currentUses]
    E -->|No| I[No discount]
    H --> J[Persist Order + OrderItems + StatusHistory PENDING_PAYMENT]
    I --> J
    J --> K[saveAndFlush → populate @Id, @CreationTimestamp]
    K --> L[CartService.clearCart — Redis DEL]
    L --> M([Return OrderDetailResponse])
    M --> N[Publish OrderPlacedEvent async]
    N --> O[SendGrid: order confirmation email]
```

---

## Coupon Discount Formula

```
subtotal = sum(item.unitPrice × item.quantity)

if FLAT:
    discount = min(coupon.discountValue, subtotal)
    
if PERCENTAGE:
    discount = subtotal × (coupon.discountPercent / 100)
    if coupon.maxDiscountAmount is set:
        discount = min(discount, coupon.maxDiscountAmount)

grandTotal = max(subtotal - discount, 0) + shippingTotal
```

---

## Payment Verification Page

After Razorpay Checkout.js modal closes:

```mermaid
stateDiagram-v2
    [*] --> Polling : Redirect to /checkout/verify/orderId
    Polling --> Polling : GET /orders/orderId every 2s
    Polling --> Confirmed : order.status == CONFIRMED
    Polling --> Timeout : 60s elapsed
    Confirmed --> Success : Redirect /checkout/success/orderId
    Timeout --> Failed : Show "Payment verification failed" + retry
```

---

## Cart → Checkout → Order Data Flow

```mermaid
graph LR
    Redis[(Redis cart\nvariantId: qty)] -->|CartService.getCart| Checkout
    MySQL_V[(MySQL\nproduct_variants\ninventory_records)] -->|findAllById batch| Checkout
    MySQL_A[(MySQL\nuser_addresses)] -->|Selected address| Checkout
    Coupon[(MySQL\ncoupons)] -->|Validate code| Checkout

    Checkout -->|commit inventory\npersist order| MySQL_O[(MySQL\norders\norder_items\norder_status_history)]
    Checkout -->|clearCart| Redis
    Checkout -->|publish event| Email[SendGrid Email]
```
