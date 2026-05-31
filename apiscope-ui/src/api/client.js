/**
 * Thin fetch wrapper used by all API service modules.
 *
 * Centralising fetch here means:
 *  - Auth headers / tokens can be added in one place later.
 *  - Error handling is consistent across every API call.
 *  - Tests can mock this single module instead of the global fetch.
 *
 * @param {string} url
 * @param {RequestInit} [options]
 * @returns {Promise<{ data: any, status: number, ok: boolean }>}
 */
export async function apiClient(url, options = {}) {
  const defaultHeaders = { 'Content-Type': 'application/json' }

  const response = await fetch(url, {
    ...options,
    headers: { ...defaultHeaders, ...options.headers },
  })

  const text = await response.text()

  let data
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = text
  }

  if (!response.ok) {
    const error = new Error(`HTTP ${response.status}`)
    error.status = response.status
    error.data   = data
    throw error
  }

  return { data, status: response.status, ok: response.ok }
}
