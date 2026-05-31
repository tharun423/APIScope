# 04 — Frontend Deep Dive

## Tech Stack

| Tool | Version | Role |
|---|---|---|
| React | 18.3.1 | UI framework |
| Vite | 5.4.10 | Build tool + dev server |
| Tailwind CSS | 4.1.11 | Utility-first styling |
| Lucide React | 0.441.0 | Icon library |
| React Markdown | 9.0.1 | Renders LLM markdown responses |

---

## Component Tree

```
App
├── Header
│   └── Logo + "New chat" button
├── main
│   ├── SuggestionChips  (shown only when messages.length === 1)
│   │   └── 6 × suggestion button
│   └── messages list  (shown after first message)
│       ├── MessageBubble (user)
│       │   └── plain text
│       ├── MessageBubble (assistant)
│       │   └── ReactMarkdown
│       │       ├── code (inline) → styled <code>
│       │       ├── code (block) → header bar + <pre>
│       │       ├── p, ul, ol, strong → styled elements
│       └── TypingIndicator (shown while loading)
└── InputBar
    ├── auto-resize <textarea>
    └── Send <button>

ApiExplorer (tab)
└── EndpointRow × N
    ├── Summary row  (always visible)
    │   ├── MethodBadge
    │   ├── path <code>
    │   └── ChevronDown / ChevronUp
    └── Expanded panel  (on click)
        ├── Metadata grid  (Controller · Method name)
        ├── Description  (if present)
        ├── Inputs & Outputs panel  ← NEW
        │   ├── Path Params   — amber badges  e.g. {id}
        │   ├── Query Params  — sky badges    e.g. ?page
        │   ├── Request Body  — emerald badge e.g. CreateUserRequest
        │   └── Response      — violet badge  e.g. UserResponse
        ├── [Try it out] button → TryItPanel
        └── [Ask AI] button
```

---

## State Management

All state lives in the root `App` component. No external state library is needed.

```javascript
const [messages, setMessages] = useState(INITIAL_MESSAGES)
const [loading, setLoading] = useState(false)
```

`messages` is an array of `{ role: 'user' | 'assistant', content: string }` objects — the same shape as the OpenAI messages API. This makes it trivial to extend to a multi-turn conversation API in the future.

`loading` drives three UI states simultaneously:
- The `TypingIndicator` component visibility
- The `InputBar` textarea `disabled` state
- The send button `disabled` state

---

## The `sendMessage` Function & Streaming

The chat now uses **Server-Sent Events (SSE) streaming** via `POST /agentic-docs/api/chat/stream`. The `useChat` hook manages this:

```javascript
const sendMessage = useCallback((question) => {
    setMessages((prev) => [...prev, { role: 'user', content: question }])
    setLoading(true)

    // Pre-insert empty assistant placeholder — filled token by token
    setMessages((prev) => [...prev, { role: 'assistant', content: '' }])

    sendChatMessageStream(
        question,
        // onToken — append each token to the last message
        (token) => {
            setMessages((prev) => {
                const updated = [...prev]
                const last    = updated[updated.length - 1]
                updated[updated.length - 1] = { ...last, content: last.content + token }
                return updated
            })
        },
        // onDone
        () => setLoading(false),
        // onError
        (errMsg) => {
            setLoading(false)
            setMessages((prev) => { /* set error content on last msg */ })
        }
    )
}, [])
```

**Why streaming?** With a local Ollama model, the full response can take 10–30 seconds to generate. Streaming delivers the first token within ~1 second, so the user sees text appearing progressively rather than waiting for a single large response.

**Pre-inserted placeholder** — an empty assistant message is added immediately when the question is sent. Each token callback appends to it. This pattern avoids flicker and keeps the scroll position stable.

**Typing indicator logic** — `<TypingIndicator />` is only shown while `loading` is `true` AND the assistant placeholder is still empty. Once the first token arrives, the indicator disappears and the text itself provides the progress signal:

```javascript
const waitingForFirstToken = loading && lastMsg?.role === 'assistant' && !lastMsg?.content
```

**Why `useCallback`?** `sendMessage` is passed as a prop to both `SuggestionChips` and `InputBar`. Without `useCallback`, a new function reference is created on every render, causing unnecessary re-renders of child components.

**Error as a message** — network errors are displayed as assistant messages in the chat rather than alert dialogs or toast notifications. This keeps the user in context and provides actionable information.

### `sendChatMessageStream()` — API layer

Defined in `src/api/chatApi.js`. Uses the browser's `fetch()` API with a `ReadableStream` reader and `TextDecoder` to parse SSE lines incrementally:

```javascript
export function sendChatMessageStream(question, onToken, onDone, onError) {
    const controller = new AbortController()
    fetch(CHAT_STREAM_URL, { method: 'POST', ... signal: controller.signal })
        .then(response => { /* pump ReadableStream, parse SSE lines */ })
    return () => controller.abort() // returns abort function
}
```

The original blocking `sendChatMessage()` is still exported for tests or custom implementations.

---

## `SuggestionChips` — First-Load Experience

```javascript
const showSuggestions = messages.length === 1
```

