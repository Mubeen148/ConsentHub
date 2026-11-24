import React, { useEffect, useId, useRef } from "react";
import { X } from "lucide-react";

export type ModalSize = "sm" | "md" | "lg";

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  size?: ModalSize;
  preventClose?: boolean;
  footer?: React.ReactNode;
  children: React.ReactNode;
}

const SIZE_CLASSES: Record<ModalSize, string> = {
  sm: "sm:max-w-[440px]",
  md: "sm:max-w-[560px]",
  lg: "sm:max-w-[720px]",
};

export const Modal: React.FC<ModalProps> = ({
  open,
  onClose,
  title,
  subtitle,
  size = "md",
  preventClose = false,
  footer,
  children,
}) => {
  const titleId = useId();
  const subtitleId = useId();
  const dialogRef = useRef<HTMLDivElement | null>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }

    previousFocusRef.current = document.activeElement as HTMLElement | null;
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const focusTimer = window.setTimeout(() => {
      const dialog = dialogRef.current;
      if (!dialog) {
        return;
      }
      const focusable = dialog.querySelectorAll<HTMLElement>(
        'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      );
      if (focusable[0]) {
        focusable[0].focus();
      } else {
        dialog.focus();
      }
    }, 10);

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !preventClose) {
        event.stopPropagation();
        onClose();
        return;
      }
      if (event.key === "Tab" && dialogRef.current) {
        const focusable = Array.from(
          dialogRef.current.querySelectorAll<HTMLElement>(
            'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
          )
        );
        if (focusable.length === 0) {
          event.preventDefault();
          return;
        }
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first && last) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last && first) {
          event.preventDefault();
          first.focus();
        }
      }
    };

    document.addEventListener("keydown", handleKeyDown);

    return () => {
      window.clearTimeout(focusTimer);
      document.body.style.overflow = prevOverflow;
      document.removeEventListener("keydown", handleKeyDown);
      if (previousFocusRef.current && typeof previousFocusRef.current.focus === "function") {
        previousFocusRef.current.focus();
      }
    };
  }, [open, onClose, preventClose]);

  if (!open) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-[rgba(20,21,25,0.52)] backdrop-blur-[4px] sm:p-4"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget && !preventClose) {
          onClose();
        }
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={subtitle ? subtitleId : undefined}
        tabIndex={-1}
        className={[
          "w-full bg-surface border border-border shadow-lg flex flex-col",
          "rounded-t-modal sm:rounded-modal max-h-[92dvh] sm:max-h-[88dvh]",
          SIZE_CLASSES[size],
        ].join(" ")}
      >
        <div className="flex items-start justify-between gap-4 px-6 py-5 border-b border-border-subtle shrink-0">
          <div className="min-w-0">
            <h2 id={titleId} className="text-lg font-semibold text-ink font-display">
              {title}
            </h2>
            {subtitle ? (
              <p id={subtitleId} className="mt-1 text-sm text-ink-secondary">
                {subtitle}
              </p>
            ) : null}
          </div>
          {!preventClose ? (
            <button
              type="button"
              onClick={onClose}
              aria-label="Close dialog"
              className="inline-flex items-center justify-center min-w-tap min-h-tap -mr-2 -mt-2 rounded-control text-ink-secondary hover:bg-subtle hover:text-ink transition-colors"
            >
              <X className="h-5 w-5" aria-hidden="true" />
            </button>
          ) : null}
        </div>

        <div className="p-6 overflow-y-auto flex-1">{children}</div>

        {footer ? (
          <div className="flex flex-col-reverse sm:flex-row sm:items-center sm:justify-end gap-2.5 px-6 py-4 border-t border-border-subtle bg-surface rounded-b-modal shrink-0">
            {footer}
          </div>
        ) : null}
      </div>
    </div>
  );
};
