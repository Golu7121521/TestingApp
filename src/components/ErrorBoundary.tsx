import { Component, type ReactNode } from 'react';

export class ErrorBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state = { error: null as Error | null };
  static getDerivedStateFromError(error: Error) { return { error }; }
  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div className="card m-4 p-6" role="alert">
        <h2 className="text-lg font-semibold">This screen hit a problem</h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">Your original files were not changed. You can try again.</p>
        <button className="btn btn-primary mt-4" onClick={() => this.setState({ error: null })}>Try again</button>
      </div>
    );
  }
}
