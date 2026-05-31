import { Component } from 'react'
import { AlertTriangle, RotateCcw } from 'lucide-react'

/**
 * React Error Boundary — catches any unhandled render/lifecycle errors in the
 * component tree below it and shows a graceful fallback UI instead of a blank
 * white screen.
 *
 * Usage:
 * ```jsx
 * <ErrorBoundary>
 *   <YourComponent />
 * </ErrorBoundary>
 * ```
 *
 * @see https://react.dev/reference/react/Component#catching-rendering-errors-with-an-error-boundary
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error }
  }

  componentDidCatch(error, info) {
    // Replace with your own logging service (e.g. Sentry) when needed
    console.error('[ErrorBoundary] Caught an error:', error, info.componentStack)
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null })
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex flex-col items-center justify-center flex-1 gap-4 px-6 py-12 text-center bg-slate-900 min-h-screen">
          <div className="flex items-center justify-center w-16 h-16 rounded-2xl bg-red-600/20 border border-red-500/30">
            <AlertTriangle size={32} className="text-red-400" />
          </div>
          <div>
            <h2 className="text-white text-xl font-semibold mb-2">Something went wrong</h2>
            <p className="text-slate-400 text-sm max-w-sm">
              An unexpected error occurred. You can try resetting the view, or check the browser
              console for more details.
            </p>
          </div>
          {this.state.error && (
            <pre className="text-xs text-red-300 bg-slate-800 border border-slate-700 rounded-lg px-4 py-3 max-w-lg overflow-x-auto text-left">
              {this.state.error.message}
            </pre>
          )}
          <button
            onClick={this.handleReset}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 text-white text-sm font-medium transition-colors"
          >
            <RotateCcw size={14} /> Try again
          </button>
        </div>
      )
    }

    return this.props.children
  }
}
