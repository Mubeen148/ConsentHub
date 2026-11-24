import React, { useState } from "react";
import { useLazyQuery, useMutation, useQuery } from "@apollo/client";
import {
  Eye,
  FlaskConical,
  Pause,
  Play,
  Plus,
  StopCircle,
  Trash2,
} from "lucide-react";
import type {
  ExperimentStatus,
  ExperimentSummary,
  ExperimentVariantInput,
  RegionCode,
  ScreenName,
  SduiScreen,
} from "../../types/contracts";
import {
  CREATE_EXPERIMENT,
  GET_ADMIN_EXPERIMENTS,
  GET_SDUI_SCREEN,
  UPDATE_EXPERIMENT_STATUS,
} from "../../graphql/operations";
import { normalizeRatePercent } from "../../lib/format";
import { Badge, type BadgeVariant } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Modal } from "../../components/ui/Modal";
import {
  EmptyState,
  ErrorState,
  KpiGridSkeleton,
  PreferenceListSkeleton,
} from "../../components/ui/FeedbackStates";
import { useToast } from "../../components/ui/ToastProvider";
import { SduiRenderer } from "../../sdui/SduiRenderer";

function statusVariant(status: ExperimentStatus): BadgeVariant {
  switch (status) {
    case "RUNNING":
      return "SUCCESS";
    case "PAUSED":
      return "WARNING";
    case "DRAFT":
      return "GDPR";
    case "CONCLUDED":
      return "DEFAULT";
    default:
      return "DEFAULT";
  }
}

