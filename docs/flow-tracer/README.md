# Flow Tracer — Documentation Index

This folder contains the complete technical documentation for the **Flow Tracer** feature
(`agentic-docs-flow` module) — a real-time API execution visualizer built on Spring AOP + SSE.

---

## Files

| File | What it covers |
|---|---|
| [01-overview.md](./01-overview.md) | What Flow Tracer is, the problem it solves, key concepts |
| [02-architecture.md](./02-architecture.md) | Module layout, component diagram, data flow |
| [03-backend-deep-dive.md](./03-backend-deep-dive.md) | Every Java class — `spi` interfaces, `TraceSerializer`, `FlowUrlBuilder`, registry, AOP, executor, controller, auto-config |
| [04-frontend-deep-dive.md](./04-frontend-deep-dive.md) | React components, hooks, API client, state machine |
| [05-how-aop-works.md](./05-how-aop-works.md) | Spring AOP pointcut, around advice, thread safety |
| [06-sse-protocol.md](./06-sse-protocol.md) | SSE event schema, buffering strategy, timeout handling |
| [07-sample-app-demo.md](./07-sample-app-demo.md) | Step-by-step demo using the checkout scenario |
| [08-configuration.md](./08-configuration.md) | All properties, enabling/disabling, production safety |
| [09-bugs-and-fixes.md](./09-bugs-and-fixes.md) | Every bug found during development and how it was fixed |
