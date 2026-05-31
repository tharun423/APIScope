/**
 * Fires a single HTTP request to every discovered endpoint so that
 * Micrometer registers at least one data-point for each — making the
 * MetricsBadges show real values instead of "No metrics".
 *
 * Strategy per HTTP method:
 *  GET / DELETE  → hit the path directly (path-params replaced with "_")
 *  POST / PUT / PATCH → send an empty JSON object {}
 *
 * Errors are swallowed intentionally — a 400/404 still registers a data-point.
 */

const PLACEHOLDER = '_'

function buildUrl(path) {
  // Replace every {param} token with a dummy value so the URL is valid
  return path.replace(/\{[^}]+\}/g, PLACEHOLDER)
}

/**
 * @param {Array<{ path: string, httpMethod: string }>} endpoints
 * @param {(done: number, total: number) => void} onProgress
 * @returns {Promise<{ ok: number, failed: number }>}
 */
export async function warmupAllEndpoints(endpoints, onProgress) {
  let ok = 0
  let failed = 0
  const total = endpoints.length

  await Promise.allSettled(
    endpoints.map(async (ep) => {
      const url    = buildUrl(ep.path)
      const method = ep.httpMethod.toUpperCase()
      const needsBody = ['POST', 'PUT', 'PATCH'].includes(method)

      try {
        await fetch(url, {
          method,
          headers: { 'Content-Type': 'application/json' },
          body: needsBody ? '{}' : undefined,
          // Short timeout — we only care that the request reaches the server
          signal: AbortSignal.timeout(5000),
        })
        ok++
      } catch {
        // Network error / timeout — still count as "attempted"
        failed++
      } finally {
        onProgress(ok + failed, total)
      }
    }),
  )

  return { ok, failed }
}
