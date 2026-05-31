import { Sparkles, ArrowRight, Zap, Code2, BookOpen, Globe } from 'lucide-react'
import { SUGGESTIONS } from '../constants/suggestions'

const ICONS = [Zap, Code2, BookOpen, Globe, Sparkles, ArrowRight]

export default function SuggestionChips({ onSelect }) {
  return (
    <div className="flex flex-col items-center justify-center flex-1 px-6 py-12">
      {/* Hero */}
      <div className="text-center mb-10">
        <div className="flex items-center justify-center w-16 h-16 rounded-2xl bg-violet-600/15 border border-violet-500/20 mx-auto mb-5">
          <Sparkles size={28} className="text-violet-400" />
        </div>
        <h2 className="text-white text-2xl font-bold mb-3 tracking-tight">What can I help you with?</h2>
        <p className="text-slate-500 text-sm max-w-md leading-relaxed">
          APIScope has indexed all your REST endpoints and can explain them, generate code samples,
          and help you integrate your APIs faster.
        </p>
      </div>

      {/* Suggestion grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 w-full max-w-2xl">
        {SUGGESTIONS.map((s, i) => {
          const Icon = ICONS[i % ICONS.length]
          return (
            <button
              key={s}
              onClick={() => onSelect(s)}
              className="group flex items-start gap-3 text-left px-4 py-4 rounded-xl bg-[#1a1d2e] border border-white/6 hover:border-violet-500/40 hover:bg-violet-600/5 transition-all duration-200 text-sm text-slate-400 hover:text-white"
            >
              <div className="shrink-0 flex items-center justify-center w-7 h-7 rounded-lg bg-violet-600/10 border border-violet-500/20 group-hover:bg-violet-600/20 transition-colors mt-0.5">
                <Icon size={13} className="text-violet-400" />
              </div>
              <span className="flex-1 leading-snug">{s}</span>
              <ArrowRight size={13} className="shrink-0 text-slate-700 group-hover:text-violet-400 group-hover:translate-x-0.5 transition-all mt-0.5" />
            </button>
          )
        })}
      </div>

      <p className="mt-8 text-[11px] text-slate-700">
        Or type your own question below ↓
      </p>
    </div>
  )
}
