import { useState, useCallback, useRef, useEffect } from 'react'
import { executeFlow, subscribeToTrace } from '../api/flowApi'

/**
 * State machine for the Flow Tracer page.
 *
 * States: idle → running → done | error
 *
 * @returns {{
 *   steps:         Array<object>,
 *   status:        'idle'|'running'|'done'|'error',
 *   finalResponse: object|null,
 *   errorMessage:  string|null,
 *   send:          (request: object) => void,
 *   reset:         () => void,
 * }}
 */
export function useFlowTracer() {
  const [steps,         setSteps]         = useState([])
  const [status,        setStatus]        = useState('idle')
  const [finalResponse, setFinalResponse] = useState(null)
  const [errorMessage,  setErrorMessage]  = useState(null)

  // Holds the EventSource cleanup fn so we can close it on unmount / reset
  const cleanupRef = useRef(null)

  // Close any open EventSource when the component unmounts
  useEffect(() => {
    return () => { cleanupRef.current?.() }
  }, [])

  const reset = useCallback(() => {
    cleanupRef.current?.()
    setSteps([])
    setStatus('idle')
    setFinalResponse(null)
    setErrorMessage(null)
  }, [])

  const send = useCallback(async (request) => {
    cleanupRef.current?.()
    setSteps([])
    setFinalResponse(null)
    setErrorMessage(null)
    setStatus('running')

    try {
      const { traceId } = await executeFlow(request)

      const cleanup = subscribeToTrace(
        traceId,
        // onStep — new TraceEvent arrived
        (event) => setSteps((prev) => [...prev, event]),
        // onDone — FlowDoneEvent
        (event) => {
          setFinalResponse(event)
          setStatus('done')
          cleanupRef.current = null
        },
        // onError
        (msg) => {
          setErrorMessage(msg)
          setStatus('error')
          cleanupRef.current = null
        },
      )

      cleanupRef.current = cleanup

    } catch (err) {
      setErrorMessage(err.message)
      setStatus('error')
    }
  }, [])

  return { steps, status, finalResponse, errorMessage, send, reset }
}
