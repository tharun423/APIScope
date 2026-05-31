/**
 * Base URLs for the APIScope backend API.
 *
 * Override the base URL at build time by setting VITE_API_BASE_URL in your
 * environment file (see .env.example). Defaults to '' so that the Vite dev
 * server proxy handles all /apiscope/api/* requests transparently.
 */
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export const CHAT_URL            = `${BASE_URL}/apiscope/api/chat`
export const CHAT_STREAM_URL     = `${BASE_URL}/apiscope/api/chat/stream`
export const ENDPOINTS_URL       = `${BASE_URL}/apiscope/api/endpoints`
export const ENDPOINT_METRICS_URL = `${BASE_URL}/apiscope/api/endpoint-metrics`
export const REINDEX_URL         = `${BASE_URL}/apiscope/api/admin/reindex`
