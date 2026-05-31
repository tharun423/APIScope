import { apiClient }    from './client'
import { ENDPOINTS_URL } from '../constants/apiUrls'

/**
 * Fetches the list of REST endpoints discovered by the Agentic Docs backend.
 *
 * @returns {Promise<Array<object>>}
 */
export async function fetchEndpoints() {
  const { data } = await apiClient(ENDPOINTS_URL)
  return data
}
