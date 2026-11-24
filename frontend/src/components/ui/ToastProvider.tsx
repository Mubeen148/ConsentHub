import React, { createContext, useCallback, useContext, useState } from "react";
import { AlertCircle, AlertTriangle, CheckCircle2, Info, X } from "lucide-react";

export type ToastVariant = "success" | "warning" | "danger" | "info";

export interface ToastItem {
  id: string;
  title: string;
  description?: string;
  variant: ToastVariant;
}

interface ToastContextValue {
  showToast: (toast: Omit<ToastItem, "id">) => void;
  dismissToast: (id: string) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const BORDER_ACCENT: Record<ToastVariant, string> = {
  success: "border-l-[var(--ch-status-success-solid)]",
  warning: "border-l-[var(--ch-status-warning-solid)]",
  danger: "border-l-[var(--ch-status-danger-solid)]",
  info: "border-l-[var(--ch-accent-brand)]",
};

const ICON_MAP: Record<ToastVariant, React.ReactNode> = {
  success: <CheckCircle2 className="h-5 w-5 text-status-success-text shrink-0" aria-hidden="true" />,
  warning: <AlertTriangle className="h-5 w-5 text-status-warning-text shrink-0" aria-hidden="true" />,
  danger: <AlertCircle className="h-5 w-5 text-status-danger-text shrink-0" aria-hidden="true" />,
  info: <Info className="h-5 w-5 text-brand shrink-0" aria-hidden="true" />,
};

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((item) => item.id !== id));
  }, []);

  const showToast = useCallback(
    (toast: Omit<ToastItem, "id">) => {
      const id = `toast_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
      setToasts((prev) => [...prev.slice(-3), { ...toast, id }]);

      if (toast.variant === "success" || toast.variant === "info") {
        window.setTimeout(() => {
          dismissToast(id);
        }, 5000);
      }
    },
    [dismissToast]
  );

  return (
    <ToastContext.Provider value={{ showToast, dismissToast }}>
      {children}
      <div
        aria-live="polite"
        aria-atomic="false"
        className="fixed top-4 left-1/2 -translate-x-1/2 md:top-auto md:left-auto md:translate-x-0 md:bottom-6 md:right-6 z-[60] flex flex-col gap-2.5 w-[min(380px,calc(100vw-32px))] pointer-events-none"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role={toast.variant === "danger" || toast.variant === "warning" ? "alert" : "status"}
            className={[
              "pointer-events-auto flex items-start justify-between gap-3 min-h-[56px] px-4 py-3",
              "bg-surface border border-border border-l-4 rounded-card shadow-md",
              BORDER_ACCENT[toast.variant],
            ].join(" ")}
          >
            <div className="flex items-start gap-2.5 pt-1 min-w-0">
              {ICON_MAP[toast.variant]}
              <div className="min-w-0">
                <p className="text-sm font-semibold text-ink">{toast.title}</p>
                {toast.description ? (
                  <p className="mt-0.5 text-xs text-ink-secondary">{toast.description}</p>
                ) : null}
              </div>
            </div>
            <button
              type="button"
              onClick={() => dismissToast(toast.id)}
              aria-label="Dismiss notification"
              className="inline-flex items-center justify-center min-w-tap min-h-tap -mr-2 -my-1.5 rounded-control text-ink-muted hover:text-ink hover:bg-subtle transition-colors shrink-0"
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
};

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error("useToast must be used within a ToastProvider");
  }
  return ctx;
}
