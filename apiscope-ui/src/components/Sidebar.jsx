import { RotateCcw, Activity, BookOpen, Bot, Workflow } from 'lucide-react'

const NAV_ITEMS = [
  { id: 'explorer', label: 'API Explorer', desc: 'Browse endpoints',      icon: BookOpen },
  { id: 'chat',     label: 'AI Chat',      desc: 'Ask questions',         icon: Bot      },
  { id: 'flow',     label: 'Flow Tracer',  desc: 'Live execution trace',  icon: Workflow },
]

export default function Sidebar({ tab, onTab, onReset }) {

  return (
    <aside className="flex flex-col w-64 shrink-0 bg-[#13151f] border-r border-white/5 h-full">
      {/* Brand */}
      <div className="px-5 py-5 border-b border-white/5">
        <div className="flex items-center gap-3">
          <div>
            <h1 className="text-white font-bold text-sm tracking-tight">APIScope</h1>
            <p className="text-violet-400/70 text-[10px] font-medium uppercase tracking-widest">AI API Assistant</p>
          </div>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-4 space-y-1">
        <p className="px-2 mb-3 text-[10px] font-semibold uppercase tracking-widest text-slate-600">Navigation</p>
        {NAV_ITEMS.map(({ id, label, desc, icon: Icon }) => {
          const active = tab === id
          return (
            <button
              key={id}
              onClick={() => onTab(id)}
              className={`group w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-left transition-all duration-150 ${
                active
                  ? 'bg-violet-600/20 border border-violet-500/30 text-white'
                  : 'text-slate-400 hover:text-white hover:bg-white/5 border border-transparent'
              }`}
            >
              <div className={`flex items-center justify-center w-7 h-7 rounded-lg transition-colors ${
                active ? 'bg-violet-600/20' : 'bg-white/5 group-hover:bg-white/8'
              }`}>
                <Icon size={14} className={active ? 'text-violet-400' : 'text-slate-500 group-hover:text-slate-300'} />
              </div>
              <div className="flex-1">
                <p className={`text-xs font-semibold leading-tight ${active ? 'text-white' : ''}`}>{label}</p>
                <p className="text-[10px] text-slate-500 mt-0.5">{desc}</p>
              </div>
              {active && (
                <div className="w-1.5 h-1.5 rounded-full bg-violet-400" />
              )}
            </button>
          )
        })}
      </nav>

      {/* Actions */}
      <div className="px-3 pb-3 space-y-1 border-t border-white/5 pt-3">
        <button
          onClick={onReset}
          title="Start a new chat session"
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-slate-400 hover:text-white hover:bg-white/5 border border-transparent transition-all duration-150 group text-left"
        >
          <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-white/5 group-hover:bg-white/10 transition-colors">
            <RotateCcw size={14} />
          </div>
          <div>
            <p className="text-xs font-semibold">New Chat</p>
            <p className="text-[10px] text-slate-500 mt-0.5">Clear conversation</p>
          </div>
        </button>
      </div>

      {/* Footer status */}
      <div className="px-5 py-4 border-t border-white/5">
        <div className="flex items-center gap-2">
          <Activity size={11} className="text-emerald-400" />
          <span className="text-[10px] text-slate-500">Connected · APIScope</span>
        </div>
        <p className="text-[10px] text-slate-700 mt-1">v1.0.0</p>
      </div>
    </aside>
  )
}
