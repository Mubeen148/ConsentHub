import React, { useMemo, useState } from "react";
import { useLazyQuery, useQuery } from "@apollo/client";
import {
  AlertOctagon,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Link2,
  ScrollText,
  ShieldCheck,
} from "lucide-react";
import type { AuditChainVerification, AuditLogEntry } from "../../types/contracts";
import { GET_ADMIN_AUDIT_LOGS, VERIFY_AUDIT_CHAIN } from "../../graphql/operations";
import { formatDateTime, truncateHash } from "../../lib/format";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import {
  EmptyState,
  ErrorState,
  TableSkeleton,
} from "../../components/ui/FeedbackStates";
import { useToast } from "../../components/ui/ToastProvider";

export const AdminAuditPage: React.FC = () => {
  const { showToast } = useToast();

  const [actionFilter, setActionFilter] = useState<string>("");
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const { data, loading, error, refetch } = useQuery<{
    adminAuditLogs: AuditLogEntry[];
  }>(GET_ADMIN_AUDIT_LOGS, {
    variables: {
      limit: 100,
      actionFilter: actionFilter.trim() || undefined,
    },
  });

  const [
    runVerifyChain,
    { data: verifyData, loading: verifyingChain },
  ] = useLazyQuery<{ verifyAuditChain: AuditChainVerification }>(VERIFY_AUDIT_CHAIN, {
    fetchPolicy: "network-only",
    onCompleted: (res) => {
      const result = res.verifyAuditChain;
      showToast({
        title: result.chainValid
          ? "Audit SHA-256 Chain Verified Intact"
          : "Cryptographic Chain Integrity Alert",
        description: result.message,
        variant: result.chainValid ? "success" : "danger",
      });
    },
    onError: (err) => {
      showToast({
        title: "Verification failed",
        description: err.message,
        variant: "danger",
      });
    },
  });

  const entries = data?.adminAuditLogs ?? [];

  const knownActions = useMemo(() => {
    const set = new Set<string>([
      "POLICY_PUBLISHED",
      "PURPOSE_UPSERTED",
      "REGION_RULE_UPDATED",
      "EXPERIMENT_CREATED",
      "EXPERIMENT_STATUS_UPDATED",
      "DSAR_REQUEST_CREATED",
      "DSAR_DATA_EXPORTED",
      "DSAR_STATUS_UPDATED",
      "API_KEY_CREATED",
      "API_KEY_REVOKED",
      "AUTH_LOGIN_SUCCESS",
      "AUTH_SIGNUP",
      "CONSENT_UPDATED",
    ]);
    for (const item of entries) {
      set.add(item.action);
    }
    return Array.from(set).sort();
  }, [entries]);

  if (loading && !data) {
    return <TableSkeleton rows={6} columns={6} />;
  }

  if (error) {
    return (
      <ErrorState
        title="Unable to load tamper-evident audit ledger"
        message={error.message}
        onRetry={() => void refetch()}
      />
    );
  }

  const verification = verifyData?.verifyAuditChain ?? null;

  return (
    <div className="space-y-6">
      {/* Header & Verify Chain CTA */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold font-display text-ink">
            Tamper-Evident Governance Audit Log
          </h2>
          <p className="text-sm text-ink-secondary">
            Append-only PostgreSQL ledger linking every entry&apos;s{" "}
            <code className="font-mono text-xs">prev_hash → entry_hash</code> via deterministic
            SHA-256 canonicalization.
          </p>
        </div>

        <Button
          variant="primary"
          loading={verifyingChain}
          onClick={() => void runVerifyChain()}
          testId="verify-audit-chain-btn"
          leftIcon={<ShieldCheck className="h-4 w-4" />}
        >
          Verify Cryptographic Chain
        </Button>
      </div>

      {/* Live Chain Verification Result Banner */}
      {verification ? (
        <div
          role={verification.chainValid ? "status" : "alert"}
          data-testid="audit-chain-verification-banner"
          className={[
            "rounded-card border px-5 py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3",
            verification.chainValid
              ? "bg-status-success-bg border-status-success-border"
              : "bg-status-danger-bg border-status-danger-border",
          ].join(" ")}
        >
          <div className="flex items-start gap-3">
            {verification.chainValid ? (
              <CheckCircle2
                className="h-5 w-5 text-status-success-text shrink-0 mt-0.5"
                aria-hidden="true"
              />
            ) : (
              <AlertOctagon
                className="h-5 w-5 text-status-danger-text shrink-0 mt-0.5"
                aria-hidden="true"
              />
            )}
            <div>
              <p
                className={`text-sm font-bold ${
                  verification.chainValid
                    ? "text-status-success-text"
                    : "text-status-danger-text"
                }`}
              >
                {verification.chainValid
                  ? `Cryptographic Integrity Verified (${verification.totalEntriesChecked} entries checked)`
                  : `Hash Chain Broken at Sequence #${verification.brokenAtSequenceNum ?? "?"}`}
              </p>
              <p className="text-xs text-ink mt-0.5">{verification.message}</p>
            </div>
          </div>
          <span className="text-xs font-mono text-ink-secondary shrink-0 tabular-nums">
            Verified {formatDateTime(verification.verifiedAt)}
          </span>
        </div>
      ) : null}

      {/* Filter Toolbar */}
      <Card padding="compact">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2.5">
            <label
              htmlFor="audit-action-filter"
              className="text-xs font-semibold uppercase tracking-wider text-ink-secondary"
            >
              Filter by Action:
            </label>
            <select
              id="audit-action-filter"
              value={actionFilter}
              onChange={(e) => setActionFilter(e.target.value)}
              data-testid="audit-action-filter-select"
              className="min-h-tap px-3 rounded-control border border-border-control bg-surface font-mono text-xs text-ink"
            >
              <option value="">ALL ACTIONS</option>
              {knownActions.map((act) => (
                <option key={act} value={act}>
                  {act}
                </option>
              ))}
            </select>
            {actionFilter ? (
              <Button variant="ghost" onClick={() => setActionFilter("")}>
                Clear Filter
              </Button>
            ) : null}
          </div>

          <span className="text-xs font-mono text-ink-muted tabular-nums">
            {entries.length} immutable record{entries.length === 1 ? "" : "s"}
          </span>
        </div>
      </Card>

      {entries.length === 0 ? (
        <EmptyState
          icon={<ScrollText className="h-6 w-6" />}
          title="No audit log entries match filter"
          description="Clear the action filter to inspect the full SHA-256 governance ledger."
          actionLabel={actionFilter ? "Reset Filter" : undefined}
          onAction={actionFilter ? () => setActionFilter("") : undefined}
        />
      ) : (
        <Card padding="flush" className="overflow-hidden">
          {/* Desktop Table */}
          <div className="hidden md:block overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-subtle border-b border-border text-xs font-semibold uppercase tracking-wider text-ink-secondary">
                  <th className="py-2.5 px-4">Seq</th>
                  <th className="py-2.5 px-4">Action &amp; Entity</th>
                  <th className="py-2.5 px-4">Actor</th>
                  <th className="py-2.5 px-4">Hash Chain Link</th>
                  <th className="py-2.5 px-4">Timestamp</th>
                  <th className="py-2.5 px-4 text-right">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-subtle text-sm">
                {entries.map((entry) => {
                  const isOpen = expandedId === entry.id;
                  return (
                    <React.Fragment key={entry.id}>
                      <tr className="hover:bg-canvas transition-colors">
                        <td className="py-3 px-4 font-mono text-xs font-bold text-ink tabular-nums">
                          #{entry.sequenceNum}
                        </td>
                        <td className="py-3 px-4">
                          <div className="font-mono text-xs font-semibold text-ink">
                            {entry.action}
                          </div>
                          <div className="text-xs text-ink-muted font-mono">
                            {entry.entityType}: {truncateHash(entry.entityId, 18)}
                          </div>
                        </td>
                        <td className="py-3 px-4">
                          <div className="text-xs font-medium text-ink">
                            {entry.actorEmail}
                          </div>
                          <Badge
                            label={entry.actorRole}
                            variant={entry.actorRole.includes("ADMIN") ? "GDPR" : "DEFAULT"}
                            showDot={false}
                          />
                        </td>
                        <td className="py-3 px-4">
                          <div className="inline-flex items-center gap-1.5 rounded bg-subtle border border-border-subtle px-2 py-1 font-mono text-[11px] text-ink-secondary">
                            <span title={`prevHash: ${entry.prevHash}`}>
                              {truncateHash(entry.prevHash, 8)}
                            </span>
                            <Link2 className="h-3 w-3 text-brand shrink-0" aria-hidden="true" />
                            <span
                              className="font-semibold text-ink"
                              title={`entryHash: ${entry.entryHash}`}
                            >
                              {truncateHash(entry.entryHash, 10)}
                            </span>
                          </div>
                        </td>
                        <td className="py-3 px-4 font-mono text-xs text-ink-muted tabular-nums">
                          {formatDateTime(entry.createdAt)}
                        </td>
                        <td className="py-3 px-4 text-right">
                          <button
                            type="button"
                            aria-expanded={isOpen}
                            aria-label={`Inspect details for audit sequence ${entry.sequenceNum}`}
                            onClick={() =>
                              setExpandedId((prev) => (prev === entry.id ? null : entry.id))
                            }
                            className="inline-flex items-center justify-center gap-1 min-h-tap px-2.5 rounded-control text-xs font-semibold text-brand hover:bg-brand-subtle"
                          >
                            <span>JSON</span>
                            {isOpen ? (
                              <ChevronUp className="h-4 w-4" />
                            ) : (
                              <ChevronDown className="h-4 w-4" />
                            )}
                          </button>
                        </td>
                      </tr>
                      {isOpen ? (
                        <tr className="bg-subtle/40">
                          <td colSpan={6} className="px-4 py-3">
                            <div className="space-y-2 text-xs font-mono">
                              <div className="grid grid-cols-1 lg:grid-cols-2 gap-2 text-ink-secondary">
                                <div className="truncate">
                                  <strong className="text-ink">prev_hash:</strong>{" "}
                                  {entry.prevHash}
                                </div>
                                <div className="truncate">
                                  <strong className="text-ink">entry_hash:</strong>{" "}
                                  {entry.entryHash}
                                </div>
                              </div>
                              <pre className="rounded-control bg-inverse text-ink-inverse p-3 overflow-x-auto">
                                {JSON.stringify(entry.details ?? {}, null, 2)}
                              </pre>
                            </div>
                          </td>
                        </tr>
                      ) : null}
                    </React.Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Mobile Stacked Cards */}
          <div className="md:hidden divide-y divide-border">
            {entries.map((entry) => {
              const isOpen = expandedId === entry.id;
              return (
                <div key={entry.id} className="p-4 space-y-2.5">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-mono text-xs font-bold text-ink">
                      #{entry.sequenceNum} · {entry.action}
                    </span>
                    <Badge
                      label={entry.actorRole}
                      variant={entry.actorRole.includes("ADMIN") ? "GDPR" : "DEFAULT"}
                      showDot={false}
                    />
                  </div>
                  <p className="text-xs text-ink-secondary">{entry.actorEmail}</p>
                  <div className="rounded bg-subtle border border-border-subtle px-2.5 py-1.5 font-mono text-[11px] text-ink-secondary flex items-center gap-1.5">
                    <span>{truncateHash(entry.prevHash, 8)}</span>
                    <Link2 className="h-3 w-3 text-brand" />
                    <span className="font-semibold text-ink">
                      {truncateHash(entry.entryHash, 10)}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-[11px] font-mono text-ink-muted">
                      {formatDateTime(entry.createdAt)}
                    </span>
                    <Button
                      variant="ghost"
                      onClick={() =>
                        setExpandedId((prev) => (prev === entry.id ? null : entry.id))
                      }
                    >
                      {isOpen ? "Hide JSON" : "Inspect JSON"}
                    </Button>
                  </div>
                  {isOpen ? (
                    <pre className="rounded-control bg-inverse text-ink-inverse p-3 text-xs font-mono overflow-x-auto">
                      {JSON.stringify(entry.details ?? {}, null, 2)}
                    </pre>
                  ) : null}
                </div>
              );
            })}
          </div>
        </Card>
      )}
    </div>
  );
};
