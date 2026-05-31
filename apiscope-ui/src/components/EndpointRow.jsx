import { useState } from 'react'
import { ChevronDown, ChevronUp, Play, Bot, Tag, Cpu, ArrowRight, Package, CornerDownRight } from 'lucide-react'
import MethodBadge    from './MethodBadge'
import TryItPanel     from './TryItPanel'
import MetricsBadges  from './MetricsBadges'
import { methodColor }           from '../constants/methodColors'
import { buildEndpointAiPrompt } from '../constants/messages'

export default function EndpointRow({ endpoint, onAskAI, token }) {
  const [expanded, setExpanded] = useState(false)
  const [tryIt,    setTryIt]    = useState(false)
  const c = methodColor(endpoint.httpMethod)

  return (
    <div className={`rounded-xl overflow-hidden border transition-all duration-200 ${
      expanded
        ? 'border-violet-500/30 bg-[#1a1d2e] shadow-sm shadow-violet-900/10'
        : 'border-white/6 bg-[#13151f] hover:border-white/12 hover:bg-[#1a1d2e]/60'
    }`}>
      {/* Summary row */}
      <button
        onClick={() => setExpanded((v) => !v)}
        className="w-full flex items-center gap-3 px-4 py-3 text-left transition-colors"
      >
        <MethodBadge method={endpoint.httpMethod} />
        <code className="text-slate-300 text-xs font-mono flex-1 truncate tracking-wide">{endpoint.path}</code>
        <MetricsBadges path={endpoint.path} method={endpoint.httpMethod} />
        {endpoint.description && (
          <span className="text-slate-600 text-xs truncate max-w-xs hidden lg:block">{endpoint.description}</span>
        )}
        <div className={`shrink-0 flex items-center justify-center w-6 h-6 rounded-lg transition-colors ${
          expanded ? 'bg-violet-600/20 text-violet-400' : 'text-slate-600 hover:text-slate-400'
        }`}>
          {expanded ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
        </div>
      </button>

      {/* Expanded detail */}
      {expanded && (
        <div className="px-4 pb-4 border-t border-white/5">
          {/* Metadata */}
          <div className="mt-3 grid grid-cols-2 gap-3">
            <div className="flex items-start gap-2">
              <div className="flex items-center justify-center w-5 h-5 rounded bg-white/5 shrink-0 mt-0.5">
                <Cpu size={10} className="text-slate-500" />
              </div>
              <div>
                <p className="text-[10px] text-slate-600 uppercase tracking-wider font-semibold">Controller</p>
                <p className="text-slate-300 text-xs font-mono mt-0.5">{endpoint.controllerName}</p>
              </div>
            </div>
            <div className="flex items-start gap-2">
              <div className="flex items-center justify-center w-5 h-5 rounded bg-white/5 shrink-0 mt-0.5">
                <Tag size={10} className="text-slate-500" />
              </div>
              <div>
                <p className="text-[10px] text-slate-600 uppercase tracking-wider font-semibold">Method</p>
                <p className="text-slate-300 text-xs font-mono mt-0.5">{endpoint.methodName}()</p>
              </div>
            </div>
          </div>

          {endpoint.description && (
            <p className="mt-3 text-slate-400 text-xs leading-relaxed bg-white/3 rounded-lg px-3 py-2 border border-white/5">
              {endpoint.description}
            </p>
          )}

          {/* Inputs & Outputs */}
          {(
            (endpoint.pathParams?.length > 0) ||
            (endpoint.requiredQueryParams?.length > 0) ||
            (endpoint.optionalQueryParams?.length > 0) ||
            endpoint.requestBodyType ||
            endpoint.responseType
          ) && (
            <div className="mt-3 rounded-xl border border-white/8 overflow-hidden">
              {/* Header */}
              <div className="flex items-center gap-2 px-3 py-2 bg-[#0f1117] border-b border-white/6">
                <Package size={11} className="text-violet-400" />
                <span className="text-[10px] font-semibold uppercase tracking-widest text-slate-500">Inputs &amp; Outputs</span>
              </div>

              <div className="divide-y divide-white/5">
                {/* Path Params */}
                {endpoint.pathParams?.length > 0 && (
                  <div className="flex items-start gap-3 px-3 py-2.5 bg-[#13151f]">
                    <span className="text-[10px] text-slate-600 uppercase tracking-wider font-semibold w-24 shrink-0 pt-0.5">Path Params</span>
                    <div className="flex flex-wrap gap-1.5">
                      {endpoint.pathParams.map((p) => (
                        <span key={p} className="px-2 py-0.5 rounded-md bg-amber-500/10 border border-amber-500/20 text-amber-300 text-[11px] font-mono">{`{${p}}`}</span>
                      ))}
                    </div>
                  </div>
                )}

                {/* Required Query Params */}
                {endpoint.requiredQueryParams?.length > 0 && (
                  <div className="flex items-start gap-3 px-3 py-2.5 bg-[#13151f]">
                    <span className="text-[10px] text-slate-600 uppercase tracking-wider font-semibold w-24 shrink-0 pt-0.5">Required</span>
                    <div className="flex flex-wrap gap-1.5">
                      {endpoint.requiredQueryParams.map((p) => (
                        <span key={p} className="px-2 py-0.5 rounded-md bg-sky-500/10 border border-sky-500/20 text-sky-300 text-[11px] font-mono">?{p}</span>
                      ))}
                    </div>
                  </div>
                )}

                {/* Optional Query Params */}
                {endpoint.optionalQueryParams?.length > 0 && (
                  <div className="flex items-start gap-3 px-3 py-2.5 bg-[#13151f]">
                    <span className="text-[10px] text-slate-600 uppercase tracking-wider font-semibold w-24 shrink-0 pt-0.5">Optional</span>
                    <div className="flex flex-wrap gap-1.5">
                      {endpoint.optionalQueryParams.map((p) => (
                        <span key={p} className="px-2 py-0.5 rounded-md bg-slate-500/10 border border-slate-500/20 text-slate-400 text-[11px] font-mono">?{p}</span>
                      ))}
                    </div>
                  </div>
                )}

                {/* Request Body */}
                {endpoint.requestBodyType && (
                  <div className="flex items-center gap-3 px-3 py-2.5 bg-[#13151f]">
                    <span className="text-[10px] text-slate-600 uppercase tracking-wider font-semibold w-24 shrink-0">Request Body</span>
                    <div className="flex items-center gap-1.5">
                      <CornerDownRight size={10} className="text-slate-600" />
                      <span className="px-2 py-0.5 rounded-md bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 text-[11px] font-mono">{endpoint.requestBodyType}</span>
                    </div>
                  </div>
                )}

                {/* Response Type */}
                {endpoint.responseType && (
                  <div className="flex items-center gap-3 px-3 py-2.5 bg-[#13151f]">
                    <span className="text-[10px] text-slate-600 uppercase tracking-wider font-semibold w-24 shrink-0">Response</span>
                    <div className="flex items-center gap-1.5">
                      <ArrowRight size={10} className="text-slate-600" />
                      <span className="px-2 py-0.5 rounded-md bg-violet-500/10 border border-violet-500/20 text-violet-300 text-[11px] font-mono">{endpoint.responseType}</span>
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Action buttons */}
          <div className="mt-4 flex items-center gap-2">
            <button
              onClick={() => setTryIt((v) => !v)}
              className={`flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg font-semibold transition-all duration-150 ${
                tryIt
                  ? 'bg-violet-600 text-white shadow-sm shadow-violet-900/40'
                  : 'border border-violet-500/40 text-violet-400 hover:bg-violet-500/10 hover:border-violet-400'
              }`}
            >
              <Play size={11} /> {tryIt ? 'Close' : 'Try it out'}
            </button>
            <button
              onClick={() => onAskAI(buildEndpointAiPrompt(endpoint.httpMethod, endpoint.path))}
              className="flex items-center gap-1.5 px-3 py-1.5 border border-white/10 text-slate-400 hover:text-white hover:border-violet-500/40 hover:bg-violet-500/5 text-xs rounded-lg font-semibold transition-all duration-150"
            >
              <Bot size={11} /> Ask AI
            </button>
          </div>

          {tryIt && <TryItPanel endpoint={endpoint} token={token} />}
        </div>
      )}
    </div>
  )
}
