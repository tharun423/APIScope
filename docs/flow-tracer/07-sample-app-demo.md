# 07 — Sample App Demo: Live Execution Trace

## Setup

Before running the demo, make sure `agentic.docs.flow.enabled=true` is set
in the sample app's `application.properties`:

```properties
# agentic-docs-sample-app/src/main/resources/application.properties
agentic.docs.flow.enabled=true
```

Start the application:

```bash
mvn spring-boot:run -pl agentic-docs-sample-app
```

Open the browser at `http://localhost:8080`.

---

## The Checkout Scenario

The sample app includes a 3-layer checkout pipeline added specifically to
demonstrate Flow Tracer. The full class hierarchy is:

```
OrderFlowController          (@RestController)
  └── OrderService           (@Service)
        ├── InventoryService (@Service)
        │     └── InventoryRepository (@Repository)
        └── PaymentService   (@Service)
              └── PaymentRepository (@Repository)
```

### POST /api/v1/checkout

**Full 10-step happy path**

| Step | Layer | Class | Method | Duration |
|------|-------|-------|--------|----------|
| 0 | CONTROLLER | OrderFlowController | checkout | ~120ms |
| 1 | SERVICE | OrderService | validateOrder | ~5ms |
| 2 | SERVICE | InventoryService | checkAvailability | ~20ms |
| 3 | REPOSITORY | InventoryRepository | findStockByProductId | ~15ms |
| 4 | SERVICE | InventoryService | reserveInventory | ~25ms |
| 5 | REPOSITORY | InventoryRepository | reserveStock | ~20ms |
| 6 | SERVICE | PaymentService | validatePayment | ~30ms |
| 7 | REPOSITORY | PaymentRepository | validatePaymentMethod | ~25ms |
| 8 | SERVICE | PaymentService | authorisePayment | ~50ms |
| 9 | REPOSITORY | PaymentRepository | createPaymentRecord | ~18ms |

> **Note:** Steps arrive in completion order via SSE (inner methods complete
> first). The UI sorts them by `stepIndex` to display them in call order as
> shown above.

---

## Demo Walkthrough

### Step 1 — Navigate to Flow Tracer

Click the **Flow Tracer** tab in the top navigation bar (Workflow icon).

### Step 2 — Select the Endpoint

In the endpoint dropdown, look for the **Checkout** group (or filter by typing
"checkout"). Select:

```
POST /api/v1/checkout
```

### Step 3 — Fill in the Request Body

A body textarea appears for POST endpoints. Paste:

```json
{
  "productId": "P001",
  "quantity": 2,
  "customerId": "C001",
  "paymentMethod": "CARD"
}
```

### Step 4 — Click "Send Request"

The button changes to a spinner ("Tracing…"). Within milliseconds, step cards
start appearing from the top:

```
┌──────────────────────────────────────────────────────┐
│  CONTROLLER  OrderFlowController.checkout()          │
│  Input: {"productId":"P001","quantity":2,...}        │
│  (waiting for output...)                             │
└──────────────────────────────────────────────────────┘
         │
┌──────────────────────────────────────────────────────┐
│  SERVICE   OrderService.validateOrder()         3ms  │
│  Input: {"productId":"P001","quantity":2,...}        │
│  Output: null (validation passes, returns void)      │
└──────────────────────────────────────────────────────┘
         │
┌──────────────────────────────────────────────────────┐  ← appears live
│  REPOSITORY  InventoryRepository.findStock…    15ms  │
│  Input: ["P001"]                                     │
│  Output: {"productId":"P001","stock":100}            │
└──────────────────────────────────────────────────────┘
```

### Step 5 — See the Final Response

After all steps, a green card appears at the bottom:

```
┌──────────────────────────────────────────────────────┐
│  ✓ 200 OK  |  87ms  |  10 steps                     │
│                                                      │
│  {                                                   │
│    "orderId": "ORD-168D1D26",                       │
│    "status": "CONFIRMED",                           │
│    "productId": "P001",                             │
│    "quantity": 2,                                   │
│    "totalAmount": 199.98                            │
│  }                                                   │
└──────────────────────────────────────────────────────┘
```

---

## Error Scenario

### POST /api/v1/checkout/simulate-error

This endpoint triggers a stock-out error by requesting quantity 999.

In the Flow Tracer, select:

```
POST /api/v1/checkout/simulate-error
```

No body is needed (it ignores the body and hardcodes `quantity=999`).

**Expected flow:**

```
┌──────────────────────────────────────────────────────┐
│  CONTROLLER  OrderFlowController.simulateError()     │
└──────────────────────────────────────────────────────┘
         │
┌──────────────────────────────────────────────────────┐
│  SERVICE   OrderService.checkout()                   │
└──────────────────────────────────────────────────────┘
         │
┌──────────────────────────────────────────────────────┐
│  SERVICE   InventoryService.checkAvailability()      │
└──────────────────────────────────────────────────────┘
         │
┌──────────────────────────────────────────────────────┐
│  REPOSITORY  InventoryRepository.findStock…          │
└──────────────────────────────────────────────────────┘
         │
┌──────────────────────────────────────────────────────┐  ← RED border
│  SERVICE   InventoryService.checkAvailability()  2ms │
│  ERROR: IllegalStateException:                       │
│  Insufficient stock for product P001. Requested:     │
│  999, Available: 100                                 │
└──────────────────────────────────────────────────────┘
```

The error propagates up — `OrderService.checkout()` and
`OrderFlowController.simulateError()` also show as ERROR.

**Final response card:**

```
┌──────────────────────────────────────────────────────┐
│  ✕ 500 Internal Server Error  |  45ms  |  N steps   │
│  {"timestamp":"...","status":500,...}                │
└──────────────────────────────────────────────────────┘
```

---

## What Each Layer Tells You

### CONTROLLER steps

Shows the raw HTTP request body (deserialised by Spring) and the final HTTP
response body. Useful for confirming that your request was parsed correctly
and that the response shape is what you expect.

### SERVICE steps

Shows the domain objects flowing through business logic. If your DTO is being
mapped incorrectly to a domain object, this is where you'll see it.

### REPOSITORY steps

Shows the exact arguments going into your data layer (the query parameters)
and what came back. Useful for debugging stale data, incorrect IDs, or
unexpected null returns.

---

## Tips

- **Quantity 1-42** → successful checkout (stock limit in `InventoryService`)
- **Quantity 43+** → `IllegalStateException` (stock-out simulation)
- **Invalid paymentMethod** → `IllegalArgumentException` from `PaymentService`
- Click **Reset** to clear the diagram and run a new trace
- Run the same endpoint twice to compare call chains side by side (open in two tabs)
