import React from "react";
import { Lock } from "lucide-react";
import { Badge } from "./Badge";

export interface ConsentToggleProps {
  id?: string;
  label: string;
  checked: boolean;
  onChange: (nextChecked: boolean) => void;
  required?: boolean;
  disabled?: boolean;
  badgeLabel?: string;
  testId?: string;
}

export const ConsentToggle: React.FC<ConsentToggleProps> = ({
  id,
  label,
  checked,
  onChange,
  required = false,
  disabled = false,
  badgeLabel,
  testId,
}) => {
  const isLocked = Boolean(required);
  const isDisabled = isLocked || Boolean(disabled);
  const effectiveChecked = isLocked ? true : checked;

  const handleClick = () => {
    if (isDisabled) {
      return;
    }
    onChange(!effectiveChecked);
  };

  return (
    <div className="inline-flex items-center gap-2.5 shrink-0">
      {isLocked ? (
        <Badge label={badgeLabel || "Always Active"} variant="ESSENTIAL" />
      ) : badgeLabel ? (
        <Badge label={badgeLabel} variant="DEFAULT" showDot={false} />
      ) : null}
      <button
        id={id}
        type="button"
        role="switch"
        data-testid={testId}
        aria-checked={effectiveChecked}
        aria-disabled={isDisabled || undefined}
        aria-label={isLocked ? `${label}, Always Active` : label}
        disabled={disabled && !isLocked}
        onClick={handleClick}
        className={[
          "inline-flex items-center justify-center min-w-tap min-h-tap rounded-control",
          "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--ch-focus-ring)]",
          isDisabled ? "cursor-not-allowed" : "cursor-pointer",
        ]
          .filter(Boolean)
          .join(" ")}
      >
        <span
          className={[
            "relative inline-flex items-center w-11 h-6 rounded-pill border transition-colors duration-150",
            isLocked
              ? "bg-ink-secondary border-ink-secondary"
              : effectiveChecked
                ? "bg-brand border-brand hover:bg-brand-hover"
                : "bg-muted border-border-control",
          ].join(" ")}
        >
          <span
            className={[
              "inline-flex items-center justify-center h-[18px] w-[18px] rounded-pill bg-surface shadow-sm transition-transform duration-150",
              effectiveChecked ? "translate-x-[22px]" : "translate-x-[2px]",
            ].join(" ")}
          >
            {isLocked ? (
              <Lock className="h-2.5 w-2.5 text-ink-secondary" aria-hidden="true" />
            ) : null}
          </span>
        </span>
      </button>
    </div>
  );
};
