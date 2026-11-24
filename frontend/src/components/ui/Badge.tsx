import React from "react";
import { Lock } from "lucide-react";

export type BadgeVariant =
  | "GDPR"
  | "CCPA"
  | "DEFAULT"
  | "ESSENTIAL"
  | "SUCCESS"
  | "WARNING"
  | "DANGER";

export interface BadgeProps {
  label: string;
  variant?: BadgeVariant;
  showDot?: boolean;
  className?: string;
}

const BADGE_STYLES: Record<
  BadgeVariant,
  { wrapper: string; dot: string }
> = {
  GDPR: {
    wrapper: "bg-badge-gdpr-bg border-badge-gdpr-border text-badge-gdpr-text",
    dot: "bg-badge-gdpr-text",
  },
  CCPA: {
    wrapper: "bg-badge-ccpa-bg border-badge-ccpa-border text-badge-ccpa-text",
    dot: "bg-badge-ccpa-text",
  },
  DEFAULT: {
    wrapper: "bg-badge-default-bg border-badge-default-border text-badge-default-text",
    dot: "bg-badge-default-text",
  },
  ESSENTIAL: {
    wrapper:
      "bg-badge-essential-bg border-badge-essential-border text-badge-essential-text",
    dot: "bg-badge-essential-text",
  },
  SUCCESS: {
    wrapper:
      "bg-status-success-bg border-status-success-border text-status-success-text",
    dot: "bg-status-success-solid",
  },
  WARNING: {
    wrapper:
      "bg-status-warning-bg border-status-warning-border text-status-warning-text",
    dot: "bg-status-warning-solid",
  },
  DANGER: {
    wrapper:
      "bg-status-danger-bg border-status-danger-border text-status-danger-text",
    dot: "bg-status-danger-solid",
  },
};

export const Badge: React.FC<BadgeProps> = ({
  label,
  variant = "DEFAULT",
  showDot = true,
  className = "",
}) => {
  const style = BADGE_STYLES[variant] ?? BADGE_STYLES.DEFAULT;

  return (
    <span
      className={[
        "inline-flex items-center gap-1.5 h-6 px-2.5 py-0.5 rounded-pill border text-xs font-semibold whitespace-nowrap",
        style.wrapper,
        className,
      ]
        .filter(Boolean)
        .join(" ")}
    >
      {variant === "ESSENTIAL" ? (
        <Lock className="h-3 w-3 shrink-0" aria-hidden="true" />
      ) : showDot ? (
        <span
          className={`h-1.5 w-1.5 rounded-pill shrink-0 ${style.dot}`}
          aria-hidden="true"
        />
      ) : null}
      <span>{label}</span>
    </span>
  );
};
