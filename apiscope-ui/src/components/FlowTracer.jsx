import { useState, useMemo } from 'react'
import {
  Workflow, Play, RotateCcw, AlertCircle,
  CheckCircle2, XCircle, Loader2, Copy, Check,
  ChevronDown, Layers, Search, KeyRound,
} from 'lucide-react'
import FlowStepCard    from './FlowStepCard'
import MethodBadge     from './MethodBadge'
import { useEndpoints }   from '../hooks/useEndpoints'
import { useFlowTracer }  from '../hooks/useFlowTracer'
import { BODY_METHODS }   from '../api/tryItApi'

/** Copy-to-clipboard button for the final response panel. */
function CopyBtn({ text }) {
  const [copied, setCopied] = useState(false)
  const copy = () => {
    navigator.clipboard.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }
  return (
    <button onClick={copy} className="flex items-center gap-1 text-[10px] text-slate-500 hover:text-white px-2 py-0.5 rounded hover:bg-white/10 transition-colors">
      {copied ? <Check size={10} className="text-emerald-400" /> : <Copy size={10} />}
      {copied ? 'Copied' : 'Copy'}
    </button>
  )
}

export default function FlowTracer() {
  const { endpoints, loading: epLoading } = useEndpoints()
  const { steps, status, finalResponse, errorMessage, send, reset } = useFlowTracer()

  // Selected endpoint
  const [selectedPath,   setSelectedPath]   = useState('')
  const [selectedMethod, setSelectedMethod] = useState('')
  const [pathParams,     setPathParams]      = useState({})
  const [queryParams,    setQueryParams]     = useState({})
  const [body,           setBody]            = useState('')
  const [endpointSearch, setEndpointSearch] = useState('')
  const [token,          setToken]           = useState(() => localStorage.getItem('apiscope_bearer_token') ?? '')
  const [showToken,      setShowToken]       = useState(false)

  const handleTokenChange = (val) => {
    setToken(val)
    if (val) localStorage.setItem('apiscope_bearer_token', val)
    else     localStorage.removeItem('apiscope_bearer_token')
  }

  // Derive the selected endpoint object
  const selectedEndpoint = useMemo(
    () => endpoints.find((e) => e.path === selectedPath && e.httpMethod === selectedMethod),
    [endpoints, selectedPath, selectedMethod],
  )

  // Extract path-param names from the selected path
  const paramNames = useMemo(
    () => [...(selectedPath.matchAll(/\{(\w+)\}/g))].map((m) => m[1]),
    [selectedPath],
  )

  // Filter endpoints by search query
  const filteredEndpoints = useMemo(() => {
    const q = endpointSearch.toLowerCase().trim()
    if (!q) return endpoints
    return endpoints.filter((e) =>
      e.path.toLowerCase().includes(q) ||
      e.httpMethod.toLowerCase().includes(q) ||
      e.controllerName?.toLowerCase().includes(q) ||
      e.description?.toLowerCase().includes(q)
    )
  }, [endpoints, endpointSearch])

  const needsBody = BODY_METHODS.has(selectedMethod)

  // Group filtered endpoints by controller name for the <optgroup> select
  const groupedEndpoints = useMemo(() =>
    filteredEndpoints.reduce((acc, ep) => {
      const key = ep.controllerName || 'Other'
      if (!acc[key]) acc[key] = []
      acc[key].push(ep)
      return acc
    }, {})
  , [filteredEndpoints])

  const handleSelect = (e) => {
    const [method, ...pathParts] = e.target.value.split('|')
    const path = pathParts.join('|')
    setSelectedMethod(method)
    setSelectedPath(path)
    setPathParams({})
    setQueryParams({})
    setBody('')
  }

  const handleSend = () => {
    if (!selectedPath || !selectedMethod) return
    send({
      httpMethod:  selectedMethod,
      path:        selectedPath,
      pathParams,
      queryParams,
      body:        needsBody ? body : null,
    })
  }

  const isRunning = status === 'running'

  // Search within execution flow steps
  const [stepSearch, setStepSearch] = useState('')
  const visibleSteps = useMemo(() => {
    const sorted = [...steps].sort((a, b) => a.stepIndex - b.stepIndex)
    const q = stepSearch.toLowerCase().trim()
    if (!q) return sorted
    return sorted.filter((s) =>
      s.className?.toLowerCase().includes(q) ||
      s.methodName?.toLowerCase().includes(q) ||
      s.layer?.toLowerCase().includes(q)
    )
  }, [steps, stepSearch])

  // Status badge for final response
  const statusBadge = finalResponse
    ? finalResponse.ok
      ? { cls: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/30', icon: <CheckCircle2 size={12} /> }
      : { cls: 'text-red-400 bg-red-500/10 border-red-500/30',             icon: <XCircle size={12} /> }
    : null

  return (
    <div className="flex flex-col h-full overflow-hidden bg-[#0f1117]">
      {/* Page header */}
      <div className="shrink-0 flex items-center gap-3 px-6 py-4 border-b border-white/5 bg-[#13151f]/60 backdrop-blur-sm z-10">
        <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-violet-600/20 border border-violet-500/20">
          <Workflow size={15} className="text-violet-400" />
        </div>
        <div className="flex-1">
          <h2 className="text-sm font-semibold text-white">Flow Tracer</h2>
          <p className="text-[11px] text-slate-500">Real-time execution diagram — watch every method fire</p>
        </div>
        {(status === 'done' || status === 'error') && (
          <button
            onClick={() => { reset(); setStepSearch('') }}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:text-white border border-white/10 hover:border-white/20 transition-all"
          >
            <RotateCcw size={11} /> Reset
          </button>
        )}
      </div>

      <div className="flex flex-col flex-1 min-h-0 overflow-y-auto scrollbar-thin px-6 pt-6 pb-16 gap-6 max-w-5xl mx-auto w-full">

        <div className="rounded-xl border border-white/8 bg-[#13151f] overflow-hidden">
          <div className="flex items-center gap-2 px-4 py-3 border-b border-white/6 bg-[#0f1117]">
            <Layers size={12} className="text-violet-400" />
            <span className="text-[11px] font-semibold uppercase tracking-widest text-slate-400">Request</span>
          </div>

          <div className="p-4 flex flex-col gap-4">
            {/* Bearer token */}
            <div className="flex items-center gap-2">
              <button
                onClick={() => setShowToken((v) => !v)}
                title={token ? 'Bearer token set' : 'Set Bearer token for secured endpoints'}
                className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold border transition-all duration-150 ${
                  token
                    ? 'border-emerald-500/40 text-emerald-400 bg-emerald-500/10'
                    : 'border-white/10 text-slate-500 hover:text-white hover:border-white/20'
                }`}
              >
                <KeyRound size={11} />
                {token ? 'Token set' : 'Bearer token'}
              </button>
              {showToken && (
                <div className="flex items-center gap-2 flex-1">
                  <input
                    type="password"
                    value={token}
                    onChange={(e) => handleTokenChange(e.target.value)}
                    placeholder="Paste JWT / Bearer token — stored in localStorage"
                    className="flex-1 bg-[#1a1d2e] border border-white/8 rounded-lg px-3 py-1.5 text-xs text-emerald-300 font-mono focus:outline-none focus:border-emerald-500/50 transition-colors"
                  />
                  {token && (
                    <button onClick={() => handleTokenChange('')} className="text-slate-600 hover:text-red-400 text-xs transition-colors" title="Clear token">✕</button>
                  )}
                </div>
              )}
            </div>

            {/* Endpoint selector */}
            <div>
              <p className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold mb-1.5">Endpoint</p>

              {/* Search */}
              <div className="flex items-center gap-2 bg-[#1a1d2e] border border-white/10 rounded-xl px-3 py-2 mb-2 focus-within:border-violet-500/50 transition-colors">
                <Search size={12} className="text-slate-600 shrink-0" />
                <input
                  value={endpointSearch}
                  onChange={(e) => setEndpointSearch(e.target.value)}
                  placeholder="Filter endpoints…"
                  disabled={epLoading || isRunning}
                  className="bg-transparent text-slate-200 text-xs flex-1 outline-none placeholder-slate-600 disabled:opacity-50"
                />
                {endpointSearch && (
                  <button onClick={() => setEndpointSearch('')} className="text-slate-600 hover:text-white text-xs leading-none">×</button>
                )}
              </div>

              <div className="relative">
                <select
                  value={selectedPath ? `${selectedMethod}|${selectedPath}` : ''}
                  onChange={handleSelect}
                  disabled={epLoading || isRunning}
                  className="w-full appearance-none bg-[#1a1d2e] border border-white/10 rounded-xl px-3 py-2.5 text-xs text-slate-200 font-mono focus:outline-none focus:border-violet-500/60 transition-colors pr-8 disabled:opacity-50"
                >
                  <option value="" disabled>
                    {epLoading ? 'Loading endpoints…' : filteredEndpoints.length === 0 ? 'No matches' : 'Select an endpoint…'}
                  </option>
                  {Object.entries(groupedEndpoints).map(([controller, eps]) => (
                    <optgroup key={controller} label={controller}>
                      {eps.map((ep) => (
                        <option key={`${ep.httpMethod}|${ep.path}`} value={`${ep.httpMethod}|${ep.path}`}>
                          [{ep.httpMethod}] {ep.path}
                        </option>
                      ))}
                    </optgroup>
                  ))}
                </select>
                <ChevronDown size={13} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none" />
              </div>

              {endpointSearch && (
                <p className="text-[10px] text-slate-600 mt-1.5">
                  {filteredEndpoints.length} result{filteredEndpoints.length !== 1 ? 's' : ''} for &ldquo;{endpointSearch}&rdquo;
                </p>
              )}

              {/* Show method badge + description */}
              {selectedEndpoint && (
                <div className="mt-2 flex items-center gap-2">
                  <MethodBadge method={selectedEndpoint.httpMethod} />
                  <span className="text-[11px] text-slate-500 truncate">{selectedEndpoint.description}</span>
                </div>
              )}
            </div>

            {/* Path parameters */}
            {paramNames.length > 0 && (
              <div>
                <p className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold mb-1.5">Path Parameters</p>
                <div className="flex flex-wrap gap-3">
                  {paramNames.map((p) => (
                    <div key={p} className="flex items-center gap-2">
                      <label className="text-slate-400 text-xs font-mono">{`{${p}}`}</label>
                      <input
                        value={pathParams[p] || ''}
                        onChange={(e) => setPathParams((prev) => ({ ...prev, [p]: e.target.value }))}
                        placeholder={p}
                        disabled={isRunning}
                        className="bg-[#1a1d2e] border border-white/10 rounded-lg px-2.5 py-1.5 text-xs text-slate-200 font-mono focus:outline-none focus:border-violet-500/60 w-32 transition-colors disabled:opacity-50"
                      />
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Query parameters */}
            {(selectedEndpoint?.requiredQueryParams?.length ?? 0) > 0 && (
              <div>
                <p className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold mb-1.5">Query Parameters</p>
                <div className="flex flex-wrap gap-3">
                  {selectedEndpoint.requiredQueryParams.map((p) => (
                    <div key={p} className="flex items-center gap-2">
                      <label className="text-slate-400 text-xs font-mono">?{p}</label>
                      <input
                        value={queryParams[p] || ''}
                        onChange={(e) => setQueryParams((prev) => ({ ...prev, [p]: e.target.value }))}
                        placeholder={p}
                        disabled={isRunning}
                        className="bg-[#1a1d2e] border border-white/10 rounded-lg px-2.5 py-1.5 text-xs text-slate-200 font-mono focus:outline-none focus:border-sky-500/60 w-36 transition-colors disabled:opacity-50"
                      />
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Request body */}
            {needsBody && (
              <div>
                <p className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold mb-1.5">Request Body (JSON)</p>
                <textarea
                  value={body}
                  onChange={(e) => setBody(e.target.value)}
                  rows={4}
                  disabled={isRunning}
                  placeholder={'{\n  \n}'}
                  className="w-full bg-[#080a10] border border-white/8 rounded-lg px-3 py-2.5 text-xs text-emerald-300 font-mono focus:outline-none focus:border-violet-500/50 resize-y transition-colors disabled:opacity-50"
                />
              </div>
            )}

            {/* Send button */}
            <button
              onClick={handleSend}
              disabled={!selectedPath || isRunning}
              className="self-start flex items-center gap-2 px-4 py-2 bg-violet-600 hover:bg-violet-500 disabled:opacity-50 disabled:cursor-not-allowed text-white text-xs rounded-xl font-semibold transition-all shadow-sm shadow-violet-900/40"
            >
              {isRunning
                ? <><Loader2 size={12} className="animate-spin" /> Tracing…</>
                : <><Play size={12} /> Send &amp; Trace</>
              }
            </button>
          </div>
        </div>

        {(steps.length > 0 || isRunning) && (
          <div className="flex flex-col">
            <div className="flex items-center gap-2 mb-3">
              <div className="w-1 h-4 rounded-full bg-gradient-to-b from-violet-500 to-purple-700" />
              <h3 className="text-xs font-semibold text-white uppercase tracking-wide">Execution Flow</h3>
              <span className="text-[10px] text-slate-600 font-mono">{steps.length} step{steps.length !== 1 ? 's' : ''}</span>
              {isRunning && <Loader2 size={11} className="text-violet-400 animate-spin ml-1" />}
            </div>

            {/* Step search */}
            {steps.length > 1 && (
              <div className="flex items-center gap-2 bg-[#13151f] border border-white/8 rounded-xl px-3 py-2 mb-4 focus-within:border-violet-500/40 transition-colors">
                <Search size={12} className="text-slate-600 shrink-0" />
                <input
                  value={stepSearch}
                  onChange={(e) => setStepSearch(e.target.value)}
                  placeholder="Search steps by class, method, or layer…"
                  className="bg-transparent text-slate-200 text-xs flex-1 outline-none placeholder-slate-600"
                />
                {stepSearch && (
                  <button onClick={() => setStepSearch('')} className="text-slate-600 hover:text-white text-xs leading-none">×</button>
                )}
                {stepSearch && (
                  <span className="text-[10px] text-slate-600 shrink-0">
                    {visibleSteps.length}/{steps.length}
                  </span>
                )}
              </div>
            )}

            <div className="flex flex-col items-stretch">
              {visibleSteps.map((step, i, arr) => (
                <FlowStepCard key={`${step.traceId}-${step.stepIndex}`} step={step} isLast={i === arr.length - 1 && !isRunning} />
              ))}

              {stepSearch && visibleSteps.length === 0 && (
                <div className="flex flex-col items-center py-8 text-center">
                  <Search size={20} className="text-slate-700 mb-2" />
                  <p className="text-slate-500 text-xs">No steps match &ldquo;{stepSearch}&rdquo;</p>
                </div>
              )}

              {/* Live pulse while running after last card */}
              {isRunning && (
                <div className="flex flex-col items-center py-2">
                  <div className="w-px h-5 border-l-2 border-dashed border-violet-500/30" />
                  <div className="w-2 h-2 rounded-full bg-violet-500 animate-pulse" />
                </div>
              )}
            </div>
          </div>
        )}

        {status === 'error' && (
          <div className="rounded-xl border border-red-500/30 bg-[#1a0d0d] p-4 flex items-start gap-3">
            <AlertCircle size={16} className="text-red-400 shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-semibold text-red-300">Execution failed</p>
              <p className="text-xs text-red-400/80 mt-1">{errorMessage}</p>
            </div>
          </div>
        )}

        {finalResponse && (
          <div className="rounded-xl border border-white/8 bg-[#13151f] overflow-hidden">
            <div className="flex items-center gap-3 px-4 py-3 border-b border-white/6 bg-[#0f1117]">
              <span className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg border text-[11px] font-bold ${statusBadge.cls}`}>
                {statusBadge.icon}
                HTTP {finalResponse.httpStatus}
              </span>
              <span className="text-[10px] text-slate-600 font-mono">{finalResponse.totalMs}ms total · {finalResponse.stepCount} method{finalResponse.stepCount !== 1 ? 's' : ''} traced</span>
              <div className="ml-auto">
                <CopyBtn text={finalResponse.responseBody} />
              </div>
            </div>
            <pre className="bg-[#080a10] px-4 py-3 text-[11px] font-mono text-slate-300 overflow-x-auto whitespace-pre-wrap max-h-[60vh] overflow-y-auto leading-relaxed">
              {finalResponse.responseBody || '(empty body)'}
            </pre>
          </div>
        )}
      </div>
    </div>
  )
}