export const AdminExperimentsPage: React.FC = () => {
  const { showToast } = useToast();

  const { data, loading, error, refetch } = useQuery<{
    adminExperiments: ExperimentSummary[];
  }>(GET_ADMIN_EXPERIMENTS);

  // Preview Variant Modal State
  const [previewInfo, setPreviewInfo] = useState<{
    experimentName: string;
    variantKey: string;
    region: RegionCode;
  } | null>(null);
  const [showRawPreviewJson, setShowRawPreviewJson] = useState(false);

  const [loadPreviewScreen, { data: previewData, loading: previewLoading }] =
    useLazyQuery<{ screen: SduiScreen }>(GET_SDUI_SCREEN, {
      fetchPolicy: "network-only",
    });

  // Create Experiment Modal State
  const [createOpen, setCreateOpen] = useState(false);
  const [slug, setSlug] = useState("");
  const [expName, setExpName] = useState("");
  const [expDescription, setExpDescription] = useState("");
  const [targetScreen, setTargetScreen] = useState<ScreenName>("CONSENT_BANNER");
  const [targetRegion, setTargetRegion] = useState<string>("ALL");
  const [initialStatus, setInitialStatus] = useState<ExperimentStatus>("RUNNING");
  const [variants, setVariants] = useState<ExperimentVariantInput[]>([
    {
      key: "control",
      name: "Control Baseline",
      weight: 50,
      headline: "Your Privacy Choices",
      body: "We use strictly necessary cookies for security and optional analytics only with your consent.",
      primaryCtaLabel: "Accept All",
      secondaryCtaLabel: "Reject Optional",
      layoutTone: "STANDARD",
    },
    {
      key: "high_clarity",
      name: "High-Clarity Trust Copy",
      weight: 50,
      headline: "Transparent Privacy, Zero Dark Patterns",
      body: "Essential cookies keep your session secure. Optional telemetry is off unless you opt in—and revocable in one click.",
      primaryCtaLabel: "Allow All Purposes",
      secondaryCtaLabel: "Essential Only",
      layoutTone: "HIGH_TRUST",
    },
  ]);

  const [updateStatus, { loading: updatingStatus }] = useMutation(
    UPDATE_EXPERIMENT_STATUS,
    {
      refetchQueries: [{ query: GET_ADMIN_EXPERIMENTS }],
    }
  );

  const [createExperiment, { loading: creatingExp }] = useMutation(CREATE_EXPERIMENT, {
    refetchQueries: [{ query: GET_ADMIN_EXPERIMENTS }],
  });

  if (loading && !data) {
    return <KpiGridSkeleton count={2} />;
  }

  if (error) {
    return (
      <ErrorState
        title="Unable to load SDUI A/B experiments"
        message={error.message}
        onRetry={() => void refetch()}
      />
    );
  }

  const experiments = data?.adminExperiments ?? [];
  const totalWeight = variants.reduce((sum, v) => sum + (Number(v.weight) || 0), 0);

  const handleOpenPreview = (exp: ExperimentSummary, variantKey: string) => {
    const region: RegionCode = exp.targetRegion ?? "EU";
    setShowRawPreviewJson(false);
    setPreviewInfo({
      experimentName: exp.name,
      variantKey,
      region,
    });
    void loadPreviewScreen({
      variables: {
        name: exp.targetScreen,
        context: {
          region,
          locale: "en-US",
          previewVariantKey: variantKey,
        },
      },
    });
  };

  const handleTransitionStatus = async (id: string, status: ExperimentStatus) => {
    try {
      await updateStatus({ variables: { id, status } });
      showToast({
        title: `Experiment transitioned to ${status}`,
        variant: "success",
      });
    } catch (err) {
      showToast({
        title: "Unable to change experiment status",
        description: err instanceof Error ? err.message : "Please try again.",
        variant: "danger",
      });
    }
  };

  const handleCreateExperiment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (totalWeight !== 100) {
      showToast({
        title: "Traffic split must total 100%",
        description: `Current variant weights sum to ${totalWeight}%.`,
        variant: "warning",
      });
      return;
    }
    if (!/^[a-z][a-z0-9_-]{2,63}$/.test(slug.trim())) {
      showToast({
        title: "Invalid experiment slug",
        description: "Use lowercase letters, numbers, hyphens, or underscores.",
        variant: "warning",
      });
      return;
    }

    try {
      await createExperiment({
        variables: {
          input: {
            slug: slug.trim(),
            name: expName.trim(),
            description: expDescription.trim(),
            targetScreen,
            targetRegion: targetRegion === "ALL" ? null : (targetRegion as RegionCode),
            status: initialStatus,
            variants: variants.map((v) => ({
              ...v,
              weight: Number(v.weight),
            })),
          },
        },
      });
      setCreateOpen(false);
      setSlug("");
      setExpName("");
      setExpDescription("");
      showToast({
        title: "SDUI A/B experiment created",
        description: `Sticky SHA-256 bucketing active across ${variants.length} variants.`,
        variant: "success",
      });
    } catch (err) {
      showToast({
        title: "Failed to create experiment",
        description: err instanceof Error ? err.message : "Check slug uniqueness.",
        variant: "danger",
      });
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold font-display text-ink">
            Server-Driven UI (SDUI) A/B Experiments
          </h2>
          <p className="text-sm text-ink-secondary">
            Test banner clarity and symmetric microcopy using deterministic SHA-256 sticky bucketing
            without violating GDPR/CCPA choice parity.
          </p>
        </div>

        <Button
          variant="primary"
          onClick={() => setCreateOpen(true)}
          testId="create-experiment-btn"
          leftIcon={<Plus className="h-4 w-4" />}
        >
          New Experiment
        </Button>
      </div>

      {experiments.length === 0 ? (
        <EmptyState
          icon={<FlaskConical className="h-6 w-6" />}
          title="No SDUI experiments configured"
          description="Create a multi-variant experiment to measure opt-in clarity across regions."
          actionLabel="Create First Experiment"
          onAction={() => setCreateOpen(true)}
        />
      ) : (
        <div className="space-y-6">
          {experiments.map((exp) => {
            const baselineVariant = exp.variants[0];
            const baselineRate = baselineVariant
              ? normalizeRatePercent(
                  baselineVariant.consentRate,
                  baselineVariant.conversions,
                  baselineVariant.exposures
                )
              : 0;

            return (
              <Card
                key={exp.id}
                header={
                  <div className="flex flex-wrap items-center gap-2.5">
                    <span>{exp.name}</span>
                    <Badge label={exp.status} variant={statusVariant(exp.status)} />
                    <Badge
                      label={exp.targetScreen}
                      variant="DEFAULT"
                      showDot={false}
                    />
                    <Badge
                      label={exp.targetRegion ? `Region: ${exp.targetRegion}` : "All Regions"}
                      variant={exp.targetRegion === "EU" ? "GDPR" : "DEFAULT"}
                      showDot={false}
                    />
                  </div>
                }
                headerAction={
                  <div className="flex flex-wrap items-center gap-2">
                    {exp.status === "RUNNING" ? (
                      <Button
                        variant="secondary"
                        disabled={updatingStatus}
                        onClick={() => void handleTransitionStatus(exp.id, "PAUSED")}
                        leftIcon={<Pause className="h-3.5 w-3.5" />}
                      >
                        Pause
                      </Button>
                    ) : exp.status === "PAUSED" || exp.status === "DRAFT" ? (
                      <Button
                        variant="secondary"
                        disabled={updatingStatus}
                        onClick={() => void handleTransitionStatus(exp.id, "RUNNING")}
                        leftIcon={<Play className="h-3.5 w-3.5" />}
                      >
                        {exp.status === "DRAFT" ? "Start" : "Resume"}
                      </Button>
                    ) : null}
                    {exp.status !== "CONCLUDED" ? (
                      <Button
                        variant="ghost"
                        disabled={updatingStatus}
                        onClick={() => void handleTransitionStatus(exp.id, "CONCLUDED")}
                        leftIcon={<StopCircle className="h-3.5 w-3.5" />}
                      >
                        Conclude
                      </Button>
                    ) : null}
                  </div>
                }
              >
                <div className="space-y-5">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-sm text-ink-secondary">
                    <p>{exp.description}</p>
                    <span className="text-xs font-mono text-ink-muted shrink-0 tabular-nums">
                      slug: {exp.slug} · {exp.totalExposures} exposures ·{" "}
                      {exp.totalConversions} conversions
                    </span>
                  </div>

                  {/* Variant Cards with Conversion Bars & Lift */}
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {exp.variants.map((variant, idx) => {
                      const ratePct = normalizeRatePercent(
                        variant.consentRate,
                        variant.conversions,
                        variant.exposures
                      );
                      const liftPts = idx === 0 ? 0 : ratePct - baselineRate;

                      return (
                        <div
                          key={variant.key}
                          className="rounded-card border border-border-subtle bg-subtle/40 p-4 flex flex-col justify-between gap-4"
                        >
                          <div className="space-y-2">
                            <div className="flex flex-wrap items-center justify-between gap-2">
                              <div className="flex items-center gap-2">
                                <span className="text-sm font-bold text-ink">
                                  {variant.name}
                                </span>
                                <span className="text-xs font-mono px-2 py-0.5 rounded bg-surface border border-border text-ink-secondary">
                                  {variant.key} · {variant.weight}%
                                </span>
                              </div>
                              {idx > 0 ? (
                                <Badge
                                  label={`${liftPts >= 0 ? "+" : ""}${liftPts.toFixed(
                                    1
                                  )} pts vs ${baselineVariant?.key ?? "control"}`}
                                  variant={liftPts >= 0 ? "SUCCESS" : "WARNING"}
                                  showDot={false}
                                />
                              ) : (
                                <Badge label="Baseline" variant="DEFAULT" showDot={false} />
                              )}
                            </div>

                            <p className="text-xs font-semibold text-ink">
                              “{variant.headline}”
                            </p>
                            <p className="text-xs text-ink-secondary line-clamp-2">
                              {variant.body}
                            </p>
                          </div>

                          <div className="space-y-2">
                            <div className="flex items-center justify-between text-xs tabular-nums">
                              <span className="text-ink-secondary">
                                {variant.conversions} / {variant.exposures} converted
                              </span>
                              <span className="font-mono font-bold text-ink text-sm">
                                {ratePct.toFixed(1)}%
                              </span>
                            </div>
                            <div className="h-2.5 w-full rounded-pill bg-surface border border-border-subtle overflow-hidden">
                              <div
                                className="h-full rounded-pill bg-brand"
                                style={{ width: `${ratePct}%` }}
                              />
                            </div>

                            <div className="pt-1 flex items-center justify-between gap-2">
                              <span className="text-[11px] font-mono text-ink-muted truncate">
                                CTAs: [{variant.primaryCtaLabel}] / [{variant.secondaryCtaLabel}]
                              </span>
                              <Button
                                variant="secondary"
                                onClick={() => handleOpenPreview(exp, variant.key)}
                                testId={`preview-variant-${variant.key}`}
                                leftIcon={<Eye className="h-3.5 w-3.5" />}
                              >
                                Preview SDUI
                              </Button>
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {/* Live SDUI Variant Preview Modal */}
      <Modal
        open={Boolean(previewInfo)}
        onClose={() => setPreviewInfo(null)}
        size="lg"
        title={`Live SDUI Variant Preview: ${previewInfo?.variantKey ?? ""}`}
        subtitle={`${previewInfo?.experimentName ?? ""} · Isolated previewVariantKey (zero assignment side-effects)`}
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() => setShowRawPreviewJson((prev) => !prev)}
            >
              {showRawPreviewJson ? "Show Rendered UI" : "Show SDUI JSON"}
            </Button>
            <Button variant="primary" onClick={() => setPreviewInfo(null)}>
              Done
            </Button>
          </>
        }
      >
        {previewLoading ? (
          <PreferenceListSkeleton rows={3} />
        ) : previewData?.screen ? (
          showRawPreviewJson ? (
            <pre className="rounded-control bg-inverse text-ink-inverse p-4 text-xs font-mono overflow-x-auto max-h-[55dvh]">
              {JSON.stringify(previewData.screen, null, 2)}
            </pre>
          ) : (
            <SduiRenderer
              screen={previewData.screen}
              region={previewInfo?.region}
              inlinePreview
            />
          )
        ) : (
          <EmptyState
            title="Preview unavailable"
            description="Could not compose SDUI preview tree for this variant."
          />
        )}
      </Modal>

      {/* Create New Experiment Modal */}
      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        size="lg"
        title="Create Sticky A/B SDUI Experiment"
        subtitle="Variant traffic split weights must sum to exactly 100%."
        preventClose={creatingExp}
        footer={
          <>
            <Button variant="secondary" onClick={() => setCreateOpen(false)}>
              Cancel
            </Button>
            <Button
              type="submit"
              form="create-experiment-form"
              variant="primary"
              loading={creatingExp}
              disabled={totalWeight !== 100}
              testId="submit-create-experiment-btn"
            >
              Create Experiment ({totalWeight}%)
            </Button>
          </>
        }
      >
        <form
          id="create-experiment-form"
          onSubmit={handleCreateExperiment}
          className="space-y-4"
        >
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label
                htmlFor="exp-slug"
                className="block text-sm font-medium text-ink-secondary mb-1"
              >
                Experiment Slug
              </label>
              <input
                id="exp-slug"
                type="text"
                required
                value={slug}
                onChange={(e) => setSlug(e.target.value.toLowerCase())}
                placeholder="ccpa-optout-clarity-q1"
                className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface font-mono text-sm text-ink"
              />
            </div>
            <div>
              <label
                htmlFor="exp-name"
                className="block text-sm font-medium text-ink-secondary mb-1"
              >
                Experiment Name
              </label>
              <input
                id="exp-name"
                type="text"
                required
                value={expName}
                onChange={(e) => setExpName(e.target.value)}
                placeholder="Q1 Banner Clarity Copy Test"
                className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-sm text-ink"
              />
            </div>
          </div>

          <div>
            <label
              htmlFor="exp-desc"
              className="block text-sm font-medium text-ink-secondary mb-1"
            >
              Hypothesis &amp; Description
            </label>
            <input
              id="exp-desc"
              type="text"
              required
              value={expDescription}
              onChange={(e) => setExpDescription(e.target.value)}
              placeholder="Evaluates concise legal-basis explanation against baseline copy."
              className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-sm text-ink"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label
                htmlFor="exp-screen"
                className="block text-sm font-medium text-ink-secondary mb-1"
              >
                Target Screen
              </label>
              <select
                id="exp-screen"
                value={targetScreen}
                onChange={(e) => setTargetScreen(e.target.value as ScreenName)}
                className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-sm text-ink"
              >
                <option value="CONSENT_BANNER">CONSENT_BANNER</option>
                <option value="PREFERENCE_CENTER">PREFERENCE_CENTER</option>
                <option value="PRIVACY_OVERVIEW">PRIVACY_OVERVIEW</option>
              </select>
            </div>
            <div>
              <label
                htmlFor="exp-region"
                className="block text-sm font-medium text-ink-secondary mb-1"
              >
                Target Region
              </label>
              <select
                id="exp-region"
                value={targetRegion}
                onChange={(e) => setTargetRegion(e.target.value)}
                className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-sm text-ink"
              >
                <option value="ALL">All Regions</option>
                <option value="EU">EU (GDPR)</option>
                <option value="US_CA">US_CA (CCPA)</option>
                <option value="GLOBAL">GLOBAL</option>
              </select>
            </div>
            <div>
              <label
                htmlFor="exp-status"
                className="block text-sm font-medium text-ink-secondary mb-1"
              >
                Initial Status
              </label>
              <select
                id="exp-status"
                value={initialStatus}
                onChange={(e) => setInitialStatus(e.target.value as ExperimentStatus)}
                className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-sm text-ink"
              >
                <option value="RUNNING">RUNNING</option>
                <option value="DRAFT">DRAFT</option>
                <option value="PAUSED">PAUSED</option>
              </select>
            </div>
          </div>

          {/* Variants Editor */}
          <div className="space-y-3 pt-2">
            <div className="flex items-center justify-between">
              <span className="text-sm font-semibold text-ink">
                Variants &amp; Traffic Allocation
              </span>
              <Badge
                label={`Total Weight: ${totalWeight}%`}
                variant={totalWeight === 100 ? "SUCCESS" : "DANGER"}
              />
            </div>

            {variants.map((v, idx) => {
              const updateVariant = (patch: Partial<ExperimentVariantInput>) => {
                setVariants((prev) =>
                  prev.map((item, i) => (i === idx ? { ...item, ...patch } : item))
                );
              };

              return (
                <div
                  key={idx}
                  className="rounded-card border border-border bg-subtle/40 p-3.5 space-y-3"
                >
                  <div className="grid grid-cols-1 sm:grid-cols-4 gap-2.5">
                    <div>
                      <label
                        htmlFor={`var-key-${idx}`}
                        className="block text-xs font-medium text-ink-secondary mb-1"
                      >
                        Variant Key
                      </label>
                      <input
                        id={`var-key-${idx}`}
                        type="text"
                        required
                        value={v.key}
                        onChange={(e) => updateVariant({ key: e.target.value })}
                        className="w-full min-h-tap px-2.5 rounded-control border border-border-control bg-surface font-mono text-xs text-ink"
                      />
                    </div>
                    <div className="sm:col-span-2">
                      <label
                        htmlFor={`var-name-${idx}`}
                        className="block text-xs font-medium text-ink-secondary mb-1"
                      >
                        Variant Name
                      </label>
                      <input
                        id={`var-name-${idx}`}
                        type="text"
                        required
                        value={v.name}
                        onChange={(e) => updateVariant({ name: e.target.value })}
                        className="w-full min-h-tap px-2.5 rounded-control border border-border-control bg-surface text-xs text-ink"
                      />
                    </div>
                    <div className="flex items-end gap-1.5">
                      <div className="flex-1">
                        <label
                          htmlFor={`var-weight-${idx}`}
                          className="block text-xs font-medium text-ink-secondary mb-1"
                        >
                          Weight %
                        </label>
                        <input
                          id={`var-weight-${idx}`}
                          type="number"
                          min={1}
                          max={99}
                          required
                          value={v.weight}
                          onChange={(e) =>
                            updateVariant({ weight: Number(e.target.value) || 0 })
                          }
                          className="w-full min-h-tap px-2.5 rounded-control border border-border-control bg-surface text-xs text-ink tabular-nums"
                        />
                      </div>
                      {variants.length > 2 ? (
                        <button
                          type="button"
                          aria-label={`Remove variant ${v.name}`}
                          onClick={() =>
                            setVariants((prev) => prev.filter((_, i) => i !== idx))
                          }
                          className="inline-flex items-center justify-center min-w-tap min-h-tap rounded-control text-status-danger-text hover:bg-status-danger-bg"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      ) : null}
                    </div>
                  </div>

                  <div>
                    <label
                      htmlFor={`var-headline-${idx}`}
                      className="block text-xs font-medium text-ink-secondary mb-1"
                    >
                      Banner Headline
                    </label>
                    <input
                      id={`var-headline-${idx}`}
                      type="text"
                      required
                      value={v.headline}
                      onChange={(e) => updateVariant({ headline: e.target.value })}
                      className="w-full min-h-tap px-2.5 rounded-control border border-border-control bg-surface text-xs text-ink"
                    />
                  </div>

                  <div>
                    <label
                      htmlFor={`var-body-${idx}`}
                      className="block text-xs font-medium text-ink-secondary mb-1"
                    >
                      Banner Body Copy
                    </label>
                    <input
                      id={`var-body-${idx}`}
                      type="text"
                      required
                      value={v.body}
                      onChange={(e) => updateVariant({ body: e.target.value })}
                      className="w-full min-h-tap px-2.5 rounded-control border border-border-control bg-surface text-xs text-ink"
                    />
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                    <div>
                      <label
                        htmlFor={`var-pcta-${idx}`}
                        className="block text-xs font-medium text-ink-secondary mb-1"
                      >
                        Primary CTA Label
                      </label>
                      <input
                        id={`var-pcta-${idx}`}
                        type="text"
                        required
                        value={v.primaryCtaLabel}
                        onChange={(e) =>
                          updateVariant({ primaryCtaLabel: e.target.value })
                        }
                        className="w-full min-h-tap px-2.5 rounded-control border border-border-control bg-surface text-xs text-ink"
                      />
                    </div>
                    <div>
                      <label
                        htmlFor={`var-scta-${idx}`}
                        className="block text-xs font-medium text-ink-secondary mb-1"
                      >
                        Secondary Reject CTA Label
                      </label>
                      <input
                        id={`var-scta-${idx}`}
                        type="text"
                        required
                        value={v.secondaryCtaLabel}
                        onChange={(e) =>
                          updateVariant({ secondaryCtaLabel: e.target.value })
                        }
                        className="w-full min-h-tap px-2.5 rounded-control border border-border-control bg-surface text-xs text-ink"
                      />
                    </div>
                  </div>
                </div>
              );
            })}

            <Button
              variant="ghost"
              onClick={() =>
                setVariants((prev) => [
                  ...prev,
                  {
                    key: `variant_${prev.length + 1}`,
                    name: `Variant ${prev.length + 1}`,
                    weight: 0,
                    headline: "Your Privacy Preferences",
                    body: "Choose how we process optional analytics and personalization.",
                    primaryCtaLabel: "Accept All",
                    secondaryCtaLabel: "Reject Optional",
                    layoutTone: "STANDARD",
                  },
                ])
              }
              leftIcon={<Plus className="h-4 w-4" />}
            >
              Add Variant
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
