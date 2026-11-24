import React from "react";
import { AlertCircle, Inbox, RefreshCw } from "lucide-react";
import { Button } from "./Button";
import { parseSafeMarkdown } from "../../lib/security";

export interface SkeletonProps {
  className?: string;
}

export const Skeleton: React.FC<SkeletonProps> = ({ className = "h-5 w-full" }) => (
  <div className={`ch-skeleton ${className}`} aria-hidden="true" />
);

export const KpiGridSkeleton: React.FC<{ count?: number }> = ({ count = 4 }) => (
  <div
    className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 md:gap-6"
    role="status"
    aria-label="Loading metrics"
  >
    {Array.from({ length: count }).map((_, idx) => (
      <div
        key={idx}
        className="bg-surface border border-border rounded-card p-6 h-[116px] flex flex-col justify-between"
      >
        <Skeleton className="h-3.5 w-28" />
        <Skeleton className="h-8 w-24" />
        <Skeleton className="h-3 w-36" />
      </div>
    ))}
  </div>
);

export const PreferenceListSkeleton: React.FC<{ rows?: number }> = ({ rows = 5 }) => (
  <div
    className="bg-surface border border-border rounded-card divide-y divide-border-subtle"
    role="status"
    aria-label="Loading consent purposes"
  >
    {Array.from({ length: rows }).map((_, idx) => (
      <div key={idx} className="px-6 py-5 flex items-center justify-between gap-6 min-h-[88px]">
        <div className="flex-1 space-y-2">
          <div className="flex items-center gap-2.5">
            <Skeleton className="h-5 w-48" />
            <Skeleton className="h-5 w-24 rounded-pill" />
          </div>
          <Skeleton className="h-4 w-full max-w-xl" />
        </div>
        <Skeleton className="h-6 w-12 rounded-pill shrink-0" />
      </div>
    ))}
  </div>
);

export const TableSkeleton: React.FC<{ rows?: number; columns?: number }> = ({
  rows = 5,
  columns = 5,
}) => (
  <div
    className="bg-surface border border-border rounded-card overflow-hidden"
    role="status"
    aria-label="Loading records"
  >
    <div className="bg-subtle border-b border-border px-4 py-3 flex items-center gap-4">
      {Array.from({ length: columns }).map((_, idx) => (
        <Skeleton key={idx} className="h-3.5 flex-1" />
      ))}
    </div>
    <div className="divide-y divide-border-subtle">
      {Array.from({ length: rows }).map((_, rowIdx) => (
        <div key={rowIdx} className="px-4 py-3.5 flex items-center gap-4 min-h-[52px]">
          {Array.from({ length: columns }).map((__, colIdx) => (
            <Skeleton key={colIdx} className="h-4 flex-1" />
          ))}
        </div>
      ))}
    </div>
  </div>
);

export interface EmptyStateProps {
  icon?: React.ReactNode;
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  icon,
  title,
  description,
  actionLabel,
  onAction,
}) => (
  <div className="flex flex-col items-center justify-center text-center px-6 py-10 border border-dashed border-border rounded-card bg-surface">
    <div
      className="h-12 w-12 rounded-card bg-subtle flex items-center justify-center text-ink-secondary mb-4"
      aria-hidden="true"
    >
      {icon ?? <Inbox className="h-6 w-6" />}
    </div>
    <h3 className="text-base font-semibold text-ink font-display">{title}</h3>
    <p className="mt-1.5 text-sm text-ink-muted max-w-[380px]">{description}</p>
    {actionLabel && onAction ? (
      <div className="mt-5">
        <Button variant="secondary" onClick={onAction}>
          {actionLabel}
        </Button>
      </div>
    ) : null}
  </div>
);

export interface ErrorStateProps {
  title?: string;
  message: string;
  correlationId?: string;
  onRetry?: () => void;
}

export const ErrorState: React.FC<ErrorStateProps> = ({
  title = "Unable to load privacy data",
  message,
  correlationId,
  onRetry,
}) => (
  <div
    role="alert"
    className="bg-status-danger-bg border border-status-danger-border rounded-card px-6 py-5"
  >
    <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
      <div className="flex items-start gap-3">
        <AlertCircle
          className="h-5 w-5 text-status-danger-text shrink-0 mt-0.5"
          aria-hidden="true"
        />
        <div>
          <h3 className="text-base font-semibold text-status-danger-text font-display">
            {title}
          </h3>
          <p className="mt-1 text-sm text-ink">{message}</p>
          {correlationId ? (
            <p className="mt-1.5 text-xs font-mono text-ink-secondary">
              Correlation ID: {correlationId}
            </p>
          ) : null}
        </div>
      </div>
      {onRetry ? (
        <div className="shrink-0">
          <Button
            variant="secondary"
            onClick={onRetry}
            leftIcon={<RefreshCw className="h-4 w-4" />}
          >
            Retry
          </Button>
        </div>
      ) : null}
    </div>
  </div>
);

export const SafeMarkdown: React.FC<{ content: string | null | undefined }> = ({
  content,
}) => {
  const blocks = parseSafeMarkdown(content);
  if (blocks.length === 0) {
    return null;
  }

  return (
    <div className="space-y-2.5 text-sm text-ink-secondary">
      {blocks.map((block, index) => {
        if (block.kind === "h1") {
          return (
            <h3 key={index} className="text-base font-semibold text-ink pt-1">
              {block.text}
            </h3>
          );
        }
        if (block.kind === "h2") {
          return (
            <h4 key={index} className="text-sm font-semibold text-ink pt-1">
              {block.text}
            </h4>
          );
        }
        if (block.kind === "h3") {
          return (
            <h5 key={index} className="text-xs font-semibold uppercase tracking-wider text-ink-secondary pt-1">
              {block.text}
            </h5>
          );
        }
        if (block.kind === "bullet") {
          return (
            <div key={index} className="flex items-start gap-2 pl-2">
              <span className="text-ink-muted select-none" aria-hidden="true">
                •
              </span>
              <span>{block.text}</span>
            </div>
          );
        }
        return (
          <p key={index} className="leading-relaxed">
            {block.text}
          </p>
        );
      })}
    </div>
  );
};
