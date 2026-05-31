# 04 — Flow Tracer: Frontend Deep Dive

## File Overview

```
agentic-docs-ui/src/
├── api/
│   └── flowApi.js          ← HTTP + SSE client functions
├── hooks/
│   └── useFlowTracer.js    ← state machine (idle → running → done|error)
└── components/
    ├── FlowTracer.jsx       ← full-page tab component
    └── FlowStepCard.jsx     ← one animated step card
```

---

## api/flowApi.js

Two exported functions — one for the HTTP call, one for the SSE stream.

```js
const BASE = '/agentic-docs/api/flow';

/**
 * Fire the execute call. Returns { traceId }.
 */
export async function executeFlow(request) {
  const res = await fetch(`${BASE}/execute`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  if (!res.ok) throw new Error(`Execute failed: ${res.status}`);
  return res.json();  // { traceId: "uuid" }
}

/**
 * Open an SSE stream for the given traceId.
 * Calls onStep(TraceEvent), onDone(FlowDoneEvent), or onError(message).
 * Returns a cleanup function — call it to close the EventSource.
 */
export function subscribeToTrace(traceId, onStep, onDone, onError) {
  const es = new EventSource(`${BASE}/trace/${traceId}`);

  es.addEventListener('step', (e) => {
    try { onStep(JSON.parse(e.data)); } catch (_) {}
  });

  es.addEventListener('done', (e) => {
    try { onDone(JSON.parse(e.data)); } catch (_) {}
    es.close();
  });

  es.addEventListener('error', (e) => {
    try { onError(JSON.parse(e.data)?.message ?? 'Unknown error'); } catch (_) {}
    es.close();
  });

  // Network-level SSE error (e.g. server down)
  es.onerror = () => {
    onError('SSE connection lost');
    es.close();
  };

  return () => es.close();  // cleanup
}
```

**Why `addEventListener` instead of `onmessage`?**
The Spring SSE events are named (`event: step`, `event: done`, `event: error`).
The browser's `EventSource.onmessage` only fires for *unnamed* events (i.e. events
with no `event:` field). Named events require `addEventListener('step', ...)`.

---

## hooks/useFlowTracer.js

A custom React hook that manages all state for the Flow Tracer page.

```js
export function useFlowTracer() {
  const [steps,         setSteps]         = useState([]);   // TraceEvent[]
  const [status,        setStatus]        = useState('idle'); // idle|running|done|error
  const [finalResponse, setFinalResponse] = useState(null);  // FlowDoneEvent
  const [errorMessage,  setErrorMessage]  = useState(null);

  const cleanupRef = useRef(null);  // SSE cleanup function

  const send = useCallback(async (request) => {
    // Reset previous run
    setSteps([]);
    setFinalResponse(null);
    setErrorMessage(null);
    setStatus('running');

    try {
      const { traceId } = await executeFlow(request);

      cleanupRef.current = subscribeToTrace(
        traceId,
        (step) => setSteps((prev) => [...prev, step]),         // add step
        (done) => { setFinalResponse(done); setStatus('done'); },
        (err)  => { setErrorMessage(err);   setStatus('error'); }
      );
    } catch (err) {
      setErrorMessage(err.message);
      setStatus('error');
    }
  }, []);

  const reset = useCallback(() => {
    cleanupRef.current?.();
    setSteps([]);
    setStatus('idle');
    setFinalResponse(null);
    setErrorMessage(null);
  }, []);

  return { steps, status, finalResponse, errorMessage, send, reset };
}
```

**State machine:**

```
     reset()
  ┌──────────────────────────────────────────────┐
  ↓                                              │
idle ──send()──→ running ──SSE done──→ done ────┘
                    │
                    └──SSE error──→ error ───────┘
```

---

## components/FlowStepCard.jsx

Renders one `TraceEvent` as an animated card with layer-coloured badge.

