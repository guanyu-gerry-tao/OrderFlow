import type { ErrorInfo, ReactNode } from "react";
import { Component } from "react";

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  message: string;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = {
    hasError: false,
    message: ""
  };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return {
      hasError: true,
      message: error.message
    };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error("OrderFlow console render error", error, info.componentStack);
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <main className="boundary-fallback">
          <section className="notice notice-error">
            <h1>Console error</h1>
            <p>{this.state.message || "The console could not render this view."}</p>
            <button type="button" onClick={() => window.location.reload()}>
              Reload console
            </button>
          </section>
        </main>
      );
    }

    return this.props.children;
  }
}
