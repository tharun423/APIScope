import { useState, useRef, useEffect } from 'react'
import { MessageSquare, Send, Loader2, Bot, User, RefreshCw } from 'lucide-react'
import { apiClient } from '../api/client'

const CHAT_URL = '/apiscope/api/chat'

const SUGGESTIONS = [
  'How do I process a payment?',
  'How do I cancel a subscription with a partial refund?',
  'What endpoints are available for order management?',
  'How do I get payment history for an account?',
]

export default function AiChat() {
  const [messages, setMessages] = useState([])
  const [input,    setInput]    = useState('')
  const [loading,  setLoading]  = useState(false)
  const bottomRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  async function send(question) {
    const q = (question ?? input).trim()
    if (!q || loading) return
    setInput('')
    setMessages(prev => [...prev, { role: 'user', text: q }])
    setLoading(true)
    try {
      const { data } = await apiClient(CHAT_URL, {
        method: 'POST',
        body: JSON.stringify({ question: q }),
      })
      setMessages(prev => [...prev, { role: 'assistant', text: data.answer ?? 'No answer returned.' }])
    } catch (err) {
      setMessages(prev => [...prev, { role: 'assistant', text: `Error: ${err.message}`, error: true }])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex flex-col flex-1 overflow-hidden bg-[#0f1117]">
      {/* Header */}
      <div className="shrink-0 px-6 py-4 border-b border-white/5 bg-[#13151f]/60 backdrop-blur-sm flex items-center gap-3">
        <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-violet-600/20 border border-violet-500/20">
          <MessageSquare size={15} className="text-violet-400" />
        </div>
        <div className="flex-1">
          <h2 className="text-sm font-semibold text-white">AI Chat</h2>
          <p className="text-[11px] text-slate-500">Ask questions about your API endpoints</p>
        </div>
        {messages.length > 0 && (
          <button
            onClick={() => setMessages([])}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] text-slate-500 hover:text-white border border-white/8 hover:border-white/20 transition-all"
          >
            <RefreshCw size={11} /> Clear
          </button>
        )}
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-6 py-5 space-y-4 scrollbar-thin">
        {messages.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full gap-6 text-center">
            <div className="flex items-center justify-center w-14 h-14 rounded-2xl bg-violet-600/10 border border-violet-500/20">
              <Bot size={26} className="text-violet-400" />
            </div>
            <div>
              <p className="text-white font-semibold text-sm">Ask about your APIs</p>
              <p className="text-slate-500 text-xs mt-1">Powered by RAG — answers are grounded in your scanned endpoints</p>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 w-full max-w-lg">
              {SUGGESTIONS.map(s => (
                <button
                  key={s}
                  onClick={() => send(s)}
                  className="text-left px-3 py-2.5 rounded-xl bg-[#1a1d2e] border border-white/8 text-xs text-slate-400 hover:text-white hover:border-violet-500/40 transition-all"
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        ) : (
          messages.map((msg, i) => (
            <div key={i} className={`flex gap-3 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
              {msg.role === 'assistant' && (
                <div className="flex items-center justify-center w-7 h-7 rounded-lg bg-violet-600/20 border border-violet-500/20 shrink-0 mt-0.5">
                  <Bot size={13} className="text-violet-400" />
                </div>
              )}
              <div className={`max-w-[75%] px-4 py-3 rounded-2xl text-xs leading-relaxed whitespace-pre-wrap ${
                msg.role === 'user'
                  ? 'bg-violet-600/20 border border-violet-500/30 text-white rounded-tr-sm'
                  : msg.error
                  ? 'bg-red-500/10 border border-red-500/20 text-red-400 rounded-tl-sm'
                  : 'bg-[#1a1d2e] border border-white/8 text-slate-200 rounded-tl-sm'
              }`}>
                {msg.text}
              </div>
              {msg.role === 'user' && (
                <div className="flex items-center justify-center w-7 h-7 rounded-lg bg-white/5 border border-white/10 shrink-0 mt-0.5">
                  <User size={13} className="text-slate-400" />
                </div>
              )}
            </div>
          ))
        )}
        {loading && (
          <div className="flex gap-3 justify-start">
            <div className="flex items-center justify-center w-7 h-7 rounded-lg bg-violet-600/20 border border-violet-500/20 shrink-0">
              <Bot size={13} className="text-violet-400" />
            </div>
            <div className="px-4 py-3 rounded-2xl rounded-tl-sm bg-[#1a1d2e] border border-white/8">
              <Loader2 size={13} className="text-violet-400 animate-spin" />
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <div className="shrink-0 px-6 py-4 border-t border-white/5 bg-[#13151f]/60">
        <form
          onSubmit={e => { e.preventDefault(); send() }}
          className="flex items-center gap-3 bg-[#1a1d2e] border border-white/8 rounded-2xl px-4 py-3 focus-within:border-violet-500/50 transition-colors"
        >
          <input
            value={input}
            onChange={e => setInput(e.target.value)}
            placeholder="Ask about your API endpoints…"
            className="flex-1 bg-transparent text-sm text-slate-200 outline-none placeholder-slate-600"
            disabled={loading}
          />
          <button
            type="submit"
            disabled={!input.trim() || loading}
            className="flex items-center justify-center w-8 h-8 rounded-xl bg-violet-600 hover:bg-violet-500 disabled:opacity-40 disabled:cursor-not-allowed transition-all shrink-0"
          >
            <Send size={13} className="text-white" />
          </button>
        </form>
      </div>
    </div>
  )
}
