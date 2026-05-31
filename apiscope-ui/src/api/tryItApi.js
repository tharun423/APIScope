import { apiClient } from './client'

/** HTTP methods that may carry a request body. */
export const BODY_METHODS = new Set(['POST', 'PUT', 'PATCH'])

/**
 * Executes a live HTTP request against the target API endpoint.
 *
 * @param {{
 *   path:        string,
 *   httpMethod:  string,
 *   pathParams:  Record<string, string>,
 *   queryParams: Record<string, string>,
 *   body:        string,
 * }} params
 * @returns {Promise<{ status: number, ok: boolean, body: string }>}
 */
export async function executeTryIt({ path, httpMethod, pathParams, queryParams, body, token }) {
  // Substitute path parameters into the URL template
  let url = Object.entries(pathParams).reduce(
    (acc, [key, value]) => acc.replace(`{${key}}`, value || key),
    path,
  )

  // Append query parameters
  if (queryParams && Object.keys(queryParams).length > 0) {
    const qs = new URLSearchParams(
      Object.entries(queryParams).filter(([, v]) => v !== '' && v != null)
    ).toString()
    if (qs) url = `${url}?${qs}`
  }

  const options = { method: httpMethod }

  if (BODY_METHODS.has(httpMethod) && body.trim()) {
    options.body = body
  }

  const headers = { 'Content-Type': 'application/json' }
  if (token && token.trim()) {
    headers['Authorization'] = `Bearer ${token.trim()}`
  }

  try {
    const response = await fetch(url, {
      ...options,
      headers,
    })
    const text = await response.text()
    let pretty
    try { pretty = JSON.stringify(JSON.parse(text), null, 2) } catch { pretty = text }
    return { status: response.status, ok: response.ok, body: pretty }
  } catch (err) {
    return { status: 0, ok: false, body: err.message }
  }
}
