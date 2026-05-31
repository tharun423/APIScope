import { useState } from 'react'
import { Play, Loader2, CheckCircle2, XCircle } from 'lucide-react'
import { useTryIt }    from '../hooks/useTryIt'
import { BODY_METHODS } from '../api/tryItApi'

export default function TryItPanel({ endpoint, token }) {
  const { body, setBody, pathParams, setPathParam, queryParams, setQueryParam, response, loading, execute } = useTryIt(endpoint, token)
  const [showOptional, setShowOptional] = useState(false)

  const paramNames         = [...(endpoint.path.matchAll(/\{(\w+)\}/g))].map((m) => m[1])
  const requiredParams     = endpoint.requiredQueryParams  ?? []
  const optionalParams     = endpoint.optionalQueryParams ?? []
  const allQueryParamNames = [...requiredParams, ...optionalParams]

  const previewUrl = (() => {
    let url = paramNames.reduce(
      (u, p) => u.replace(`{${p}}`, pathParams[p] || `{${p}}`),
      endpoint.path,
    )
    const qs = new URLSearchParams(
      allQueryParamNames
        .filter((p) => queryParams[p])
        .map((p) => [p, queryParams[p]])
    ).toString()
    return qs ? `${url}?${qs}` : url
  })()

  const isOk = response?.ok

  return (
    <div className="mt-4 rounded-xl overflow-hidden border border-white/8">
      {/* Path parameters */}
      {paramNames.length > 0 && (
        <div className="bg-[#0f1117] px-4 py-3 border-b border-white/8">
          <p className="text-[10px] text-slate-600 font-semibold uppercase tracking-widest mb-2">Path Parameters</p>
          <div className="flex flex-wrap gap-3">
            {paramNames.map((p) => (
              <div key={p} className="flex items-center gap-2">
                <label className="text-slate-400 text-xs font-mono">{`{${p}}`}</label>
                <input
                  value={pathParams[p] || ''}
                  onChange={(e) => setPathParam(p, e.target.value)}
                  placeholder={p}
                  className="bg-[#1a1d2e] border border-white/10 rounded-lg px-2.5 py-1.5 text-xs text-slate-200 font-mono focus:outline-none focus:border-violet-500/60 w-32 transition-colors"
                />
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Required query parameters — shown as inputs */}
      {requiredParams.length > 0 && (
        <div className="bg-[#0f1117] px-4 py-3 border-b border-white/8">
          <p className="text-[10px] text-slate-600 font-semibold uppercase tracking-widest mb-2">
            Query Parameters <span className="text-red-400 ml-1">* required</span>
          </p>
          <div className="flex flex-wrap gap-3">
            {requiredParams.map((p) => (
              <div key={p} className="flex items-center gap-2">
                <label className="text-slate-400 text-xs font-mono">?{p}</label>
                <input
                  value={queryParams[p] || ''}
                  onChange={(e) => setQueryParam(p, e.target.value)}
                  placeholder={p}
                  className="bg-[#1a1d2e] border border-white/10 rounded-lg px-2.5 py-1.5 text-xs text-slate-200 font-mono focus:outline-none focus:border-sky-500/60 w-36 transition-colors"
                />
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Optional query parameters — collapsed by default */}
      {optionalParams.length > 0 && (
        <div className="bg-[#0f1117] px-4 py-3 border-b border-white/8">
          <button
            onClick={() => setShowOptional((v) => !v)}
            className="flex items-center gap-2 text-[10px] text-slate-600 font-semibold uppercase tracking-widest hover:text-slate-400 transition-colors"
          >
            <span>{showOptional ? '▾' : '▸'}</span>
            Optional Parameters
            <span className="text-slate-700 normal-case tracking-normal font-normal">
              ({optionalParams.join(', ')})
            </span>
          </button>
          {showOptional && (
            <div className="flex flex-wrap gap-3 mt-2">
              {optionalParams.map((p) => (
                <div key={p} className="flex items-center gap-2">
                  <label className="text-slate-500 text-xs font-mono">?{p}</label>
                  <input
                    value={queryParams[p] || ''}
                    onChange={(e) => setQueryParam(p, e.target.value)}
                    placeholder={`${p} (optional)`}
                    className="bg-[#1a1d2e] border border-white/10 rounded-lg px-2.5 py-1.5 text-xs text-slate-400 font-mono focus:outline-none focus:border-sky-500/40 w-40 transition-colors"
                  />
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Request body */}
      {BODY_METHODS.has(endpoint.httpMethod) && (
        <div className="bg-[#0f1117] px-4 py-3 border-b border-white/8">
          <p className="text-[10px] text-slate-600 font-semibold uppercase tracking-widest mb-2">Request Body (JSON)</p>
          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            rows={4}
            placeholder={'{\n  \n}'}
            className="w-full bg-[#080a10] border border-white/8 rounded-lg px-3 py-2.5 text-xs text-emerald-300 font-mono focus:outline-none focus:border-violet-500/50 resize-y transition-colors"
          />
        </div>
      )}

      {/* Execute row */}
      <div className="bg-[#0f1117] px-4 py-3 flex items-center gap-3 border-b border-white/8">
        <code className="text-slate-500 text-xs font-mono flex-1 truncate">{previewUrl}</code>
        <button
          onClick={execute}
          disabled={loading}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-violet-600 hover:bg-violet-500 disabled:opacity-50 text-white text-xs rounded-lg font-semibold transition-all shadow-sm shadow-violet-900/40"
        >
          {loading ? <Loader2 size={11} className="animate-spin" /> : <Play size={11} />}
          Execute
        </button>
      </div>

      {/* Response */}
      {response && (
        <div className="bg-[#080a10] px-4 py-3">
          <div className="flex items-center gap-2 mb-2">
            {isOk
              ? <CheckCircle2 size={12} className="text-emerald-400" />
              : <XCircle      size={12} className="text-red-400" />}
            <p className="text-[10px] font-semibold uppercase tracking-widest text-slate-600">
              Response — <span className={isOk ? 'text-emerald-400' : 'text-red-400'}>{response.status || 'Error'}</span>
            </p>
          </div>
          <pre className="text-xs font-mono text-slate-300 overflow-x-auto whitespace-pre-wrap max-h-60 overflow-y-auto leading-relaxed">
            {response.body}
          </pre>
        </div>
      )}
    </div>
  )
}
