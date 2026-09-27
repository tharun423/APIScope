import { useState } from 'react'

const STORAGE_KEY = 'apiscope_bearer_token'

export function useBearerToken() {
  const [token, setToken] = useState(() => localStorage.getItem(STORAGE_KEY) ?? '')

  const updateToken = (val) => {
    setToken(val)
    if (val) localStorage.setItem(STORAGE_KEY, val)
    else     localStorage.removeItem(STORAGE_KEY)
  }

  return { token, updateToken }
}
