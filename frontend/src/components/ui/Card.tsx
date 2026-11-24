import React from "react";

export type CardPadding = "compact" | "default" | "flush";
export type CardTone = "surface" | "subtle";

export interface CardProps {
  padding?: CardPadding;
  tone?: CardTone;
  header?: React.ReactNode;
  headerAction?: React.ReactNode;
  className?: string;
  children: React.ReactNode;
}

const PADDING_MAP: Record<CardPadding, string> = {
  compact: "p-4",
  default: "p-6",
  flush: "p-0",
};

export const Card: React.FC<CardProps> = ({
  padding = "default",
  tone = "surface",
  header,
  headerAction,
  className = "",
  children,
}) => {
  const bgClass = tone === "subtle" ? "bg-subtle" : "bg-surface";

  return (
    <div
      className={[
        bgClass,
        "border border-border rounded-card shadow-none",
        className,
      ]
        .filter(Boolean)
        .join(" ")}
    >
      {header ? (
        <div className="flex flex-wrap items-center justify-between gap-3 px-6 py-5 border-b border-border-subtle">
          <div className="text-base font-semibold text-ink">{header}</div>
          {headerAction ? <div className="shrink-0">{headerAction}</div> : null}
        </div>
      ) : null}
      <div className={PADDING_MAP[padding]}>{children}</div>
    </div>
  );
};
