import React, { useMemo, useState } from "react";
import { useQuery } from "@apollo/client";
import { useNavigate } from "react-router-dom";
import { Check, Copy, History, ShieldCheck } from "lucide-react";
import type { ConsentHistoryEntry } from "../../types/contracts";
import { GET_MY_CONSENT_HISTORY } from "../../graphql/operations";
import { usePrivacySession } from "../../context/PrivacySessionContext";
import { formatDateTime, truncateHash } from "../../lib/format";
import { Badge } from "../../components/ui/Badge";
import { Card } from "../../components/ui/Card";
import {
  EmptyState,
  ErrorState,
  TableSkeleton,
} from "../../components/ui/FeedbackStates";
import { useToast } from "../../components/ui/ToastProvider";

type TransitionFilter = "ALL" | "GRANTED" | "WITHDRAWN";

export const ConsentHistoryPage: React.FC = () => {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const { querySubjectKey, anonSubjectKey, user } = usePrivacySession();

  const [filter, setFilter] = useState<TransitionFilter>("ALL");
  const [purposeFilter, setPurposeFilter] = useState<string>("ALL");
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const { data, loading, error, refetch } = useQuery<{
    myConsentHistory: ConsentHistoryEntry[];
  }>(GET_MY_CONSENT_HISTORY, {
    variables: {
      subjectKey: querySubjectKey,
      limit: 100,
    },
  });

  const entries = data?.myConsentHistory ?? [];

  const distinctPurposes = useMemo(() => {
    const map = new Map<string, string>();
    for (const item of entries) {
      map.set(item.purposeSlug, item.purposeName);
    }
    return Array.from(map.entries());
  }, [entries]);

  const filteredEntries = useMemo(() => {
    return entries.filter((entry) => {
      if (filter === "GRANTED" && !entry.newGranted) {
        return false;
      }
      if (filter === "WITHDRAWN" && entry.newGranted) {
        return false;
      }
      if (purposeFilter !== "ALL" && entry.purposeSlug !== purposeFilter) {
        return false;
      }
      return true;
    });
  }, [entries, filter, purposeFilter]);

  const handleCopyHash = async (entry: ConsentHistoryEntry) => {
    try {
      await navigator.clipboard.writeText(entry.proofHash);
      setCopiedId(entry.id);
      showToast({
        title: "SHA-256 receipt hash copied",
        description: truncateHash(entry.proofHash, 24),
        variant: "info",
      });
      window.setTimeout(() => setCopiedId(null), 2000);
    } catch {
      showToast({
        title: "Clipboard copy unavailable",
        description: entry.proofHash,
        variant: "warning",
      });
    }
  };

  if (loading && !data) {
    return (
      <div className="space-y-6">
        <TableSkeleton rows={6} columns={5} />
      </div>
    );
  }

  if (error) {
    return (
      <ErrorState
        title="Unable to load immutable consent ledger"
        message={error.message}
        onRetry={() => void refetch()}
      />
    );
  }

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div className="space-y-1.5">
          <div className="flex items-center gap-2">
            <Badge label="Append-Only PostgreSQL Ledger" variant="SUCCESS" />
            <span className="text-xs font-mono text-ink-muted">
              {user ? user.subjectKey : anonSubjectKey}
            </span>
          </div>
          <h1 className="text-2xl font-bold font-display text-ink">
            Cryptographic Consent History
          </h1>
          <p className="text-sm text-ink-secondary">
            Every consent choice generates an immutable SHA-256 receipt linking your subject key,
            purpose, policy version, region, and timestamp.
          </p>
        </div>
      </div>

      {/* Filter Toolbar */}
      <Card padding="compact">
        <div className="flex flex-col sm:flex-row flex-wrap items-stretch sm:items-center justify-between gap-3">
          <div
            role="group"
            aria-label="Filter by consent state transition"
            className="inline-flex rounded-control border border-border bg-subtle/60 p-1"
          >
            {(["ALL", "GRANTED", "WITHDRAWN"] as TransitionFilter[]).map((option) => (
              <button
                key={option}
                type="button"
                onClick={() => setFilter(option)}
                className={[
                  "min-h-[36px] px-3 rounded text-xs font-semibold transition-colors",
                  filter === option
                    ? "bg-surface text-ink shadow-sm border border-border"
                    : "text-ink-secondary hover:text-ink",
                ].join(" ")}
              >
                {option === "ALL"
                  ? "All Events"
                  : option === "GRANTED"
                    ? "Granted"
                    : "Withdrawn"}
              </button>
            ))}
          </div>

          <div className="flex items-center gap-2.5">
            <label htmlFor="history-purpose-filter" className="text-xs font-medium text-ink-secondary">
              Purpose:
            </label>
            <select
              id="history-purpose-filter"
              value={purposeFilter}
              onChange={(e) => setPurposeFilter(e.target.value)}
              className="min-h-tap px-3 rounded-control border border-border-control bg-surface text-sm text-ink"
            >
              <option value="ALL">All Purposes ({entries.length})</option>
              {distinctPurposes.map(([slug, name]) => (
                <option key={slug} value={slug}>
                  {name}
                </option>
              ))}
            </select>
          </div>
        </div>
      </Card>

      {filteredEntries.length === 0 ? (
        <EmptyState
          icon={<History className="h-6 w-6" />}
          title={
            entries.length === 0
              ? "No consent receipts recorded yet"
              : "No ledger entries match your filter"
          }
          description={
            entries.length === 0
              ? "Save your preferences or respond to the consent banner to generate your first SHA-256 consent proof."
              : "Clear or adjust the transition and purpose filters above to inspect all historical receipts."
          }
          actionLabel={entries.length === 0 ? "Configure Preferences" : "Clear Filters"}
          onAction={() => {
            if (entries.length === 0) {
              navigate("/portal/preferences");
            } else {
              setFilter("ALL");
              setPurposeFilter("ALL");
            }
          }}
        />
      ) : (
        <ol
          aria-label="Chronological consent ledger timeline"
          className="relative border-l-2 border-border ml-3 md:ml-4 space-y-4 pl-5 md:pl-6"
        >
          {filteredEntries.map((entry) => {
            const isCopied = copiedId === entry.id;
            const regionVariant =
              entry.regionCode === "EU"
                ? "GDPR"
                : entry.regionCode === "US_CA"
                  ? "CCPA"
                  : "DEFAULT";

            return (
              <li
                key={entry.id}
                data-testid={`consent-history-item-${entry.purposeSlug}`}
                className="relative"
              >
                <span
                  aria-hidden="true"
                  className={[
                    "absolute -left-[27px] md:-left-[31px] top-5 h-3.5 w-3.5 rounded-pill border-2 border-surface",
                    entry.newGranted ? "bg-status-success-solid" : "bg-ink-muted",
                  ].join(" ")}
                />

                <Card padding="compact">
                  <div className="space-y-3">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-base font-semibold text-ink">
                          {entry.purposeName}
                        </span>
                        <Badge
                          label={entry.newGranted ? "Granted" : "Withdrawn"}
                          variant={entry.newGranted ? "SUCCESS" : "WARNING"}
                        />
                        <Badge
                          label={`Policy ${entry.policyVersionLabel}`}
                          variant="DEFAULT"
                          showDot={false}
                        />
                        <Badge label={entry.regionCode} variant={regionVariant} />
                      </div>
                      <time
                        dateTime={entry.occurredAt}
                        className="text-xs font-mono text-ink-muted tabular-nums"
                      >
                        {formatDateTime(entry.occurredAt)}
                      </time>
                    </div>

                    <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs text-ink-secondary">
                      <span>
                        Collection Source:{" "}
                        <strong className="font-mono text-ink">{entry.source}</strong>
                      </span>
                      {entry.previousGranted !== null ? (
                        <span>
                          Transition:{" "}
                          <span className="font-mono">
                            {entry.previousGranted ? "true" : "false"} →{" "}
                            {entry.newGranted ? "true" : "false"}
                          </span>
                        </span>
                      ) : (
                        <span>Initial Consent Record</span>
                      )}
                      {entry.experimentVariant ? (
                        <Badge
                          label={`Variant: ${entry.experimentVariant}`}
                          variant="GDPR"
                          showDot={false}
                        />
                      ) : null}
                      {entry.userAgentSummary ? (
                        <span className="text-ink-muted">Client: {entry.userAgentSummary}</span>
                      ) : null}
                    </div>

                    {/* SHA-256 Proof Receipt Row */}
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 rounded-control bg-subtle/70 border border-border-subtle px-3 py-2">
                      <div className="flex items-center gap-2 min-w-0">
                        <ShieldCheck
                          className="h-4 w-4 text-status-success-text shrink-0"
                          aria-hidden="true"
                        />
                        <span className="text-xs font-mono text-ink-secondary truncate">
                          SHA-256 Proof: {entry.proofHash}
                        </span>
                      </div>
                      <button
                        type="button"
                        onClick={() => void handleCopyHash(entry)}
                        aria-label={`Copy proof hash for ${entry.purposeName}`}
                        className="inline-flex items-center justify-center gap-1.5 min-h-tap px-3 rounded-control bg-surface border border-border hover:border-border-strong text-xs font-semibold text-ink shrink-0 transition-colors"
                      >
                        {isCopied ? (
                          <>
                            <Check className="h-3.5 w-3.5 text-status-success-text" />
                            <span>Copied</span>
                          </>
                        ) : (
                          <>
                            <Copy className="h-3.5 w-3.5" />
                            <span>Copy Hash</span>
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                </Card>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
};
