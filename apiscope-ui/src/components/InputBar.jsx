import { useState, useRef, useEffect } from 'react'
import { Send, Loader2 } from 'lucide-react'

export default function InputBar({ onSend, loading }) {
  const [value,    setValue]    = useState('')
  const textareaRef             = useRef(null)

  const submit = () => {
    const q = value.trim()
    if (!q || loading) return
    setValue('')
    onSend(q)
  }

  const handleKey = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit() }
  }

  useEffect(() => {
    const el = textareaRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 140) + 'px'
  }, [value])

  const hasContent = value.trim().length > 0

  return (
    <div className="shrink-0 border-t border-white/5 bg-[#13151f]/80 backdrop-blur-sm px-6 py-4">
      <div className="max-w-3xl mx-auto">
        <div className={`flex items-end gap-3 bg-[#1a1d2e] border rounded-2xl px-4 py-3 transition-all duration-200 ${
          hasContent || loading ? 'border-violet-500/50 shadow-sm shadow-violet-900/20' : 'border-white/8 focus-within:border-violet-500/50'
        }`}>
          <textarea
            ref={textareaRef}
            rows={1}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={handleKey}
            disabled={loading}
            placeholder="Ask about any API endpoint…"
            className="flex-1 bg-transparent text-slate-200 placeholder-slate-600 text-sm resize-none outline-none leading-relaxed max-h-36 overflow-y-auto py-0.5"
          />
          <button
            onClick={submit}
            disabled={loading || !hasContent}
            className={`shrink-0 flex items-center justify-center w-9 h-9 rounded-xl transition-all duration-200 ${
              hasContent && !loading
                ? 'bg-violet-600 hover:bg-violet-500 text-white shadow-sm shadow-violet-900/40 scale-100'
                : 'bg-white/5 text-slate-600 cursor-not-allowed'
            }`}
          >
            {loading
              ? <Loader2 size={15} className="animate-spin text-violet-400" />
              : <Send size={15} className={hasContent ? 'text-white' : 'text-slate-600'} />}
          </button>
        </div>
        <p className="text-center text-[10px] text-slate-700 mt-2">
          Press <kbd className="font-mono bg-white/5 px-1 rounded">Enter</kbd> to send · <kbd className="font-mono bg-white/5 px-1 rounded">Shift+Enter</kbd> for new line
        </p>
      </div>
    </div>
  )
}
