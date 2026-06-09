# Order Lifecycle

> All states and transitions verified against `OrderStatus.java` and `ReturnStatus.java`. State machines are exact representations of the source enum switch statements.

---

## Order Status Machine

```mermaid
stateDiagram-v2
    [*] --> PENDING_PAYMENT : POST /orders/checkout

    PENDING_PAYMENT --> CONFIRMED : Razorpay webhook (auto)\nor Admin manual (COD/bank)
    PENDING_PAYMENT --> CANCELLED : Customer cancel\nor Admin cancel

    CONFIRMED --> PROCESSING : Admin
    CONFIRMED --> CANCELLED : Admin

    PROCESSING --> SHIPPED : Admin

    SHIPPED --> OUT_FOR_DELIVERY : Admin
    SHIPPED --> DELIVERED : Admin (skip OFD stage)

    OUT_FOR_DELIVERY --> DELIVERED : Admin

    DELIVERED --> REFUNDED : Returns module ONLY\n(never via admin status endpoint)

    DELIVERED --> [*] : Terminal
    CANCELLED --> [*] : Terminal
    REFUNDED --> [*] : Terminal

    note right of PENDING_PAYMENT
        Inventory committed at checkout.\nCustomer can cancel up to this point.
    end note

    note right of DELIVERED
        DELIVERED timestamp in OrderStatusHistory\nis source of truth for 7-day return window.
    end note
```

---

## Return Status Machine

```mermaid
stateDiagram-v2
    [*] --> REQUESTED : POST /orders/{id}/returns\n(7-day window from DELIVERED)

    REQUESTED --> APPROVED : Admin approves
    REQUESTED --> REJECTED : Admin rejects

    APPROVED --> REFUND_INITIATED : Razorpay refund API called\n(OUTSIDE @Transactional)
    REFUND_INITIATED --> REFUND_COMPLETED : Refund confirmed\nOrder → REFUNDED\nInventory restored

    REJECTED --> [*] : Terminal (customer may resubmit)
    REFUND_COMPLETED --> [*] : Terminal

    note right of APPROVED
        Transient state — briefly persisted\nbefore Razorpay API call.
    end note
```

---

## Combined Timeline: Order + Return

```mermaid
gantt
    title Order to Refund Timeline
    dateFormat  YYYY-MM-DD
    section Order
    PENDING_PAYMENT    :a1, 2026-06-01, 1d
    CONFIRMED          :a2, after a1, 1d
    PROCESSING         :a3, after a2, 2d
    SHIPPED            :a4, after a3, 3d
    OUT_FOR_DELIVERY   :a5, after a4, 1d
    DELIVERED          :milestone, after a5, 0d
    section Return Window
    7-day window open  :crit, ret1, after a5, 7d
    section Return
    REQUESTED          :b1, after a5, 1d
    APPROVED           :b2, after b1, 1d
    REFUND_COMPLETED   :b3, after b2, 2d
```

---

## Inventory State Through Order Lifecycle

```mermaid
flowchart LR
    subgraph Cart
        A["qty_available: 10<br/>qty_reserved: 0"]
        A -->|Add to cart<br/>reserve(5)| B
        B["qty_available: 10<br/>qty_reserved: 5"]
    end

    subgraph Checkout
        B -->|commit(5)<br/>@Transactional| C
        C["qty_available: 5<br/>qty_reserved: 0"]
    end

    subgraph CancelOrReturn
        C -->|restore(5)<br/>cancel or refund| D
        D["qty_available: 10<br/>qty_reserved: 0"]
    end
```

---

## Admin State Transition Rules (source-verified)

| From | To | Trigger |
|---|---|---|
| `PENDING_PAYMENT` | `CONFIRMED` | Admin manual (COD) or Razorpay webhook |
| `PENDING_PAYMENT` | `CANCELLED` | Customer cancel or admin cancel |
| `CONFIRMED` | `PROCESSING` | Admin |
| `CONFIRMED` | `CANCELLED` | Admin |
| `PROCESSING` | `SHIPPED` | Admin |
| `SHIPPED` | `OUT_FOR_DELIVERY` | Admin |
| `SHIPPED` | `DELIVERED` | Admin (skip OFD) |
| `OUT_FOR_DELIVERY` | `DELIVERED` | Admin |
| `DELIVERED` | `REFUNDED` | **Returns module only** — blocked on admin status endpoint |

**Blocked transitions** (all throw `400 Invalid status transition`):
- Any backward transition (e.g. `SHIPPED → CONFIRMED`)
- `DELIVERED → CANCELLED`
- Setting `REFUNDED` via admin status endpoint
- Any transition from terminal states (`DELIVERED`, `CANCELLED`, `REFUNDED`)
