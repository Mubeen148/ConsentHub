import React, { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@apollo/client";
import { Check, ChevronDown, ChevronUp, Code2, ShieldCheck } from "lucide-react";
import type {
  ConsentStatusBundle,
  PurposeDecisionInput,
  SduiScreen,
} from "../../types/contracts";
import {
  ACCEPT_ALL_CONSENT,
  GET_MY_CONSENT_HISTORY,
  GET_MY_CONSENT_STATE,
  GET_SDUI_SCREEN,
  REJECT_OPTIONAL_CONSENT,
  SAVE_CONSENT_PREFERENCES,
} from "../../graphql/operations";
import { usePrivacySession } from "../../context/PrivacySessionContext";
import { formatDateTime, formatLegalBasis, formatShortDate } from "../../lib/format";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { ConsentToggle } from "../../components/ui/ConsentToggle";
import {
  EmptyState,
  ErrorState,
  PreferenceListSkeleton,
  SafeMarkdown,
} from "../../components/ui/FeedbackStates";
import { useToast } from "../../components/ui/ToastProvider";
import { SduiRenderer } from "../../sdui/SduiRenderer";

export const PreferenceCenterPage: React.FC = () => {
  const { showToast } = useToast();
  const { region, querySubjectKey, setInspectedScreen } = usePrivacySession();

  const [viewMode, setViewMode] = useState<"GRANULAR" | "SDUI">("GRANULAR");
  const [policyExpanded, setPolicyExpanded] = useState(false);
  const [draftChoices, setDraftChoices] = useState<Record<string, boolean>>({});
  const [activeMutation, setActiveMutation] = useState<
    "SAVE" | "ACCEPT" | "REJECT" | null
  >(null);

  const {
    data: stateData,
    loading: stateLoading,
    error: stateError,
    refetch: refetchState,
  } = useQuery<{ myConsentState: ConsentStatusBundle }>(GET_MY_CONSENT_STATE, {
    variables: {
      subjectKey: querySubjectKey,
      region,
    },
  });

  const {
    data: sduiData,
    loading: sduiLoading,
    refetch: refetchSdui,
  } = useQuery<{ screen: SduiScreen }>(GET_SDUI_SCREEN, {
    variables: {
      name: "PREFERENCE_CENTER",
      context: {
        subjectKey: querySubjectKey,
        region,
        locale: "en-US",
      },
    },
  });

  const consentState = stateData?.myConsentState;
  const sduiScreen = sduiData?.screen ?? null;

  useEffect(() => {
    if (!consentState) {
      return;
    }
    const nextMap: Record<string, boolean> = {};
    for (const decision of consentState.decisions) {
      nextMap[decision.slug] = decision.isEssential ? true : decision.granted;
    }
    setDraftChoices(nextMap);
  }, [consentState]);

  const refetchQueries = useMemo(
    () => [
      { query: GET_MY_CONSENT_STATE, variables: { subjectKey: querySubjectKey, region } },
      { query: GET_MY_CONSENT_HISTORY, variables: { subjectKey: querySubjectKey, limit: 50 } },
      {
        query: GET_SDUI_SCREEN,
        variables: {
          name: "PREFERENCE_CENTER",
          context: { subjectKey: querySubjectKey, region, locale: "en-US" },
        },
      },
      {
        query: GET_SDUI_SCREEN,
        variables: {
          name: "CONSENT_BANNER",
          context: { subjectKey: querySubjectKey, region, locale: "en-US" },
        },
      },
    ],
    [querySubjectKey, region]
  );

  const [savePreferences] = useMutation<{ saveConsentPreferences: ConsentStatusBundle }>(
    SAVE_CONSENT_PREFERENCES,
    { refetchQueries }
  );
  const [acceptAll] = useMutation<{ acceptAllConsent: ConsentStatusBundle }>(
    ACCEPT_ALL_CONSENT,
    { refetchQueries }
  );
  const [rejectOptional] = useMutation<{ rejectOptionalConsent: ConsentStatusBundle }>(
    REJECT_OPTIONAL_CONSENT,
    { refetchQueries }
  );

  if (stateLoading && !consentState) {
    return (
      <div className="space-y-6">
        <PreferenceListSkeleton rows={5} />
      </div>
    );
  }

  if (stateError) {
    return (
      <ErrorState
        title="Unable to load preference controls"
        message={stateError.message}
        onRetry={() => {
          void refetchState();
          void refetchSdui();
        }}
      />
    );
  }

  if (!consentState || consentState.decisions.length === 0) {
    return (
      <EmptyState
        title="No consent purposes configured"
        description="There are currently no active processing purposes configured for this environment."
        actionLabel="Reload"
        onAction={() => void refetchState()}
      />
    );
  }

  const hasUnsavedChanges = consentState.decisions.some((d) => {
    if (d.isEssential) {
      return false;
    }
    return Boolean(draftChoices[d.slug]) !== d.granted;
  });

  const handleSaveCustom = async () => {
    try {
      setActiveMutation("SAVE");
      const decisions: PurposeDecisionInput[] = consentState.decisions.map((d) => ({
        purposeSlug: d.slug,
        granted: d.isEssential ? true : Boolean(draftChoices[d.slug]),
      }));
      await savePreferences({
        variables: {
          input: {
            subjectKey: querySubjectKey,
            region,
            source: "PREFERENCE_CENTER",
            experimentId: sduiScreen?.experiment?.experimentId,
            variantKey: sduiScreen?.experiment?.variantKey,
            decisions,
          },
        },
      });
      showToast({
        title: "Consent preferences saved",
        description: `Recorded immutable SHA-256 receipt under Policy ${consentState.activePolicyVersion.versionLabel}.`,
        variant: "success",
      });
    } catch (err) {
      showToast({
        title: "Failed to save preferences",
        description: err instanceof Error ? err.message : "Please try again.",
        variant: "danger",
      });
    } finally {
      setActiveMutation(null);
    }
  };

  const handleAcceptAll = async () => {
    try {
      setActiveMutation("ACCEPT");
      await acceptAll({
        variables: {
          subjectKey: querySubjectKey,
          region,
          source: "PREFERENCE_CENTER",
          experimentId: sduiScreen?.experiment?.experimentId,
          variantKey: sduiScreen?.experiment?.variantKey,
        },
      });
      showToast({
        title: "All purposes granted",
        description: `Active under Policy ${consentState.activePolicyVersion.versionLabel} (${region}).`,
        variant: "success",
      });
    } catch (err) {
      showToast({
        title: "Failed to grant consent",
        description: err instanceof Error ? err.message : "Please try again.",
        variant: "danger",
      });
    } finally {
      setActiveMutation(null);
    }
  };

  const handleRejectOptional = async () => {
    try {
      setActiveMutation("REJECT");
      await rejectOptional({
        variables: {
          subjectKey: querySubjectKey,
          region,
          source: "PREFERENCE_CENTER",
          experimentId: sduiScreen?.experiment?.experimentId,
          variantKey: sduiScreen?.experiment?.variantKey,
        },
      });
      showToast({
        title: "Optional consent withdrawn",
        description: "All non-essential purposes have been turned off immediately.",
        variant: "success",
      });
    } catch (err) {
      showToast({
        title: "Failed to withdraw optional consent",
        description: err instanceof Error ? err.message : "Please try again.",
        variant: "danger",
      });
    } finally {
      setActiveMutation(null);
    }
  };

  const rejectButtonLabel =
    consentState.regionRule.regimeName === "CCPA"
      ? "Do Not Sell or Share / Reject Optional"
      : "Withdraw Optional Consent";

  return (
    <div className="space-y-6 max-w-3xl mx-auto">
      {/* Header & Mode Switcher */}
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div className="space-y-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <Badge
              label={`${consentState.regionRule.regionCode} · ${consentState.regionRule.regimeName}`}
              variant={
                consentState.regionRule.regimeName === "GDPR"
                  ? "GDPR"
                  : consentState.regionRule.regimeName === "CCPA"
                    ? "CCPA"
                    : "DEFAULT"
              }
            />
            <Badge
              label={`Policy ${consentState.activePolicyVersion.versionLabel}`}
              variant="DEFAULT"
              showDot={false}
            />
          </div>
          <h1 className="text-2xl font-bold font-display text-ink">
            Granular Consent Preferences
          </h1>
          <p className="text-sm text-ink-secondary">
            Withdrawing optional consent is always a single click—identical in effort to opting in.
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <div
            role="group"
            aria-label="Preference rendering mode"
            className="inline-flex rounded-control border border-border bg-surface p-1"
          >
            <button
              type="button"
              onClick={() => setViewMode("GRANULAR")}
              className={[
                "min-h-[36px] px-3 rounded text-xs font-semibold transition-colors",
                viewMode === "GRANULAR"
                  ? "bg-action text-ink-inverse"
                  : "text-ink-secondary hover:text-ink",
              ].join(" ")}
            >
              Interactive Controls
            </button>
            <button
              type="button"
              onClick={() => setViewMode("SDUI")}
              data-testid="pref-sdui-mode-btn"
              className={[
                "min-h-[36px] px-3 rounded text-xs font-semibold transition-colors",
                viewMode === "SDUI"
                  ? "bg-action text-ink-inverse"
                  : "text-ink-secondary hover:text-ink",
              ].join(" ")}
            >
              SDUI Renderer
            </button>
          </div>

          {sduiScreen ? (
            <Button
              variant="secondary"
              onClick={() => setInspectedScreen(sduiScreen)}
              aria-label="Inspect Preference Center SDUI JSON"
              leftIcon={<Code2 className="h-4 w-4" />}
            >
              JSON
            </Button>
          ) : null}
        </div>
      </div>

      {/* Active Policy Summary & Collapsible Full Text */}
      <Card padding="compact" tone="subtle">
        <div className="space-y-3">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <ShieldCheck className="h-4 w-4 text-brand shrink-0" aria-hidden="true" />
              <span className="text-sm font-semibold text-ink">
                {consentState.activePolicyVersion.title} (
                {consentState.activePolicyVersion.versionLabel})
              </span>
            </div>
            <span className="text-xs font-mono text-ink-muted">
              Published {formatShortDate(consentState.activePolicyVersion.publishedAt)}
            </span>
          </div>
          <p className="text-sm text-ink-secondary">
            {consentState.activePolicyVersion.summary}
          </p>
          <div>
            <button
              type="button"
              aria-expanded={policyExpanded}
              onClick={() => setPolicyExpanded((prev) => !prev)}
              className="inline-flex items-center gap-1.5 min-h-tap px-2 -ml-2 rounded-control text-xs font-semibold text-brand hover:bg-brand-subtle transition-colors"
            >
              <span>
                {policyExpanded ? "Hide full policy notice" : "Read full policy notice"}
              </span>
              {policyExpanded ? (
                <ChevronUp className="h-4 w-4" aria-hidden="true" />
              ) : (
                <ChevronDown className="h-4 w-4" aria-hidden="true" />
              )}
            </button>
          </div>
          {policyExpanded ? (
            <div className="p-4 rounded-control bg-surface border border-border-subtle">
              <SafeMarkdown content={consentState.activePolicyVersion.contentMarkdown} />
            </div>
          ) : null}
        </div>
      </Card>

      {viewMode === "SDUI" ? (
        sduiLoading && !sduiScreen ? (
          <PreferenceListSkeleton rows={4} />
        ) : sduiScreen ? (
          <SduiRenderer
            screen={sduiScreen}
            subjectKey={querySubjectKey}
            region={region}
          />
        ) : (
          <EmptyState
            title="SDUI Preference Screen Unavailable"
            description="Switch back to Interactive Controls or retry."
            actionLabel="Use Interactive Controls"
            onAction={() => setViewMode("GRANULAR")}
          />
        )
      ) : (
        <>
          {/* Granular Per-Purpose List */}
          <Card padding="flush">
            <div className="divide-y divide-border-subtle">
              {consentState.decisions.map((item) => {
                const checked = item.isEssential
                  ? true
                  : Boolean(draftChoices[item.slug]);

                return (
                  <div
                    key={item.slug}
                    data-testid={`purpose-row-${item.slug}`}
                    className="px-5 py-5 md:px-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4"
                  >
                    <div className="space-y-1.5 min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <label
                          htmlFor={`purpose-switch-${item.slug}`}
                          className="text-base font-semibold text-ink cursor-pointer"
                        >
                          {item.name}
                        </label>
                        <Badge
                          label={formatLegalBasis(item.legalBasis)}
                          variant={item.isEssential ? "ESSENTIAL" : "DEFAULT"}
                          showDot={false}
                        />
                      </div>
                      <p className="text-sm text-ink-secondary leading-relaxed">
                        {item.description}
                      </p>
                      <div className="flex flex-wrap items-center gap-3 text-xs text-ink-muted font-mono pt-0.5">
                        <span>slug: {item.slug}</span>
                        {item.source ? <span>source: {item.source}</span> : null}
                        {item.updatedAt ? (
                          <span>updated: {formatDateTime(item.updatedAt)}</span>
                        ) : null}
                      </div>
                    </div>

                    <ConsentToggle
                      id={`purpose-switch-${item.slug}`}
                      label={item.name}
                      checked={checked}
                      required={item.isEssential}
                      disabled={activeMutation !== null}
                      testId={`toggle-purpose-${item.slug}`}
                      onChange={(next) =>
                        setDraftChoices((prev) => ({
                          ...prev,
                          [item.slug]: next,
                        }))
                      }
                    />
                  </div>
                );
              })}
            </div>
          </Card>

          {/* Symmetric Action Bar */}
          <div className="flex flex-col sm:flex-row flex-wrap items-stretch sm:items-center justify-between gap-3 pt-1">
            <div className="flex flex-col sm:flex-row gap-2.5">
              <Button
                variant="primary"
                loading={activeMutation === "SAVE"}
                disabled={activeMutation !== null}
                onClick={handleSaveCustom}
                testId="save-preferences-btn"
                leftIcon={<Check className="h-4 w-4" />}
              >
                {hasUnsavedChanges ? "Save Preferences *" : "Save Preferences"}
              </Button>
              <Button
                variant="secondary"
                loading={activeMutation === "REJECT"}
                disabled={activeMutation !== null}
                onClick={handleRejectOptional}
                testId="reject-optional-btn"
              >
                {rejectButtonLabel}
              </Button>
            </div>

            <Button
              variant="secondary"
              loading={activeMutation === "ACCEPT"}
              disabled={activeMutation !== null}
              onClick={handleAcceptAll}
              testId="accept-all-btn"
            >
              Accept All Purposes
            </Button>
          </div>
        </>
      )}
    </div>
  );
};
