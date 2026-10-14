import { Component, type ReactNode } from 'react';

export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    if (this.state.failed) {
      return (
        <main className="page-shell">
          <div className="empty-state" role="alert">
            <h1>Let's try that again.</h1>
            <p>The page ran into a problem. Your saved orders are still available.</p>
            <a className="button button-dark" href="/">
              Reload the store ↗
            </a>
          </div>
        </main>
      );
    }
    return this.props.children;
  }
}
