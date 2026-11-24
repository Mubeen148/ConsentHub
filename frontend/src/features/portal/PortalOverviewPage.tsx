import React from "react";
import { useQuery } from "@apollo/client";
import { Link } from "react-router-dom";
import {
  AlertTriangle,
  ArrowRight,
  Code2,
  FileSpreadsheet,
  History,
  RotateCcw,
  ShieldCheck,
  SlidersHorizontal,
} from "lucide-react";
import type { ConsentStatusBundle, SduiScreen } from "../../types/contracts";
import { GET_MY_CONSENT_STATE, GET_SDUI_SCREEN } from "../../graphql/operations";
import { usePrivacySession } from "../../context/PrivacySessionContext";
import { formatDateTime } from "../../lib/format";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import {
  EmptyState,
  ErrorState,
  KpiGridSkeleton,
  PreferenceListSkeleton,
} from "../../components/ui/FeedbackStates";
import { useToast } from "../../components/ui/ToastProvider";
import { SduiRenderer } from "../../sdui/SduiRenderer";

export const PortalOverviewPage: React.FC = () => {
  const { showToast } = useToast();
  const {
    user,
    region,
    anonSubjectKey,
    querySubjectKey,
    openConsentBanner,
    resetVisitorIdentity,
    setInspectedScreen,
  } = usePrivacySession();

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
    data: overviewScreenData,
    loading: screenLoading,
    error: screenError,
    refetch: refetchScreen,
  } = useQuery<{ screen: SduiScreen }>(GET_SDUI_SCREEN, {
    variables: {
      name: "PRIVACY_OVERVIEW",
      context: {
        subjectKey: querySubjectKey,
        region,
        locale: "en-US",
      },
    },
  });

  const handleRetry = () => {
    void refetchState();
    void refetchScreen();
  };

  if (stateLoading && !stateData) {
    return (
      <div className="space-y-6">
        <KpiGridSkeleton count={4} />
        <PreferenceListSkeleton rows={3} />
      </div>
    );
  }

  if (stateError || screenError) {
    const errMsg =
      stateError?.message ??
      screenError?.message ??
      "Could not connect to the ConsentHub GraphQL service.";
    return (
      <ErrorState
        title="Unable to load Privacy Center overview"
        message={errMsg}
        onRetry={handleRetry}
      />
    );
  }

  const consentState = stateData?.myConsentState;
  const overviewScreen = overviewScreenData?.screen ?? null;

  if (!consentState) {
    return (
      <EmptyState
        title="No privacy configuration available"
        description="Active privacy policy and regional rules have not been initialized yet."
        actionLabel="Retry Loading"
        onAction={handleRetry}
      />
    );
  }

  const grantedCount = consentState.decisions.filter((d) => d.granted).length;
  const totalCount = consentState.decisions.length;
  const regimeBadgeVariant =
    consentState.regionRule.regimeName === "GDPR"
      ? "GDPR"
      : consentState.regionRule.regimeName === "CCPA"
        ? "CCPA"
        : "DEFAULT";

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div className="space-y-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <Badge
              label={`${consentState.regionRule.regionCode} · ${consentState.regionRule.regimeName}`}
              variant={regimeBadgeVariant}
            />
            <Badge
              label={`Active Policy ${consentState.activePolicyVersion.versionLabel}`}
              variant="DEFAULT"
              showDot={false}
            />
            <Badge
              label={
                consentState.needsReconsent
                  ? "Re-Consent Required"
                  : consentState.hasConsented
                    ? "Consent Verified"
                    : "Awaiting Choice"
              }
              variant={
                consentState.needsReconsent
                  ? "WARNING"
                  : consentState.hasConsented
                    ? "SUCCESS"
                    : "DEFAULT"
              }
            />
          </div>
          <h1 className="text-2xl font-bold font-display text-ink">
            Your Privacy &amp; Data Rights Center
          </h1>
          <p className="text-base text-ink-secondary max-w-2xl">
            Inspect every active data processing purpose, verify SHA-256 consent proofs, and exercise
            GDPR &amp; CCPA portability or erasure rights with symmetric controls.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5 shrink-0">
          {overviewScreen ? (
            <Button
              variant="secondary"
              onClick={() => setInspectedScreen(overviewScreen)}
              testId="inspect-sdui-overview-btn"
              leftIcon={<Code2 className="h-4 w-4" />}
            >
              Inspect SDUI JSON
            </Button>
          ) : null}
          {!user ? (
            <Button
              variant="ghost"
              onClick={() => {
                resetVisitorIdentity();
                showToast({
                  title: "New anonymous visitor simulated",
                  description:
                    "Generated a fresh anon:<uuid> subject key and reopened the first-visit banner.",
                  variant: "info",
                });
              }}
              leftIcon={<RotateCcw className="h-4 w-4" />}
            >
              Simulate New Visitor
            </Button>
          ) : null}
        </div>
      </div>

      {/* Actionable Re-Consent Required Banner */}
      {consentState.needsReconsent ? (
        <div
          role="alert"
          data-testid="reconsent-required-callout"
          className="rounded-card border border-status-warning-border bg-status-warning-bg px-5 py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4"
        >
          <div className="flex items-start gap-3">
            <AlertTriangle
              className="h-5 w-5 text-status-warning-text shrink-0 mt-0.5"
              aria-hidden="true"
            />
            <div>
              <p className="text-sm font-semibold text-status-warning-text">
                Action Required: Privacy Policy {consentState.activePolicyVersion.versionLabel} Published
              </p>
              <p className="mt-0.5 text-sm text-ink">
                {consentState.consentedPolicyVersion
                  ? `You previously consented under ${consentState.consentedPolicyVersion}. Material updates in ${consentState.activePolicyVersion.versionLabel} require renewed review.`
                  : `Review and confirm your privacy choices under ${consentState.activePolicyVersion.title} (${consentState.activePolicyVersion.versionLabel}).`}
              </p>
            </div>
          </div>
          <Button variant="primary" onClick={openConsentBanner}>
            Review &amp; Confirm
          </Button>
        </div>
      ) : null}

      {/* Summary KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 md:gap-6">
        <Card padding="compact">
          <div className="space-y-1.5">
            <span className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
              Active Policy Version
            </span>
            <p className="text-xl font-bold font-display text-ink tabular-nums">
              {consentState.activePolicyVersion.versionLabel}
            </p>
            <p className="text-xs text-ink-secondary truncate">
              {consentState.activePolicyVersion.title}
            </p>
          </div>
        </Card>

        <Card padding="compact">
          <div className="space-y-1.5">
            <span className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
              Regulatory Regime
            </span>
            <p className="text-xl font-bold font-display text-ink">
              {consentState.regionRule.regimeName}
            </p>
            <p className="text-xs text-ink-secondary">
              Model: {consentState.regionRule.consentModel.replace("_", "-")} ·{" "}
              {consentState.regionRule.retentionDays}d retention
            </p>
          </div>
        </Card>

        <Card padding="compact">
          <div className="space-y-1.5">
            <span className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
              Enabled Purposes
            </span>
            <p className="text-xl font-bold font-display text-ink tabular-nums">
              {grantedCount} / {totalCount}
            </p>
            <p className="text-xs text-ink-secondary">
              {consentState.updatedAt
                ? `Updated ${formatDateTime(consentState.updatedAt)}`
                : "Default regional state"}
            </p>
          </div>
        </Card>

        <Card padding="compact">
          <div className="space-y-1.5">
            <span className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
              Subject Namespace
            </span>
            <p className="text-sm font-mono font-semibold text-ink truncate pt-1">
              {user ? user.subjectKey : anonSubjectKey}
            </p>
            <p className="text-xs text-ink-secondary">
              {user ? `Authenticated (${user.email})` : "Pseudonymous Visitor"}
            </p>
          </div>
        </Card>
      </div>

      {/* Live Server-Driven UI (SDUI) Overview Section */}
      <Card
        header={
          <div className="flex items-center gap-2">
            <ShieldCheck className="h-4 w-4 text-brand" aria-hidden="true" />
            <span>Server-Driven Privacy Summary</span>
          </div>
        }
        headerAction={
          overviewScreen ? (
            <Badge
              label={`SDUI v${overviewScreen.schemaVersion} · ${overviewScreen.screenName}`}
              variant="GDPR"
              showDot={false}
            />
          ) : null
        }
      >
        {screenLoading && !overviewScreen ? (
          <PreferenceListSkeleton rows={2} />
        ) : overviewScreen ? (
          <SduiRenderer
            screen={overviewScreen}
            subjectKey={querySubjectKey}
            region={region}
          />
        ) : (
          <EmptyState
            title="SDUI screen unavailable"
            description="No PRIVACY_OVERVIEW component tree was returned for this context."
          />
        )}
      </Card>

      {/* Self-Service Action Hub Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 md:gap-6">
        <Card className="flex flex-col justify-between">
          <div className="space-y-3">
            <div className="h-10 w-10 rounded-control bg-subtle flex items-center justify-center text-ink">
              <SlidersHorizontal className="h-5 w-5" aria-hidden="true" />
            </div>
            <div className="space-y-1">
              <h2 className="text-lg font-semibold font-display text-ink">
                Granular Preferences
              </h2>
              <p className="text-sm text-ink-secondary">
                Toggle analytics, personalization, marketing, and cross-context ad sharing
                with 1-click withdrawal parity.
              </p>
            </div>
            <div className="pt-2">
              <Link to="/portal/preferences" className="inline-flex w-full">
                <Button
                  variant="secondary"
                  fullWidth
                  rightIcon={<ArrowRight className="h-4 w-4" />}
                >
                  Manage Purposes
                </Button>
              </Link>
            </div>
          </div>
        </Card>

        <Card className="flex flex-col justify-between">
          <div className="space-y-3">
            <div className="h-10 w-10 rounded-control bg-subtle flex items-center justify-center text-ink">
              <History className="h-5 w-5" aria-hidden="true" />
            </div>
            <div className="space-y-1">
              <h2 className="text-lg font-semibold font-display text-ink">
                Immutable Consent Ledger
              </h2>
              <p className="text-sm text-ink-secondary">
                Inspect chronological consent state changes, policy versions, A/B variants,
                and copyable SHA-256 receipt hashes.
              </p>
            </div>
            <div className="pt-2">
              <Link to="/portal/history" className="inline-flex w-full">
                <Button
                  variant="secondary"
                  fullWidth
                  rightIcon={<ArrowRight className="h-4 w-4" />}
                >
                  View Proof Timeline
                </Button>
              </Link>
            </div>
          </div>
        </Card>

        <Card className="flex flex-col justify-between">
          <div className="space-y-3">
            <div className="h-10 w-10 rounded-control bg-subtle flex items-center justify-center text-ink">
              <FileSpreadsheet className="h-5 w-5" aria-hidden="true" />
            </div>
            <div className="space-y-1">
              <h2 className="text-lg font-semibold font-display text-ink">
                Data Rights (DSAR)
              </h2>
              <p className="text-sm text-ink-secondary">
                Download your complete machine-readable JSON portability archive or submit a
                tracked Right-to-Erasure deletion request.
              </p>
            </div>
            <div className="pt-2">
              <Link to="/portal/requests" className="inline-flex w-full">
                <Button
                  variant="secondary"
                  fullWidth
                  rightIcon={<ArrowRight className="h-4 w-4" />}
                >
                  Open DSAR Center
                </Button>
              </Link>
            </div>
          </div>
        </Card>
      </div>
    </div>
  );
};
