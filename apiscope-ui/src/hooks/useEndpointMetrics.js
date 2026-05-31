import { useState, useEffect } from 'react'
import { ENDPOINT_METRICS_URL } from '../constants/apiUrls'

const POLL_INTERVAL = 30_000  // refresh every 30 s

/**
 * Fetches live metrics for one endpoint from the Agentic Docs actuator proxy.
 * Returns { avgResponseMs, successRate, totalRequests, available, ready }.
 *
 * - available: false  -> actuator not present or endpoint never hit yet
 * - ready: false      -> first fetch not yet complete (show skeleton)
 */
export function useEndpointMetrics(path, method) {
  const [metrics, setMetrics] = useState({
    avgResponseMs:  null,
    successRate:    null,
    totalRequests:  null,
    available:      false,
    ready:          false,
  })

  useEffect(() => {
    let cancelled = false

    async function fetchMetrics() {
      try {
        const url = `${ENDPOINT_METRICS_URL}?uri=${encodeURIComponent(path)}&method=${encodeURIComponent(method)}`
        const res  = await fetch(url)
        if (!res.ok || cancelled) return
        const data = await res.json()
        if (!cancelled) setMetrics({ ...data, ready: true })
      } catch {
        if (!cancelled) setMetrics(m => ({ ...m, available: false, ready: true }))
      }
    }

    fetchMetrics()
    const id = setInterval(fetchMetrics, POLL_INTERVAL)
    return () => { cancelled = true; clearInterval(id) }
  }, [path, method])

  return metrics
}
