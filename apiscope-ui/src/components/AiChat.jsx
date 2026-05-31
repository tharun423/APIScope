import { useRef, useEffect } from 'react'
import MessageBubble    from './MessageBubble'
import TypingIndicator  from './TypingIndicator'
import InputBar         from './InputBar'
import SuggestionChips  from './SuggestionChips'
import { useChat }      from '../hooks/useChat'
import { Bot, MessageSquare } from 'lucide-react'

export default function AiChat({ pendingQuestion, onPendingConsumed }) {
  const { messages, loading, sendMessage } = useChat()
  const bottomRef       = useRef(null)
  const showSuggestions = messages.length === 0

  // Show typing indicator only while waiting for the FIRST token.
  // Once the assistant message has content, the text itself is visible — no spinner needed.
  const lastMsg              = messages[messages.length - 1]
  const waitingForFirstToken = loading && lastMsg?.role === 'assistant' && !lastMsg?.content

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  useEffect(() => {
    if (pendingQuestion) {
      sendMessage(pendingQuestion)
      onPendingConsumed()
    }
  }, [pendingQuestion, sendMessage, onPendingConsumed])

  return (
    <div className="flex flex-col flex-1 overflow-hidden bg-[#0f1117]">
      {/* Page header */}
      <div className="shrink-0 flex items-center gap-3 px-6 py-4 border-b border-white/5 bg-[#13151f]/60 backdrop-blur-sm">
        <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-violet-600/20 border border-violet-500/20">
          <Bot size={15} className="text-violet-400" />
        </div>
        <div>
          <h2 className="text-sm font-semibold text-white">AI Chat</h2>
          <p className="text-[11px] text-slate-500">
            {showSuggestions ? 'Start a conversation' : `${messages.length} message${messages.length !== 1 ? 's' : ''}`}
          </p>
        </div>
        {!showSuggestions && (
          <div className="ml-auto flex items-center gap-1.5 text-[10px] text-slate-600">
            <MessageSquare size={10} />
            <span>Session active</span>
          </div>
        )}
      </div>

      {/* Message area */}
      <main className="flex flex-col flex-1 overflow-y-auto scrollbar-thin">
        {showSuggestions ? (
          <SuggestionChips onSelect={sendMessage} />
        ) : (
          <div className="max-w-3xl mx-auto w-full px-6 py-6 flex flex-col gap-6">
            {messages.map((msg, i) => <MessageBubble key={`${msg.role}-${msg.timestamp}-${i}`} msg={msg} />)}
            {waitingForFirstToken && <TypingIndicator />}
            <div ref={bottomRef} />
          </div>
        )}
      </main>

      <InputBar onSend={sendMessage} loading={loading} />
    </div>
  )
}
