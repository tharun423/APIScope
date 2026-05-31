const BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const EXECUTE_URL = `${BASE}/apiscope/api/flow/execute`
const TRACE_URL   = (id) => `${BASE}/apiscope/api/flow/trace/${id}`

/**
 * Sends the flow execution request to the backend.
 * Returns { traceId } immediately — execution is async.
 *
 * @param {{ httpMethod: string, path: string, pathParams: Record<string,string>, body: string }} request
 * @returns {Promise<{ traceId: string }>}
 */
export async function executeFlow(request) {
  const token = localStorage.getItem('apiscope_bearer_token')
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = token.startsWith('Bearer ') ? token : `Bearer ${token}`

  const res = await fetch(EXECUTE_URL, {
    method:  'POST',
    headers,
    body:    JSON.stringify({
      ...request,
      authorizationHeader: token ? (token.startsWith('Bearer ') ? token : `Bearer ${token}`) : null,
    }),
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`Execute failed (${res.status}): ${text}`)
  }
  return res.json()
}

/**
 * Opens an EventSource SSE connection for the given traceId.
 *
 * @param {string}   traceId
 * @param {(event: object) => void} onStep   — called for each TraceEvent
 * @param {(event: object) => void} onDone   — called with FlowDoneEvent when complete
 * @param {(msg: string)  => void}  onError  — called on SSE error event or connection failure
 * @returns {() => void} cleanup — call to close the EventSource
 */
export function subscribeToTrace(traceId, onStep, onDone, onError) {
  const es = new EventSource(TRACE_URL(traceId))

  es.addEventListener('step', (e) => {
    try { onStep(JSON.parse(e.data)) } catch { onStep(e.data) }
  })

  es.addEventListener('done', (e) => {
    try { onDone(JSON.parse(e.data)) } catch { onDone(e.data) }
    es.close()
  })

  es.addEventListener('error', (e) => {
    try {
      const data = JSON.parse(e.data)
      onError(data.message ?? 'Unknown error')
    } catch {
      onError('SSE connection error')
    }
    es.close()
  })

  // onerror fires on network failure (readyState goes to CLOSED)
  // Only handle it when the stream was not already closed by a named error event
  es.onerror = () => {
    if (es.readyState === EventSource.CLOSED) return
    onError('Lost connection to the backend.')
    es.close()
  }

  return () => es.close()
}
