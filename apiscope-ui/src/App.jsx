import { useState } from 'react'
import Sidebar     from './components/Sidebar'
import ApiExplorer from './components/ApiExplorer'
import AiChat      from './components/AiChat'
import FlowTracer  from './components/FlowTracer'

export default function App() {
  const [tab,             setTab]             = useState('explorer')
  const [pendingQuestion, setPendingQuestion] = useState(null)
  const [resetKey,        setResetKey]        = useState(0)

  const handleAskAI = (question) => {
    setPendingQuestion(question)
    setTab('chat')
  }

  const handleReset = () => {
    setResetKey((k) => k + 1)
    setPendingQuestion(null)
  }

  return (
    <div className="flex h-screen bg-[#0f1117] text-white overflow-hidden">
      <Sidebar tab={tab} onTab={setTab} onReset={handleReset} />
      <main className="flex flex-col flex-1 overflow-hidden">
        {tab === 'explorer'
          ? <ApiExplorer onAskAI={handleAskAI} />
          : tab === 'flow'
          ? <FlowTracer />
          : <AiChat
              key={resetKey}
              pendingQuestion={pendingQuestion}
              onPendingConsumed={() => setPendingQuestion(null)}
            />
        }
      </main>
    </div>
  )
}
