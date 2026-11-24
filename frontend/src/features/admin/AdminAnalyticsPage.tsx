import React, { useState } from "react";
import { useQuery } from "@apollo/client";
import { Link } from "react-router-dom";
import {
  Activity,
  BarChart3,
  FlaskConical,
  Inbox,
  Users,
} from "lucide-react";
import type { ConsentAnalytics } from "../../types/contracts";
import { GET_ADMIN_ANALYTICS } from "../../graphql/operations";
import { formatPercent, normalizeRatePercent } from "../../lib/format";
import { Badge } from "../../components/ui/Badge";
import { Card } from "../../components/ui/Card";
import {
  EmptyState,
  ErrorState,
  KpiGridSkeleton,
  TableSkeleton,
} from "../../components/ui/FeedbackStates";

const DAY_WINDOWS = [7, 30, 90] as const;

export const AdminAnalyticsPage: React.FC = () => {
  const [days, setDays] = useState<number>(30);
  const [hoveredIdx, setHoveredIdx] = useState<number | null>(null);

  const { data, loading, error, refetch } = useQuery<{
    adminAnalytics: ConsentAnalytics;
  }>(GET_ADMIN_ANALYTICS, {
    variables: { days },
  });

  if (loading && !data) {
    return (
      <div className="space-y-6">
        <KpiGridSkeleton count={4} />
        <TableSkeleton rows={5} columns={4} />
      </div>
    );
  }

  if (error) {
    return (
      <ErrorState
        title="Unable to load governance analytics"
        message={error.message}
        onRetry={() => void refetch()}
      />
    );
  }

  const analytics = data?.adminAnalytics;

  if (!analytics) {
    return (
      <EmptyState
        icon={<BarChart3 className="h-6 w-6" />}
        title="No analytics telemetry available"
        description="Consent telemetry will populate once visitors interact with regional banners or preference controls."
        actionLabel="Refresh"
        onAction={() => void refetch()}
      />
    );
  }

  const daily = analytics.dailyMetrics ?? [];
  const chartWidth = 760;
  const chartHeight = 220;
  const padLeft = 42;
  const padRight = 18;
  const padTop = 18;
  const padBottom = 32;
  const innerW = chartWidth - padLeft - padRight;
  const innerH = chartHeight - padTop - padBottom;

  const chartPoints = daily.map((item, idx) => {
    const x =
      daily.length <= 1
        ? padLeft + innerW / 2
        : padLeft + (idx / (daily.length - 1)) * innerW;
    const pct = normalizeRatePercent(
      item.optInRate,
      item.acceptAllCount + item.customCount,
      item.totalInteractions
    );
    const y = padTop + innerH - (pct / 100) * innerH;
    return { x, y, pct, item };
  });

  const linePath = chartPoints
    .map((pt, idx) => `${idx === 0 ? "M" : "L"} ${pt.x.toFixed(1)} ${pt.y.toFixed(1)}`)
    .join(" ");

  const areaPath =
    chartPoints.length > 0
      ? `${linePath} L ${chartPoints[chartPoints.length - 1]!.x.toFixed(1)} ${(
          padTop + innerH
        ).toFixed(1)} L ${chartPoints[0]!.x.toFixed(1)} ${(padTop + innerH).toFixed(
          1
        )} Z`
      : "";

  const activePoint =
    hoveredIdx !== null && chartPoints[hoveredIdx]
      ? chartPoints[hoveredIdx]
      : chartPoints[chartPoints.length - 1] ?? null;

  return (
    <div className="space-y-6">
      {/* Header & Time Range Switcher */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold font-display text-ink">
            Privacy &amp; Consent Telemetry Overview
          </h2>
          <p className="text-sm text-ink-secondary">
            Real-time opt-in conversion rates, regional regime compliance, and DSAR SLA workload.
          </p>
        </div>

        <div
          role="group"
          aria-label="Analytics time range"
          className="inline-flex rounded-control border border-border bg-surface p-1 self-start"
        >
          {DAY_WINDOWS.map((d) => (
            <button
              key={d}
              type="button"
              onClick={() => {
                setHoveredIdx(null);
                setDays(d);
              }}
              data-testid={`analytics-days-${d}`}
              className={[
                "min-h-[36px] px-3.5 rounded text-xs font-semibold transition-colors tabular-nums",
                days === d
                  ? "bg-action text-ink-inverse"
                  : "text-ink-secondary hover:text-ink",
              ].join(" ")}
            >
              Last {d}d
            </button>
          ))}
        </div>
      </div>

      {/* 4 KPI Stat Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 md:gap-6">
        <Card padding="compact">
          <div className="flex items-start justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
              Overall Consent Rate
            </span>
            <Activity className="h-4 w-4 text-brand" aria-hidden="true" />
          </div>
          <p className="mt-2 text-2xl font-bold font-display text-ink tabular-nums">
            {formatPercent(analytics.overallOptInRate)}
          </p>
          <p className="mt-1 text-xs text-ink-secondary tabular-nums">
            Re-consent completion: {formatPercent(analytics.reconsentCompletionRate)}
          </p>
        </Card>

        <Card padding="compact">
          <div className="flex items-start justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
              Consent Decisions
            </span>
            <Users className="h-4 w-4 text-ink-secondary" aria-hidden="true" />
          </div>
          <p className="mt-2 text-2xl font-bold font-display text-ink tabular-nums">
            {analytics.totalConsentEvents.toLocaleString()}
          </p>
          <p className="mt-1 text-xs text-ink-secondary tabular-nums">
            Across {analytics.totalSubjects.toLocaleString()} distinct subjects
          </p>
        </Card>

        <Card padding="compact">
          <div className="flex items-start justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
              Active Experiments
            </span>
            <FlaskConical className="h-4 w-4 text-brand" aria-hidden="true" />
          </div>
          <p className="mt-2 text-2xl font-bold font-display text-ink tabular-nums">
            {analytics.activeExperimentsCount}
          </p>
          <Link
            to="/admin/experiments"
            className="mt-1 inline-block text-xs font-semibold text-brand hover:underline"
          >
            Inspect variant lift →
          </Link>
        </Card>

        <Card padding="compact">
          <div className="flex items-start justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
              Pending DSAR Queue
            </span>
            <Inbox className="h-4 w-4 text-status-warning-text" aria-hidden="true" />
          </div>
          <p className="mt-2 text-2xl font-bold font-display text-ink tabular-nums">
            {analytics.pendingDsarCount}
          </p>
          <Link
            to="/admin/requests"
            className="mt-1 inline-block text-xs font-semibold text-brand hover:underline"
          >
            Review open requests →
          </Link>
        </Card>
      </div>

      {/* Interactive SVG Consent Rate Over Time Area Chart */}
      <Card
        header="Consent Opt-In Rate Over Time"
        headerAction={
          activePoint ? (
            <div className="flex flex-wrap items-center gap-2 text-xs font-mono">
              <span className="text-ink-muted">{activePoint.item.date}:</span>
              <Badge
                label={`${activePoint.pct.toFixed(1)}% Opt-In`}
                variant="GDPR"
                showDot={false}
              />
              <span className="text-ink-secondary hidden sm:inline">
                ({activePoint.item.acceptAllCount} accept ·{" "}
                {activePoint.item.rejectOptionalCount} reject ·{" "}
                {activePoint.item.customCount} custom)
              </span>
            </div>
          ) : null
        }
      >
        {chartPoints.length === 0 ? (
          <EmptyState
            title="No daily time-series points in window"
            description="Select a wider window (30d or 90d) or record new visitor consent interactions."
          />
        ) : (
          <div className="w-full overflow-x-auto">
            <svg
              viewBox={`0 0 ${chartWidth} ${chartHeight}`}
              role="img"
              aria-label={`Daily consent opt-in rate chart over ${days} days`}
              className="w-full min-w-[520px] h-auto select-none"
            >
              <defs>
                <linearGradient id="chConsentAreaGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#1E40AF" stopOpacity="0.22" />
                  <stop offset="100%" stopColor="#1E40AF" stopOpacity="0.01" />
                </linearGradient>
              </defs>

              {[0, 25, 50, 75, 100].map((gridVal) => {
                const gy = padTop + innerH - (gridVal / 100) * innerH;
                return (
                  <g key={gridVal}>
                    <line
                      x1={padLeft}
                      x2={chartWidth - padRight}
                      y1={gy}
                      y2={gy}
                      stroke="#E2DED5"
                      strokeDasharray={gridVal === 0 ? undefined : "4 4"}
                    />
                    <text
                      x={padLeft - 8}
                      y={gy + 4}
                      textAnchor="end"
                      fontSize="11"
                      fill="#5C5F69"
                    >
                      {gridVal}%
                    </text>
                  </g>
                );
              })}

              <path d={areaPath} fill="url(#chConsentAreaGrad)" />
              <path
                d={linePath}
                fill="none"
                stroke="#1E40AF"
                strokeWidth="2.5"
                strokeLinecap="round"
                strokeLinejoin="round"
              />

              {chartPoints.map((pt, idx) => {
                const isSelected = activePoint === pt;
                const showLabel =
                  idx === 0 ||
                  idx === chartPoints.length - 1 ||
                  idx === Math.floor(chartPoints.length / 2);

                return (
                  <g key={pt.item.date}>
                    {showLabel ? (
                      <text
                        x={pt.x}
                        y={chartHeight - 8}
                        textAnchor={
                          idx === 0
                            ? "start"
                            : idx === chartPoints.length - 1
                              ? "end"
                              : "middle"
                        }
                        fontSize="11"
                        fill="#5C5F69"
                      >
                        {pt.item.date.slice(5)}
                      </text>
                    ) : null}
                    <circle
                      cx={pt.x}
                      cy={pt.y}
                      r={isSelected ? 5.5 : 3.5}
                      fill={isSelected ? "#141519" : "#1E40AF"}
                      stroke="#FFFFFF"
                      strokeWidth="2"
                    />
                    <rect
                      x={pt.x - Math.max(8, innerW / Math.max(2, chartPoints.length * 2))}
                      y={padTop}
                      width={Math.max(16, innerW / Math.max(1, chartPoints.length))}
                      height={innerH}
                      fill="transparent"
                      className="cursor-pointer"
                      onMouseEnter={() => setHoveredIdx(idx)}
                      onFocus={() => setHoveredIdx(idx)}
                      tabIndex={0}
                      role="button"
                      aria-label={`${pt.item.date}: ${pt.pct.toFixed(1)}% opt-in (${pt.item.totalInteractions} interactions)`}
                    />
                  </g>
                );
              })}
            </svg>
          </div>
        )}
      </Card>

      {/* Regional Breakdown & Per-Purpose Opt-In Rates */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card header="Consent Rate by Regulatory Region">
          <div className="space-y-5">
            {analytics.regionalBreakdown.map((reg) => {
              const pct = normalizeRatePercent(
                reg.consentRate,
                reg.optInCount,
                reg.totalSubjects
              );
              const badgeVariant =
                reg.regimeName === "GDPR"
                  ? "GDPR"
                  : reg.regimeName === "CCPA"
                    ? "CCPA"
                    : "DEFAULT";

              return (
                <div key={reg.regionCode} className="space-y-2">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <Badge
                        label={`${reg.regionCode} · ${reg.regimeName}`}
                        variant={badgeVariant}
                      />
                      <span className="text-xs text-ink-muted tabular-nums">
                        {reg.totalSubjects} subjects
                      </span>
                    </div>
                    <span className="text-sm font-bold font-mono text-ink tabular-nums">
                      {pct.toFixed(1)}%
                    </span>
                  </div>
                  <div className="h-3 w-full rounded-pill bg-subtle overflow-hidden border border-border-subtle">
                    <div
                      className="h-full rounded-pill bg-brand transition-all duration-300"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                  <div className="flex items-center justify-between text-xs text-ink-muted tabular-nums">
                    <span>Opt-In: {reg.optInCount}</span>
                    <span>Opt-Out / Essential Only: {reg.optOutCount}</span>
                  </div>
                </div>
              );
            })}
          </div>
        </Card>

        <Card header="Per-Purpose Opt-In Breakdown">
          <div className="space-y-4">
            {analytics.purposeOptInRates.map((purpose) => {
              const total = purpose.grantedCount + purpose.deniedCount;
              const pct = purpose.isEssential
                ? 100
                : normalizeRatePercent(purpose.optInRate, purpose.grantedCount, total);

              return (
                <div key={purpose.purposeSlug} className="space-y-1.5">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2 min-w-0">
                      <span className="text-sm font-semibold text-ink truncate">
                        {purpose.purposeName}
                      </span>
                      {purpose.isEssential ? (
                        <Badge label="Always Active" variant="ESSENTIAL" />
                      ) : null}
                    </div>
                    <span className="text-sm font-bold font-mono text-ink tabular-nums shrink-0">
                      {pct.toFixed(1)}%
                    </span>
                  </div>
                  <div className="h-2.5 w-full rounded-pill bg-subtle overflow-hidden border border-border-subtle">
                    <div
                      className={[
                        "h-full rounded-pill transition-all duration-300",
                        purpose.isEssential ? "bg-ink-secondary" : "bg-status-success-solid",
                      ].join(" ")}
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                  <div className="flex items-center justify-between text-xs text-ink-muted tabular-nums">
                    <span>Granted: {purpose.grantedCount}</span>
                    <span>Denied: {purpose.deniedCount}</span>
                  </div>
                </div>
              );
            })}
          </div>
        </Card>
      </div>
    </div>
  );
};
