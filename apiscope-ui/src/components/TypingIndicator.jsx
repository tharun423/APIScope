import { Bot } from 'lucide-react'

export default function TypingIndicator() {
  return (
    <div className="flex gap-3 items-end">
      <div className="shrink-0 flex items-center justify-center w-8 h-8 rounded-full bg-[#1e2030] border border-white/10">
        <Bot size={13} className="text-violet-400" />
      </div>
      <div className="bg-[#1a1d2e] border border-white/8 rounded-2xl rounded-tl-sm px-4 py-3">
        <div className="flex items-center gap-1.5">
          {[0, 200, 400].map((delay) => (
            <span
              key={delay}
              className="typing-dot block w-1.5 h-1.5 rounded-full bg-violet-500 opacity-60"
              style={{ animationDelay: `${delay}ms` }}
            />
          ))}
        </div>
      </div>
      <span className="text-[10px] text-slate-700 mb-1 ml-1">Thinking…</span>
    </div>
  )
}
