import { apiClient } from './client'
import { CHAT_URL, CHAT_STREAM_URL } from '../constants/apiUrls'

/**
 * Sends a user question to the AI chat backend (non-streaming, full response).
 *
 * @param {string} question
 * @returns {Promise<{ answer: string }>}
 */
export async function sendChatMessage(question) {
  const { data } = await apiClient(CHAT_URL, {
    method: 'POST',
    body:   JSON.stringify({ question }),
  })
  return data
}

/**
 * Sends a question and streams the response token-by-token via Server-Sent Events.
 * This dramatically reduces perceived latency with local Ollama models.
 *
 * @param {string}   question        - The user's question
 * @param {Function} onToken         - Called with each token string as it arrives
 * @param {Function} onDone          - Called when the stream completes
 * @param {Function} onError         - Called with an error message if the stream fails
 * @returns {Function} abort         - Call this function to cancel the stream
 */
export function sendChatMessageStream(question, onToken, onDone, onError) {
  const controller = new AbortController()

  fetch(CHAT_STREAM_URL, {
    method:  'POST',
    headers: { 'Content-Type': 'application/json' },
    body:    JSON.stringify({ question }),
    signal:  controller.signal,
  })
    .then((response) => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }
      const reader  = response.body.getReader()
      const decoder = new TextDecoder()

      function pump() {
        return reader.read().then(({ done, value }) => {
          if (done) {
            onDone()
            return
          }
          // SSE lines arrive as: "event: token\ndata: <text>\n\n"
          const chunk = decoder.decode(value, { stream: true })
          const lines = chunk.split('\n')
          let eventName = null
          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventName = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              const data = line.slice(5).trim()
              if (eventName === 'token') {
                onToken(data)
              } else if (eventName === 'done') {
                onDone()
                return
              } else if (eventName === 'error') {
                onError(data)
                return
              }
              eventName = null // reset after consuming data line
            }
          }
          return pump()
        })
      }

      return pump()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError(err.message)
      }
    })

  // Return an abort function so the caller can cancel mid-stream
  return () => controller.abort()
}
