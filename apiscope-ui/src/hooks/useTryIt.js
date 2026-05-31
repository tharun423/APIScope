import { useState, useCallback } from 'react'
import { executeTryIt } from '../api/tryItApi'

/**
 * Manages the state for the "Try it out" panel inside {@link TryItPanel}.
 *
 * Keeps all HTTP execution logic out of the component so the component only
 * handles rendering. The actual fetch is delegated to {@link executeTryIt}
 * (src/api/tryItApi.js).
 *
 * @param {{ path: string, httpMethod: string, requiredQueryParams: string[], optionalQueryParams: string[] }} endpoint
 * @returns {{
 *   body:           string,
 *   setBody:        React.Dispatch<React.SetStateAction<string>>,
 *   pathParams:     Record<string, string>,
 *   setPathParam:   (name: string, value: string) => void,
 *   queryParams:    Record<string, string>,
 *   setQueryParam:  (name: string, value: string) => void,
 *   response:       { status: number, ok: boolean, body: string } | null,
 *   loading:        boolean,
 *   execute:        () => Promise<void>,
 * }}
 */
export function useTryIt(endpoint, token) {
  const [body,        setBody]       = useState('')
  const [pathParams,  setParams]     = useState({})
  const [queryParams, setQueryState] = useState({})
  const [response,    setResponse]   = useState(null)
  const [loading,     setLoading]    = useState(false)

  const setPathParam = useCallback((name, value) => {
    setParams((prev) => ({ ...prev, [name]: value }))
  }, [])

  const setQueryParam = useCallback((name, value) => {
    setQueryState((prev) => ({ ...prev, [name]: value }))
  }, [])

  const execute = useCallback(async () => {
    setLoading(true)
    setResponse(null)
    const result = await executeTryIt({
      path:       endpoint.path,
      httpMethod: endpoint.httpMethod,
      pathParams,
      queryParams,
      body,
      token,
    })
    setResponse(result)
    setLoading(false)
  }, [endpoint.path, endpoint.httpMethod, pathParams, queryParams, body, token])

  return { body, setBody, pathParams, setPathParam, queryParams, setQueryParam, response, loading, execute }
}
