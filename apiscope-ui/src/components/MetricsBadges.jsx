import { Zap, CheckCircle, BarChart3 } from 'lucide-react'
import { useEndpointMetrics } from '../hooks/useEndpointMetrics'

export default function MetricsBadges({ path, method }) {
  const { avgResponseMs, successRate, available, ready } = useEndpointMetrics(path, method)

  // Still loading first result
  if (!ready) {
    return (
      <div className="flex items-center gap-1.5 shrink-0">
        <span className="w-14 h-4 rounded-md bg-white/5 animate-pulse" />
        <span className="w-14 h-4 rounded-md bg-white/5 animate-pulse" />
      </div>
    )
  }

  // Actuator not present or endpoint has never been called — show hint
  if (!available) {
    return (
      <div className="flex items-center gap-1 shrink-0" title="No metrics yet — hit this endpoint to see live stats (requires spring-boot-starter-actuator)">
        <BarChart3 size={11} className="text-slate-600" />
        <span className="text-[10px] text-slate-600 font-mono">No metrics</span>
      </div>
    )
  }

  const responseColor =
    avgResponseMs < 30  ? 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20' :
    avgResponseMs < 100 ? 'text-amber-400   bg-amber-500/10   border-amber-500/20'   :
                          'text-red-400     bg-red-500/10     border-red-500/20'

  const rateColor =
    successRate >= 99   ? 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20' :
    successRate >= 95   ? 'text-amber-400   bg-amber-500/10   border-amber-500/20'   :
                          'text-red-400     bg-red-500/10     border-red-500/20'

  return (
    <div className="flex items-center gap-1.5 shrink-0">
      <span
        title={`Avg response time (Micrometer • last ${Math.round(avgResponseMs)}ms)`}
        className={`flex items-center gap-1 px-1.5 py-0.5 rounded-md border text-[10px] font-semibold font-mono transition-colors ${responseColor}`}
      >
        <Zap size={9} />
        {avgResponseMs}ms
      </span>

      <span
        title={`Success rate from Micrometer http.server.requests`}
        className={`flex items-center gap-1 px-1.5 py-0.5 rounded-md border text-[10px] font-semibold font-mono transition-colors ${rateColor}`}
      >
        <CheckCircle size={9} />
        {successRate}%
      </span>
    </div>
  )
}
