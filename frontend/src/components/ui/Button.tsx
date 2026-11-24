import React from "react";
import { Loader2 } from "lucide-react";

export type ButtonVariant = "primary" | "secondary" | "ghost" | "destructive";

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  loading?: boolean;
  fullWidth?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  testId?: string;
}

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary:
    "bg-action text-ink-inverse border border-action hover:bg-action-hover active:bg-action-active",
  secondary:
    "bg-surface text-ink border border-border-control hover:bg-canvas hover:border-border-strong",
  ghost:
    "bg-transparent text-ink border border-transparent hover:bg-subtle",
  destructive:
    "bg-status-danger-solid text-ink-inverse border border-status-danger-solid hover:bg-status-danger-hover",
};

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = "primary",
    loading = false,
    fullWidth = false,
    leftIcon,
    rightIcon,
    testId,
    disabled,
    className = "",
    children,
    type = "button",
    ...rest
  },
  ref
) {
  const isDisabled = Boolean(disabled || loading);

  return (
    <button
      ref={ref}
      type={type}
      disabled={isDisabled}
      data-testid={testId}
      className={[
        "inline-flex items-center justify-center gap-2 min-h-tap min-w-tap px-[18px] py-[10px]",
        "rounded-control text-sm font-semibold leading-tight transition-colors duration-150",
        "select-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--ch-focus-ring)]",
        VARIANT_CLASSES[variant],
        fullWidth ? "w-full" : "",
        isDisabled ? "opacity-55 cursor-not-allowed" : "cursor-pointer",
        className,
      ]
        .filter(Boolean)
        .join(" ")}
      {...rest}
    >
      {loading ? (
        <Loader2 className="h-4 w-4 shrink-0 animate-spin" aria-hidden="true" />
      ) : leftIcon ? (
        <span className="inline-flex shrink-0" aria-hidden="true">
          {leftIcon}
        </span>
      ) : null}
      <span>{children}</span>
      {!loading && rightIcon ? (
        <span className="inline-flex shrink-0" aria-hidden="true">
          {rightIcon}
        </span>
      ) : null}
    </button>
  );
});