```jsx
const LAYER_STYLES = {
  CONTROLLER: 'bg-violet-600 text-white',
  SERVICE:    'bg-blue-600   text-white',
  REPOSITORY: 'bg-amber-500  text-white',
};

export function FlowStepCard({ step, index }) {
  return (
    <div className={`
      relative flex flex-col gap-2 rounded-xl border p-4 shadow-sm
      animate-fade-in-up
      ${step.status === 'ERROR' ? 'border-red-400 bg-red-50' : 'border-gray-200 bg-white'}
    `}>
      {/* Dashed connector line above every card except the first */}
      {index > 0 && (
        <div className="absolute -top-4 left-1/2 w-px h-4 border-l-2 border-dashed border-gray-300" />
      )}

      {/* Header row */}
      <div className="flex items-center gap-2">
        <span className={`rounded px-2 py-0.5 text-xs font-bold ${LAYER_STYLES[step.layer] ?? 'bg-gray-500 text-white'}`}>
          {step.layer}
        </span>
        <span className="font-mono text-sm font-semibold text-gray-800">
          {step.className}.{step.methodName}()
        </span>
        <span className="ml-auto text-xs text-gray-400">{step.durationMs}ms</span>
      </div>

      {/* Input */}
      {step.inputJson && (
        <CollapsibleCode label="Input" code={step.inputJson} />
      )}

      {/* Output or Error */}
      {step.status === 'EXIT' && step.outputJson && (
        <CollapsibleCode label="Output" code={step.outputJson} />
      )}
      {step.status === 'ERROR' && (
        <div className="rounded bg-red-100 p-2 text-xs text-red-700 font-mono">
          {step.errorMessage}
        </div>
      )}
    </div>
  );
}
```

**Layer badge colour guide:**

| Layer | Colour | Why |
|---|---|---|
| CONTROLLER | Violet | Outermost layer — user-facing |
| SERVICE | Blue | Business logic — the heart of the app |
| REPOSITORY | Amber | Data layer — persistence / external calls |
| UNKNOWN | Grey | Fallback for edge cases |

**`animate-fade-in-up`** is a Tailwind custom keyframe:
```css
@keyframes fade-in-up {
  from { opacity: 0; transform: translateY(12px); }
  to   { opacity: 1; transform: translateY(0); }
}
```
Each new step card slides in from below as the SSE event arrives.

---

## components/FlowTracer.jsx

The full-page tab component. Responsibilities:
1. Fetch all discoverable endpoints on mount (reuses `endpointsApi`)
2. Render an endpoint dropdown grouped by controller tag
3. Render path-parameter inputs (auto-detected from `{param}` in path)
4. Render a body textarea (shown for POST/PUT/PATCH)
5. Show live step cards as SSE events arrive
6. Show the final response card when `done` fires

### Endpoint Sorting

Steps are rendered in **call order**, not arrival order:

```jsx
{[...steps]
  .sort((a, b) => a.stepIndex - b.stepIndex)
  .map((step, i) => (
    <FlowStepCard key={step.stepIndex} step={step} index={i} />
  ))
}
```

> This sorting is essential because AOP events arrive in **completion order**
> (inner methods complete first), but `stepIndex` was assigned at **entry**
> (outer methods entered first). Sorting restores the logical call order.

### Path-Parameter Detection

```jsx
const pathParams = useMemo(() => {
  const matches = (selectedEndpoint?.path ?? '').matchAll(/\{(\w+)\}/g);
  return [...matches].map(m => m[1]);
}, [selectedEndpoint]);
```

For each detected parameter, an `<input>` field is rendered. The values are
collected into a `Map<String, String>` and passed in `FlowRequest.pathParams`.

### Request Dispatch

```jsx
function handleSend() {
  const request = {
    httpMethod: selectedEndpoint.method,
    path:       selectedEndpoint.path,
    pathParams: Object.fromEntries(paramValues),
    body:       bodyText || null,
  };
  send(request);  // from useFlowTracer
}
```

### Status Indicators

| Status | What the user sees |
|---|---|
| `idle` | "Send Request" button enabled; empty canvas |
| `running` | Spinner + "Tracing…" button disabled; step cards appear live |
| `done` | All step cards + green final response card |
| `error` | All step cards (if any) + red error banner |
