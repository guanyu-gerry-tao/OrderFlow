import type { ReactNode } from "react";

interface LoadingStateProps {
  label: string;
}

interface EmptyStateProps {
  title: string;
  body: string;
}

interface ErrorStateProps {
  title: string;
  message: string;
  onRetry: () => void;
}

interface StatusPillProps {
  tone: "good" | "warn" | "danger" | "neutral";
  children: ReactNode;
}

export function LoadingState({ label }: LoadingStateProps) {
  return <div className="state state-loading">{label}</div>;
}

export function EmptyState({ title, body }: EmptyStateProps) {
  return (
    <div className="state state-empty">
      <strong>{title}</strong>
      <span>{body}</span>
    </div>
  );
}

export function ErrorState({ title, message, onRetry }: ErrorStateProps) {
  return (
    <div className="state state-error">
      <strong>{title}</strong>
      <span>{message}</span>
      <button type="button" onClick={onRetry}>
        Retry
      </button>
    </div>
  );
}

export function StatusPill({ tone, children }: StatusPillProps) {
  return <span className={`status-pill status-${tone}`}>{children}</span>;
}
