import React from "react";
import { Blocks } from "lucide-react";
import type { SduiNode } from "../types/contracts";

export interface UnknownComponentFallbackProps {
  node: SduiNode;
}

/**
 * Forward-compatible SDUI fallback for unrecognized server component types.
 * Never throws; renders a non-blocking accessible status block or server-supplied fallbackText.
 */
export const UnknownComponentFallback: React.FC<UnknownComponentFallbackProps> = ({
  node,
}) => {
  const fallbackText =
    typeof node.props?.fallbackText === "string" && node.props.fallbackText.trim().length > 0
      ? node.props.fallbackText.trim()
      : null;

  return (
    <div
      role="status"
      data-testid="sdui-unknown-component"
      data-sdui-id={node.id}
      data-sdui-unknown-type={node.type}
      className="flex items-center gap-2 rounded-control border border-dashed border-border bg-subtle/60 px-3 py-2 text-xs text-ink-muted"
    >
      <Blocks className="h-3.5 w-3.5 shrink-0 text-ink-muted" aria-hidden="true" />
      <span>
        {fallbackText ?? `Unsupported UI component (${String(node.type || "Unknown")})`}
      </span>
    </div>
  );
};
