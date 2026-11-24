import React, { useState } from "react";
import { Check, Code2, Copy } from "lucide-react";
import type { SduiScreen } from "../../types/contracts";
import { Modal } from "../../components/ui/Modal";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";

export interface SduiJsonInspectorModalProps {
  screen: SduiScreen | null;
  onClose: () => void;
}

export const SduiJsonInspectorModal: React.FC<SduiJsonInspectorModalProps> = ({
  screen,
  onClose,
}) => {
  const [copied, setCopied] = useState(false);

  if (!screen) {
    return null;
  }

  const formattedJson = JSON.stringify(screen, null, 2);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(formattedJson);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard API unavailable in non-secure test contexts
    }
  };

  return (
    <Modal
      open={Boolean(screen)}
      onClose={onClose}
      size="lg"
      title="Live Server-Driven UI (SDUI) Payload Inspector"
      subtitle="Exact versioned component tree composed by GraphQL screen(name, context)"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Close Inspector
          </Button>
          <Button
            variant="primary"
            onClick={handleCopy}
            leftIcon={
              copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />
            }
          >
            {copied ? "Copied JSON" : "Copy SDUI Tree"}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-2 bg-subtle border border-border-subtle rounded-control px-3.5 py-2.5">
          <Badge
            label={`${screen.regionCode} · ${screen.regime}`}
            variant={
              screen.regime === "GDPR"
                ? "GDPR"
                : screen.regime === "CCPA"
                  ? "CCPA"
                  : "DEFAULT"
            }
          />
          <Badge label={`Schema v${screen.schemaVersion}`} variant="DEFAULT" showDot={false} />
          <Badge label={`Policy ${screen.policyVersion}`} variant="DEFAULT" showDot={false} />
          <Badge
            label={screen.needsReconsent ? "Needs Re-Consent" : "Up to Date"}
            variant={screen.needsReconsent ? "WARNING" : "SUCCESS"}
          />
          {screen.experiment ? (
            <Badge
              label={`Exp: ${screen.experiment.experimentSlug} (${screen.experiment.variantKey})`}
              variant="GDPR"
              showDot={false}
            />
          ) : null}
        </div>

        <div className="rounded-control bg-inverse text-ink-inverse p-4 overflow-x-auto max-h-[52dvh] border border-border-strong">
          <div className="flex items-center gap-2 text-xs text-badge-gdpr-border mb-2 font-mono">
            <Code2 className="h-3.5 w-3.5" aria-hidden="true" />
            <span>screen(name: {screen.screenName})</span>
          </div>
          <pre
            data-testid="sdui-inspector-json"
            className="text-xs font-mono leading-relaxed whitespace-pre"
          >
            {formattedJson}
          </pre>
        </div>
      </div>
    </Modal>
  );
};
