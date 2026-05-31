import { useState } from 'react'
import { Copy, Check, ChevronDown, ChevronUp, AlertTriangle, Flame, Database } from 'lucide-react'

/** Layer badge colour map */
const LAYER_STYLES = {
  CONTROLLER: 'bg-violet-500/15 border-violet-500/30 text-violet-300',
  SERVICE:    'bg-blue-500/15   border-blue-500/30   text-blue-300',
  REPOSITORY: 'bg-amber-500/15  border-amber-500/30  text-amber-300',
  COMPONENT:  'bg-slate-500/15  border-slate-500/30  text-slate-300',
}

/** Thresholds in ms */
const HOT_MS  = 200
const WARN_MS = 100
const FAST_MS = 30

function CopyBtn({ text }) {
  const [copied, setCopied] = useState(false)
  const copy = () => {
    navigator.clipboard.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }
  return (
    <button onClick={copy} className="flex items-center gap-1 text-[10px] text-slate-500 hover:text-white transition-colors px-1.5 py-0.5 rounded hover:bg-white/10">
      {copied ? <Check size={9} className="text-emerald-400" /> : <Copy size={9} />}
      {copied ? 'Copied' : 'Copy'}
    </button>
  )
}

function prettyJson(value) {
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return JSON.stringify(parsed, null, 2)
  } catch {
    return value
  }
}

function JsonPanel({ label, value, accent }) {
  if (!value || value === 'null') return null
  const pretty = prettyJson(value)
  return (
    <div className="mt-2">
      <p className={`text-[10px] font-semibold uppercase tracking-widest mb-1 ${accent}`}>{label}</p>
      <div className="rounded-lg overflow-hidden border border-white/6">
        <div className="flex items-center justify-between bg-[#080a10] px-3 py-1.5 border-b border-white/6">
          <span className="text-[10px] text-slate-600 font-mono">json</span>
          <CopyBtn text={pretty} />
        </div>
        <pre className="bg-[#0a0c14] px-3 py-2.5 text-[11px] font-mono text-slate-300 overflow-x-auto whitespace-pre-wrap max-h-64 overflow-y-auto leading-relaxed">
          {pretty}
        </pre>
      </div>
    </div>
  )
}

