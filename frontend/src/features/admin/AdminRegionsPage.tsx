import React, { useEffect, useState } from "react";
import { useMutation, useQuery } from "@apollo/client";
import { Globe2, Save } from "lucide-react";
import type {
  BannerStyle,
  ConsentModel,
  RegionCode,
  RegionRule,
  UpdateRegionRuleInput,
} from "../../types/contracts";
import { GET_REGION_RULES, UPDATE_REGION_RULE } from "../../graphql/operations";
import { formatDateTime } from "../../lib/format";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { ConsentToggle } from "../../components/ui/ConsentToggle";
import {
  EmptyState,
  ErrorState,
  KpiGridSkeleton,
} from "../../components/ui/FeedbackStates";
import { useToast } from "../../components/ui/ToastProvider";

export const AdminRegionsPage: React.FC = () => {
  const { showToast } = useToast();

  const { data, loading, error, refetch } = useQuery<{
    regionRules: RegionRule[];
  }>(GET_REGION_RULES);

  const [drafts, setDrafts] = useState<Record<string, UpdateRegionRuleInput>>({});
  const [savingCode, setSavingCode] = useState<RegionCode | null>(null);

  useEffect(() => {
    if (!data?.regionRules) {
      return;
    }
    const map: Record<string, UpdateRegionRuleInput> = {};
    for (const rule of data.regionRules) {
      map[rule.regionCode] = {
        regionCode: rule.regionCode,
        regimeName: rule.regimeName,
        consentModel: rule.consentModel,
        bannerStyle: rule.bannerStyle,
        showRejectAll: rule.showRejectAll,
        retentionDays: rule.retentionDays,
      };
    }
    setDrafts(map);
  }, [data]);

  const [updateRegionRule] = useMutation<{ updateRegionRule: RegionRule }>(
    UPDATE_REGION_RULE,
    {
      refetchQueries: [{ query: GET_REGION_RULES }],
    }
  );

  if (loading && !data) {
    return <KpiGridSkeleton count={3} />;
  }

  if (error) {
    return (
      <ErrorState
        title="Unable to load regional regulatory rules"
        message={error.message}
        onRetry={() => void refetch()}
      />
    );
  }

  const rules = data?.regionRules ?? [];

  if (rules.length === 0) {
    return (
      <EmptyState
        icon={<Globe2 className="h-6 w-6" />}
        title="No regional rules configured"
        description="Regional compliance rules have not been initialized."
        actionLabel="Reload"
        onAction={() => void refetch()}
      />
    );
  }

  const handleSaveRule = async (code: RegionCode) => {
    const draft = drafts[code];
    if (!draft) {
      return;
    }
    if (draft.retentionDays < 30 || draft.retentionDays > 3650) {
      showToast({
        title: "Invalid retention period",
        description: "Retention days must be between 30 and 3,650 days.",
        variant: "warning",
      });
      return;
    }

    try {
      setSavingCode(code);
      await updateRegionRule({
        variables: {
          input: draft,
        },
      });
      showToast({
        title: `Updated ${code} (${draft.regimeName}) rule`,
        description: `Model: ${draft.consentModel} · Banner: ${draft.bannerStyle} · Retention: ${draft.retentionDays}d.`,
        variant: "success",
      });
    } catch (err) {
      showToast({
        title: `Failed to update ${code} rule`,
        description: err instanceof Error ? err.message : "Please try again.",
        variant: "danger",
      });
    } finally {
      setSavingCode(null);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold font-display text-ink">
          Regional Regulatory Rules
        </h2>
        <p className="text-sm text-ink-secondary">
          Configure jurisdiction-specific consent models, SDUI banner geometry, first-layer reject
          parity, and statutory record retention windows.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {rules.map((rule) => {
          const draft = drafts[rule.regionCode] ?? {
            regionCode: rule.regionCode,
            regimeName: rule.regimeName,
            consentModel: rule.consentModel,
            bannerStyle: rule.bannerStyle,
            showRejectAll: rule.showRejectAll,
            retentionDays: rule.retentionDays,
          };

          const badgeVariant =
            rule.regimeName === "GDPR"
              ? "GDPR"
              : rule.regimeName === "CCPA"
                ? "CCPA"
                : "DEFAULT";

          const updateDraft = (patch: Partial<UpdateRegionRuleInput>) => {
            setDrafts((prev) => ({
              ...prev,
              [rule.regionCode]: {
                ...draft,
                ...patch,
              },
            }));
          };

          return (
            <Card
              key={rule.regionCode}
              header={
                <div className="flex items-center gap-2">
                  <span>{rule.regionCode}</span>
                  <Badge label={rule.regimeName} variant={badgeVariant} />
                </div>
              }
            >
              <div className="space-y-4">
                <div>
                  <label
                    htmlFor={`consent-model-${rule.regionCode}`}
                    className="block text-sm font-medium text-ink-secondary mb-1"
                  >
                    Consent Model
                  </label>
                  <select
                    id={`consent-model-${rule.regionCode}`}
                    value={draft.consentModel}
                    onChange={(e) =>
                      updateDraft({ consentModel: e.target.value as ConsentModel })
                    }
                    className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-sm text-ink"
                  >
                    <option value="OPT_IN">OPT_IN (Prior Explicit Opt-In)</option>
                    <option value="OPT_OUT">OPT_OUT (Notice + Opt-Out)</option>
                    <option value="NOTICE_ONLY">NOTICE_ONLY (Global Baseline)</option>
                  </select>
                </div>

                <div>
                  <label
                    htmlFor={`banner-style-${rule.regionCode}`}
                    className="block text-sm font-medium text-ink-secondary mb-1"
                  >
                    SDUI Banner Shell Style
                  </label>
                  <select
                    id={`banner-style-${rule.regionCode}`}
                    value={draft.bannerStyle}
                    onChange={(e) =>
                      updateDraft({ bannerStyle: e.target.value as BannerStyle })
                    }
                    className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-sm text-ink"
                  >
                    <option value="MODAL">MODAL (Centered Non-Dismissible)</option>
                    <option value="BANNER">BANNER (Bottom Docked Bar)</option>
                    <option value="COMPACT">COMPACT (Minimal Notice Card)</option>
                  </select>
                </div>

                <div>
                  <label
                    htmlFor={`retention-${rule.regionCode}`}
                    className="block text-sm font-medium text-ink-secondary mb-1"
                  >
                    Ledger Retention Period (Days)
                  </label>
                  <input
                    id={`retention-${rule.regionCode}`}
                    type="number"
                    min={30}
                    max={3650}
                    value={draft.retentionDays}
                    onChange={(e) =>
                      updateDraft({ retentionDays: Number(e.target.value) || 365 })
                    }
                    className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-sm text-ink tabular-nums"
                  />
                </div>

                <div className="flex items-center justify-between rounded-control border border-border-subtle p-3">
                  <div>
                    <p className="text-sm font-semibold text-ink">
                      First-Layer Reject CTA
                    </p>
                    <p className="text-xs text-ink-secondary">
                      Symmetric Reject Optional / Do Not Sell button.
                    </p>
                  </div>
                  <ConsentToggle
                    label={`Show Reject All for ${rule.regionCode}`}
                    checked={draft.showRejectAll}
                    onChange={(next) => updateDraft({ showRejectAll: next })}
                  />
                </div>

                <p className="text-xs font-mono text-ink-muted">
                  Last updated: {formatDateTime(rule.updatedAt)}
                </p>

                <Button
                  variant="primary"
                  fullWidth
                  loading={savingCode === rule.regionCode}
                  onClick={() => void handleSaveRule(rule.regionCode)}
                  testId={`save-region-${rule.regionCode}`}
                  leftIcon={<Save className="h-4 w-4" />}
                >
                  Save {rule.regionCode} Rule
                </Button>
              </div>
            </Card>
          );
        })}
      </div>
    </div>
  );
};
