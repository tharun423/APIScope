import { useState, useEffect } from 'react'
import { fetchEndpoints } from '../api/endpointsApi'

/**
 * Fetches the list of REST endpoints from the Agentic Docs backend on mount.
 *
 * Data fetching is delegated to {@link fetchEndpoints} (src/api/endpointsApi.js)
 * so this hook only owns UI state, not transport logic.
 *
 * @returns {{ endpoints: Array<object>, loading: boolean, error: string | null }}
 */
export function useEndpoints() {
  const [endpoints, setEndpoints] = useState([])
  const [loading,   setLoading]   = useState(true)
  const [error,     setError]     = useState(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const data = await fetchEndpoints()
        if (!cancelled) { setEndpoints(data); setLoading(false) }
      } catch (err) {
        if (!cancelled) { setError(err.message); setLoading(false) }
      }
    }

    load()
    return () => { cancelled = true }
  }, [])

  return { endpoints, loading, error }
}
