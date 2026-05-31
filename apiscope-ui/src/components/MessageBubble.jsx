import ReactMarkdown from 'react-markdown'
import { Bot, User, Code2, Copy, Check } from 'lucide-react'
import { useState } from 'react'

function CopyButton({ text }) {
  const [copied, setCopied] = useState(false)
  const handleCopy = () => {
    navigator.clipboard.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }
  return (
    <button
      onClick={handleCopy}
      className="flex items-center gap-1 text-[10px] text-slate-400 hover:text-white transition-colors px-2 py-0.5 rounded hover:bg-white/10"
    >
      {copied ? <Check size={10} className="text-emerald-400" /> : <Copy size={10} />}
      {copied ? 'Copied' : 'Copy'}
    </button>
  )
}

function MarkdownCode({ node, children, ...props }) {
  const codeStr  = String(children)
  const isInline = !codeStr.includes('\n')
  if (isInline) {
    return (
      <code className="bg-[#0f1117] text-violet-300 px-1.5 py-0.5 rounded-md text-xs font-mono border border-white/8" {...props}>
        {children}
      </code>
    )
  }
  return (
    <div className="my-3 rounded-xl overflow-hidden border border-white/8">
      <div className="flex items-center justify-between bg-[#0f1117] px-3 py-2 border-b border-white/8">
        <div className="flex items-center gap-2">
          <Code2 size={11} className="text-slate-500" />
          <span className="text-slate-500 text-[10px] font-mono uppercase tracking-wider">code</span>
        </div>
        <CopyButton text={codeStr} />
      </div>
      <pre className="bg-[#080a10] p-4 overflow-x-auto">
        <code className="text-emerald-300 text-xs font-mono" {...props}>{children}</code>
      </pre>
    </div>
  )
}

const MD_COMPONENTS = {
  code: MarkdownCode,
  p:          ({ children }) => <p className="mb-2 last:mb-0 leading-relaxed">{children}</p>,
  ul:         ({ children }) => <ul className="list-none mb-2 space-y-1.5">{children}</ul>,
  li:         ({ children }) => <li className="flex items-start gap-2"><span className="text-violet-400 mt-1.5 shrink-0">▸</span><span>{children}</span></li>,
  ol:         ({ children }) => <ol className="list-decimal list-inside mb-2 space-y-1">{children}</ol>,
  strong:     ({ children }) => <strong className="text-white font-semibold">{children}</strong>,
  h1:         ({ children }) => <h1 className="text-base font-bold text-white mb-2 mt-3 first:mt-0">{children}</h1>,
  h2:         ({ children }) => <h2 className="text-sm font-bold text-white mb-2 mt-3 first:mt-0">{children}</h2>,
  h3:         ({ children }) => <h3 className="text-xs font-bold text-slate-200 mb-1.5 mt-2 first:mt-0">{children}</h3>,
  blockquote: ({ children }) => <blockquote className="border-l-2 border-violet-500/50 pl-3 text-slate-400 italic my-2">{children}</blockquote>,
}

export default function MessageBubble({ msg }) {
  const isUser = msg.role === 'user'
  const time   = msg.timestamp
    ? new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : ''

  return (
    <div className={`flex gap-3 group ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
      {/* Avatar */}
      <div className={`shrink-0 flex items-center justify-center w-8 h-8 rounded-full border ${
        isUser
          ? 'bg-gradient-to-br from-violet-500 to-purple-700 border-violet-500/50 shadow-sm shadow-violet-900/50'
          : 'bg-[#1e2030] border-white/10'
      }`}>
        {isUser
          ? <User size={13} className="text-white" />
          : <Bot  size={13} className="text-violet-400" />}
      </div>

      {/* Content */}
      <div className={`flex flex-col gap-1 max-w-[80%] ${isUser ? 'items-end' : 'items-start'}`}>
        <div className={`flex items-center gap-2 px-1 ${ isUser ? 'flex-row-reverse' : 'flex-row'}`}>
            <span className="text-[10px] font-semibold text-slate-500">{isUser ? 'You' : 'APIScope AI'}</span>
          <span className="text-[10px] text-slate-700 opacity-0 group-hover:opacity-100 transition-opacity">{time}</span>
        </div>
        <div className={`rounded-2xl px-4 py-3 text-sm leading-relaxed ${
          isUser
            ? 'bg-gradient-to-br from-violet-600 to-purple-700 text-white rounded-tr-sm shadow-sm shadow-violet-900/40'
            : 'bg-[#1a1d2e] border border-white/8 text-slate-200 rounded-tl-sm'
        }`}>
          {isUser ? (
            <p className="whitespace-pre-wrap">{msg.content}</p>
          ) : (
            <ReactMarkdown components={MD_COMPONENTS}>
              {msg.content}
            </ReactMarkdown>
          )}
        </div>
      </div>
    </div>
  )
}
