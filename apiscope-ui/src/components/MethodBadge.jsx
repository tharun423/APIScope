import { methodColor } from '../constants/methodColors'

export default function MethodBadge({ method, size = 'sm' }) {
  const c       = methodColor(method)
  const padding = size === 'lg' ? 'px-3 py-1 text-sm' : 'px-2 py-0.5 text-xs'
  return (
    <span className={`${c.bg} ${c.text} ${padding} rounded font-mono font-bold uppercase shrink-0`}>
      {method}
    </span>
  )
}
