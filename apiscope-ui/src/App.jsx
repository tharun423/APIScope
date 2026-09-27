import { useState } from 'react'
import Sidebar     from './components/Sidebar'
import ApiExplorer from './components/ApiExplorer'
import FlowTracer  from './components/FlowTracer'
import AiChat      from './components/AiChat'

export default function App() {
  const [tab, setTab] = useState('explorer')

  return (
    <div className="flex h-screen bg-[#0f1117] text-white overflow-hidden">
      <Sidebar tab={tab} onTab={setTab} />
      <main className="flex flex-col flex-1 overflow-hidden">
        {tab === 'explorer' && <ApiExplorer />}
        {tab === 'flow'     && <FlowTracer />}
        {tab === 'chat'     && <AiChat />}
      </main>
    </div>
  )
}
