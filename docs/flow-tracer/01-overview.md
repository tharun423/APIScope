# 01 — Flow Tracer: Overview

## What Is It?

Flow Tracer is a real-time API execution visualizer built into Agentic Docs.
When a developer sends an HTTP request through the Flow Tracer UI, every Spring bean
method that participates in handling that request is intercepted, timed, and streamed
back to the browser as a live step-by-step diagram — before the final response arrives.

---

## The Problem It Solves

In a typical Spring Boot application, a single HTTP request fans out through many layers:

```
POST /api/v1/checkout
  → OrderFlowController.checkout()
      → OrderService.validateOrder()
      → InventoryService.checkAvailability()
          → InventoryRepository.findStockByProductId()   ← DB call
      → InventoryService.reserveInventory()
          → InventoryRepository.reserveStock()           ← DB call
      → PaymentService.validatePayment()
          → PaymentRepository.validatePaymentMethod()    ← external API
      → PaymentService.authorisePayment()
          → PaymentRepository.createPaymentRecord()      ← DB write
      → OrderService.confirmOrder()
```

Without Flow Tracer, a developer can only see:
- The request they sent
- The final response
- Logs (if they are verbose enough)

**Flow Tracer makes the invisible call chain visible** — in real-time, with exact
inputs, outputs, layer labels, and durations for every method.

---

## Key Concepts

### Trace
A single execution run tied to a UUID (`traceId`). Every method call during that
HTTP request produces a `TraceEvent` attached to the trace.

### TraceEvent
A snapshot of one method call:
- Which layer it belongs to (`CONTROLLER`, `SERVICE`, `REPOSITORY`)
- Which class and method was called
- The exact JSON-serialized input arguments
- The JSON-serialized return value
- How long it took (`durationMs`)
- Whether it succeeded (`EXIT`) or threw an exception (`ERROR`)

### SSE Stream
Server-Sent Events (SSE) is used to push `TraceEvent` objects to the browser
as they happen — without polling, without WebSockets, without blocking the
main request thread. Each event arrives and immediately renders as a new card
in the Flow Diagram.

### AOP Interception
Spring AOP intercepts public method calls on `@Service`, `@RestController`,
and `@Repository` beans using an `@Around` advice. The interception is
completely transparent — no annotation or code change is needed in the
host application.

---

## What You See in the UI

```
┌────────────────────────────────────────────────────┐
│  CONTROLLER  OrderFlowController.checkout()   12ms │
│  ┌ Input ──────────────────────────────────────┐   │
│  │ {"productId":"P001","quantity":2,...}        │   │
│  └─────────────────────────────────────────────┘   │
│  ┌ Output ─────────────────────────────────────┐   │
│  │ {"orderId":"ORD-168D1D26","status":"CONF...} │   │
│  └─────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────┘
           │ (dashed connector)
┌────────────────────────────────────────────────────┐
│  SERVICE   OrderService.validateOrder()        3ms  │
│  ...                                               │
└────────────────────────────────────────────────────┘
           │
┌────────────────────────────────────────────────────┐  ← RED if exception
│  SERVICE   InventoryService.checkAvailability() 2ms │
│  ┌ Error ──────────────────────────────────────┐   │
│  │ IllegalStateException: Insufficient stock…  │   │
│  └─────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────┘
```

---

## Who Is It For?

- **New developers** onboarding onto a Spring Boot application — they can
  see exactly which service handles which part of a request
- **Senior developers** debugging a subtle integration issue — they can see
  the exact input each layer received and what it returned
- **Tech leads** reviewing code paths — they can verify the call chain matches
  the intended design without reading through every class

---

## Non-Goals

- Flow Tracer does **not** replace a profiler (no heap/CPU metrics)
- Flow Tracer does **not** persist traces (in-memory only, lost on restart)
- Flow Tracer is **not** for production traffic monitoring (it is an opt-in
  developer tool — off by default)