When the chat has only the initial greeting message, the suggestion chips are shown instead of the message list. This solves the "blank page problem" — new users don't know what to ask. The 6 pre-written suggestions demonstrate the agent's capabilities immediately.

Clicking a chip calls `sendMessage(suggestion)` directly, bypassing the input bar.

---

## `MessageBubble` — Markdown Rendering

The assistant's responses are rendered through `ReactMarkdown` with custom component overrides:

```javascript
code({ inline, children, ...props }) {
    return inline
        ? <code className="bg-slate-900 text-violet-300 ...">...</code>
        : (
            <div>
                <div className="header bar with Code2 icon">...</div>
                <pre className="bg-slate-950 ...">
                    <code className="text-green-300 font-mono">...</code>
                </pre>
            </div>
        )
}
```

**Why custom code rendering?** The default `ReactMarkdown` code block has no styling. The custom renderer adds:
- A header bar with a `Code2` icon (visual affordance that it's code)
- Syntax-highlighted green text on a near-black background
- Horizontal scroll for long lines

This is critical because the LLM frequently returns Java code blocks — they need to be readable.

---

## `InputBar` — Auto-Resize Textarea

```javascript
useEffect(() => {
    const el = textareaRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 140) + 'px'
}, [value])
```

The textarea starts at 1 row and grows as the user types, up to a maximum of 140px (approximately 5 lines). This is the standard pattern used by ChatGPT, Claude, and other chat interfaces. It avoids the jarring experience of a fixed-height input that requires scrolling for multi-line questions.

**Enter to send, Shift+Enter for newline** — standard chat keyboard behavior. The hint is shown below the input bar.

---

## `EndpointRow` — Inputs & Outputs Panel

When an `EndpointRow` is expanded, the component renders a conditional `Inputs & Outputs` panel:

```jsx
{(
  (endpoint.pathParams?.length > 0) ||
  (endpoint.queryParams?.length > 0) ||
  endpoint.requestBodyType ||
  endpoint.responseType
) && (
  <div className="mt-3 rounded-xl border border-white/8 overflow-hidden">
    {/* Path Params — amber */}
    {/* Query Params — sky */}
    {/* Request Body — emerald */}
    {/* Response    — violet */}
  </div>
)}
```

**Why conditional rendering?**  
Endpoints like `GET /api/health` have no params and return `void`. Rendering an empty "Inputs & Outputs" box for those would add visual noise. The panel only renders when there is something meaningful to show.

**Color semantics:**

| Color | Token | Meaning |
|---|---|---|
| 🟡 Amber | `bg-amber-500/10 text-amber-300` | Path variable — part of the URL itself |
| 🔵 Sky | `bg-sky-500/10 text-sky-300` | Query string — optional filter/pagination |
| 🟢 Emerald | `bg-emerald-500/10 text-emerald-300` | Request body — structured JSON input |
| 🟣 Violet | `bg-violet-500/10 text-violet-300` | Response — what the caller receives back |

The four colors map to four distinct roles in an HTTP contract. A developer scanning 20 endpoints can identify the input/output shape at a glance without reading any code.

---

## Build Pipeline

```
agentic-docs-ui/          (npm source)
        │
        │  npm run build
        ▼
agentic-docs-spring-boot-starter/
  src/main/resources/static/agentic-docs/
        ├── index.html
        └── assets/
            ├── index-[hash].js
            └── index-[hash].css
```

The Vite `build.outDir` is configured to point directly into the starter's `static/agentic-docs/` folder. When Maven packages the starter JAR, these files are included as classpath resources. Spring Boot's `ResourceHttpRequestHandler` automatically serves them at `/agentic-docs/**`.

**Why not a separate CDN or npm package?**  
The goal is zero-infrastructure. A developer adding the starter dependency gets the UI for free — no separate deployment, no CDN configuration, no npm install in production.

### Vite `base: '/agentic-docs/'`

This tells Vite to prefix all asset URLs with `/agentic-docs/`. Without this, the built `index.html` would reference `/assets/index.js` which would 404 when served from `/agentic-docs/`. With it, the reference becomes `/agentic-docs/assets/index.js` which resolves correctly.

### Dev Proxy

```javascript
proxy: {
    '/agentic-docs/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
    },
},
```

During development (`npm run dev`), the Vite dev server runs on port 5173. API calls to `/agentic-docs/api/chat` are proxied to the Spring Boot app on port 8080. This avoids CORS issues and mirrors the production setup where both are served from the same origin.

---

## Dark Theme Design

The UI uses a dark slate color palette:

| Token | Color | Usage |
|---|---|---|
| `slate-900` | `#0f172a` | App background, header, input bar |
| `slate-800` | `#1e293b` | Message bubbles (assistant), input field |
| `slate-700` | `#334155` | Borders, avatar backgrounds |
| `violet-600` | `#7c3aed` | Primary accent — logo, user bubbles, send button |
| `violet-400` | `#a78bfa` | Secondary accent — bot avatar, suggestion arrows |
| `green-300` | `#86efac` | Code syntax highlighting |

The violet accent was chosen to be distinct from the typical blue (GitHub, Jira) and green (success states) used in developer tools, giving Agentic Docs a recognizable visual identity.