export default function FlowStepCard({ step, isLast }) {
  const [expanded, setExpanded] = useState(true)

  const isError = step.status === 'ERROR'
  const isHot   = step.durationMs >= HOT_MS
  const layerCls = LAYER_STYLES[step.layer] ?? LAYER_STYLES.COMPONENT

  const durationColor = (
    step.durationMs < FAST_MS ? 'text-emerald-400' :
    step.durationMs < WARN_MS ? 'text-amber-400'   :
    step.durationMs < HOT_MS  ? 'text-orange-400'  :
                                'text-red-400'
  )

  const hasSql = step.sqlQueries?.length > 0

  return (
    <div className="flex flex-col items-center w-full">
      {/* Card */}
      <div className={`w-full rounded-xl border transition-all duration-300 animate-fadeIn overflow-hidden ${
        isError ? 'border-red-500/40 bg-[#1a0d0d]' :
        isHot   ? 'border-orange-500/35 bg-[#1a120d]' :
                  'border-white/8 bg-[#13151f]'
      }`}>
        {/* Header row */}
        <button
          onClick={() => setExpanded((v) => !v)}
          className="w-full flex items-center gap-3 px-4 py-3 text-left"
        >
          {/* Step index bubble */}
          <div className={`shrink-0 flex items-center justify-center w-6 h-6 rounded-full text-[10px] font-bold ${
            isError ? 'bg-red-500/20 text-red-400 border border-red-500/30' :
            isHot   ? 'bg-orange-500/20 text-orange-400 border border-orange-500/30' :
                      'bg-violet-500/20 text-violet-400 border border-violet-500/30'
          }`}>
            {step.stepIndex + 1}
          </div>

          {/* Layer badge */}
          <span className={`shrink-0 px-2 py-0.5 rounded-md border text-[10px] font-bold uppercase tracking-wide ${layerCls}`}>
            {step.layer}
          </span>

          {/* Class.method() */}
          <code className="flex-1 text-xs font-mono text-slate-200 break-all min-w-0">
            {step.className}<span className="text-slate-500">.</span>{step.methodName}<span className="text-slate-500">()</span>
          </code>

          {/* SQL badge */}
          {hasSql && (
            <span
              title={`${step.sqlQueries.length} SQL quer${step.sqlQueries.length === 1 ? 'y' : 'ies'} intercepted`}
              className="shrink-0 flex items-center gap-1 px-1.5 py-0.5 rounded-md bg-amber-500/10 border border-amber-500/20 text-amber-300 text-[10px] font-semibold"
            >
              <Database size={9} />
              {step.sqlQueries.length}
            </span>
          )}

          {/* Duration + hot flame */}
          <span className={`shrink-0 flex items-center gap-1 text-[10px] font-mono font-semibold ${durationColor}`}>
            {isHot && <Flame size={11} className="text-orange-400" />}
            {step.durationMs}ms
          </span>

          {/* Error icon */}
          {isError && <AlertTriangle size={13} className="shrink-0 text-red-400" />}

          {/* Expand toggle */}
          <div className="shrink-0 text-slate-600">
            {expanded ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
          </div>
        </button>

        {/* Hot-method banner */}
        {isHot && !isError && (
          <div className="flex items-center gap-2 px-4 py-1.5 bg-orange-500/8 border-t border-orange-500/15">
            <Flame size={11} className="text-orange-400 shrink-0" />
            <span className="text-[10px] text-orange-300 font-medium">
              Performance bottleneck — {step.durationMs}ms exceeds the 200ms threshold
            </span>
          </div>
        )}

        {/* Expanded body */}
        {expanded && (
          <div className="px-4 pb-4 border-t border-white/5">
            {isError ? (
              <div className="mt-3">
                <p className="text-[10px] font-semibold uppercase tracking-widest text-red-400 mb-1">Error</p>
                <pre className="bg-[#0f0808] border border-red-500/20 rounded-lg px-3 py-2.5 text-[11px] font-mono text-red-300 overflow-x-auto whitespace-pre-wrap max-h-72 overflow-y-auto leading-relaxed">
                  {step.errorMessage}
                </pre>
              </div>
            ) : (
              <>
                <JsonPanel label="Input"  value={step.inputJson}  accent="text-sky-400" />
                <JsonPanel label="Output" value={step.outputJson} accent="text-emerald-400" />
              </>
            )}

            {/* SQL Queries */}
            {hasSql && (
              <div className="mt-3">
                <div className="flex items-center gap-2 mb-1.5">
                  <Database size={10} className="text-amber-400" />
                  <p className="text-[10px] font-semibold uppercase tracking-widest text-amber-400">
                    SQL Queries ({step.sqlQueries.length})
                  </p>
                </div>
                <div className="flex flex-col gap-1.5">
                  {step.sqlQueries.map((sql, i) => (
                    <div key={i} className="rounded-lg overflow-hidden border border-amber-500/15">
                      <div className="flex items-center justify-between bg-[#080a10] px-3 py-1.5 border-b border-white/5">
                        <span className="text-[10px] text-slate-600 font-mono">query {i + 1}</span>
                        <CopyBtn text={sql} />
                      </div>
                      <pre className="bg-[#0d0e18] px-3 py-2.5 text-[11px] font-mono text-amber-200/80 overflow-x-auto whitespace-pre-wrap leading-relaxed">
                        {sql}
                      </pre>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Dashed connector line to next step */}
      {!isLast && (
        <div className="flex flex-col items-center py-1">
          <div className="w-px h-5 border-l-2 border-dashed border-white/15" />
          <div className="w-1.5 h-1.5 rounded-full bg-white/15" />
          <div className="w-px h-5 border-l-2 border-dashed border-white/15" />
        </div>
      )}
    </div>
  )
}
