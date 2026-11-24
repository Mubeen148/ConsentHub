import React, { useState } from "react";
import { useMutation, useQuery } from "@apollo/client";
import { AlertTriangle, CheckCircle2, ClipboardCheck, Inbox } from "lucide-react";
import type { DataRequest, DataRequestStatus } from "../../types/contracts";
import {
  GET_ADMIN_ANALYTICS,
  GET_ADMIN_DATA_REQUESTS,
  PROCESS_DATA_REQUEST,
} from "../../graphql/operations";
import { formatDateTime } from "../../lib/format";
import { Badge, type BadgeVariant } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Modal } from "../../components/ui/Modal";
import {
  EmptyState,
  ErrorState,
  TableSkeleton,
} from "../../components/ui/FeedbackStates";
import { useToast } from "../../components/ui/ToastProvider";

type StatusFilter = "ALL" | DataRequestStatus;

const STATUS_FILTERS: StatusFilter[] = [
  "ALL",
  "PENDING",
  "IN_REVIEW",
  "APPROVED",
  "COMPLETED",
  "REJECTED",
];

function badgeForStatus(status: DataRequestStatus): BadgeVariant {
  switch (status) {
    case "COMPLETED":
    case "APPROVED":
      return "SUCCESS";
    case "PENDING":
    case "IN_REVIEW":
      return "WARNING";
    case "REJECTED":
      return "DANGER";
    default:
      return "DEFAULT";
  }
}

export const AdminRequestsPage: React.FC = () => {
  const { showToast } = useToast();

  const [filter, setFilter] = useState<StatusFilter>("ALL");
  const [selectedReq, setSelectedReq] = useState<DataRequest | null>(null);
  const [nextStatus, setNextStatus] = useState<DataRequestStatus>("IN_REVIEW");
  const [adminNotes, setAdminNotes] = useState("");

  const { data, loading, error, refetch } = useQuery<{
    adminDataRequests: DataRequest[];
  }>(GET_ADMIN_DATA_REQUESTS, {
    variables: {
      status: filter === "ALL" ? undefined : filter,
    },
  });

  const [processRequest, { loading: processing }] = useMutation<{
    processDataRequest: DataRequest;
  }>(PROCESS_DATA_REQUEST, {
    refetchQueries: [
      {
        query: GET_ADMIN_DATA_REQUESTS,
        variables: { status: filter === "ALL" ? undefined : filter },
      },
      { query: GET_ADMIN_DATA_REQUESTS, variables: { status: "PENDING" } },
      { query: GET_ADMIN_ANALYTICS, variables: { days: 30 } },
    ],
  });

  if (loading && !data) {
    return <TableSkeleton rows={5} columns={6} />;
  }

  if (error) {
    return (
      <ErrorState
        title="Unable to load DSAR Governance Queue"
        message={error.message}
        onRetry={() => void refetch()}
      />
    );
  }

  const requests = data?.adminDataRequests ?? [];

  const openReviewModal = (req: DataRequest) => {
    setSelectedReq(req);
    setAdminNotes(req.adminNotes ?? "");
    if (req.status === "PENDING") {
      setNextStatus("IN_REVIEW");
    } else if (req.status === "IN_REVIEW") {
      setNextStatus("APPROVED");
    } else if (req.status === "APPROVED") {
      setNextStatus("COMPLETED");
    } else {
      setNextStatus(req.status);
    }
  };

  const handleProcessSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedReq) {
      return;
    }

    try {
      await processRequest({
        variables: {
          input: {
            id: selectedReq.id,
            status: nextStatus,
            adminNotes: adminNotes.trim() || undefined,
          },
        },
      });
      setSelectedReq(null);
      showToast({
        title: `DSAR ticket transitioned to ${nextStatus}`,
        description:
          selectedReq.requestType === "DELETE" && nextStatus === "COMPLETED"
            ? "User PII pseudonymized, optional consents revoked, and sessions invalidated."
            : `Audited governance update saved for #${selectedReq.id.slice(0, 8)}.`,
        variant: "success",
      });
    } catch (err) {
      showToast({
        title: "Failed to process DSAR ticket",
        description: err instanceof Error ? err.message : "Please try again.",
        variant: "danger",
      });
    }
  };

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h2 className="text-xl font-bold font-display text-ink">
          DSAR Fulfillment Queue
        </h2>
        <p className="text-sm text-ink-secondary">
          Review, approve, and complete GDPR Art. 15/17/20 &amp; CCPA portability and account
          erasure requests with audited state transitions.
        </p>
      </div>

      {/* Filter Bar */}
      <Card padding="compact">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div
            role="group"
            aria-label="Filter DSAR tickets by status"
            className="flex flex-wrap items-center gap-1.5"
          >
            {STATUS_FILTERS.map((st) => (
              <button
                key={st}
                type="button"
                onClick={() => setFilter(st)}
                data-testid={`dsar-filter-${st}`}
                className={[
                  "min-h-tap px-3.5 rounded-pill text-xs font-semibold transition-colors",
                  filter === st
                    ? "bg-action text-ink-inverse"
                    : "bg-subtle/70 text-ink-secondary hover:text-ink hover:bg-subtle",
                ].join(" ")}
              >
                {st.replace("_", " ")}
              </button>
            ))}
          </div>
          <span className="text-xs font-mono text-ink-muted tabular-nums">
            Showing {requests.length} record{requests.length === 1 ? "" : "s"}
          </span>
        </div>
      </Card>

      {requests.length === 0 ? (
        <EmptyState
          icon={<Inbox className="h-6 w-6" />}
          title={
            filter === "ALL"
              ? "No DSAR tickets in queue"
              : `No ${filter.replace("_", " ")} DSAR tickets`
          }
          description="Self-service portability exports and erasure requests submitted by users appear here immediately."
          actionLabel={filter !== "ALL" ? "Show All Tickets" : undefined}
          onAction={filter !== "ALL" ? () => setFilter("ALL") : undefined}
        />
      ) : (
        <Card padding="flush" className="overflow-hidden">
          {/* Desktop Table */}
          <div className="hidden md:block overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-subtle border-b border-border text-xs font-semibold uppercase tracking-wider text-ink-secondary">
                  <th className="py-2.5 px-4">Subject</th>
                  <th className="py-2.5 px-4">Type</th>
                  <th className="py-2.5 px-4">Status</th>
                  <th className="py-2.5 px-4">Requested At</th>
                  <th className="py-2.5 px-4">Reason / Notes</th>
                  <th className="py-2.5 px-4 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-subtle text-sm">
                {requests.map((req) => {
                  const isTerminal =
                    req.status === "COMPLETED" || req.status === "REJECTED";
                  return (
                    <tr
                      key={req.id}
                      data-testid={`admin-dsar-row-${req.id}`}
                      className="hover:bg-canvas transition-colors"
                    >
                      <td className="py-3 px-4">
                        <div className="font-semibold text-ink">{req.userFullName}</div>
                        <div className="text-xs font-mono text-ink-muted">
                          {req.userEmail}
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <Badge
                          label={req.requestType}
                          variant={req.requestType === "DELETE" ? "DANGER" : "GDPR"}
                          showDot={false}
                        />
                      </td>
                      <td className="py-3 px-4">
                        <Badge
                          label={req.status}
                          variant={badgeForStatus(req.status)}
                        />
                      </td>
                      <td className="py-3 px-4 font-mono text-xs text-ink-muted tabular-nums">
                        {formatDateTime(req.requestedAt)}
                      </td>
                      <td className="py-3 px-4 text-xs text-ink-secondary max-w-xs">
                        <div className="truncate">{req.reason || "—"}</div>
                        {req.adminNotes ? (
                          <div className="text-ink-muted truncate mt-0.5">
                            Note: {req.adminNotes}
                          </div>
                        ) : null}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <Button
                          variant={isTerminal ? "ghost" : "secondary"}
                          onClick={() => openReviewModal(req)}
                          testId={`review-dsar-btn-${req.id}`}
                          leftIcon={
                            isTerminal ? (
                              <CheckCircle2 className="h-4 w-4" />
                            ) : (
                              <ClipboardCheck className="h-4 w-4" />
                            )
                          }
                        >
                          {isTerminal ? "Inspect" : "Review"}
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Mobile Stacked Cards (< 768px) */}
          <div className="md:hidden divide-y divide-border">
            {requests.map((req) => {
              const isTerminal =
                req.status === "COMPLETED" || req.status === "REJECTED";
              return (
                <div key={req.id} className="p-4 space-y-3">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-ink truncate">
                        {req.userFullName}
                      </p>
                      <p className="text-xs font-mono text-ink-muted truncate">
                        {req.userEmail}
                      </p>
                    </div>
                    <Badge label={req.status} variant={badgeForStatus(req.status)} />
                  </div>
                  <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-ink-secondary">
                    <Badge
                      label={req.requestType}
                      variant={req.requestType === "DELETE" ? "DANGER" : "GDPR"}
                      showDot={false}
                    />
                    <span className="font-mono text-ink-muted">
                      {formatDateTime(req.requestedAt)}
                    </span>
                  </div>
                  {req.reason ? (
                    <p className="text-xs text-ink-secondary">{req.reason}</p>
                  ) : null}
                  <Button
                    variant={isTerminal ? "ghost" : "secondary"}
                    fullWidth
                    onClick={() => openReviewModal(req)}
                  >
                    {isTerminal ? "Inspect Ticket" : "Review & Update Status"}
                  </Button>
                </div>
              );
            })}
          </div>
        </Card>
      )}

      {/* Review / Process DSAR Modal */}
      <Modal
        open={Boolean(selectedReq)}
        onClose={() => setSelectedReq(null)}
        title={`DSAR Ticket #${selectedReq?.id.slice(0, 8) ?? ""}`}
        subtitle={
          selectedReq
            ? `${selectedReq.requestType} request for ${selectedReq.userFullName} (${selectedReq.userEmail})`
            : ""
        }
        preventClose={processing}
        footer={
          selectedReq?.status === "COMPLETED" || selectedReq?.status === "REJECTED" ? (
            <Button variant="primary" onClick={() => setSelectedReq(null)}>
              Close
            </Button>
          ) : (
            <>
              <Button
                variant="secondary"
                disabled={processing}
                onClick={() => setSelectedReq(null)}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                form="process-dsar-form"
                variant={
                  selectedReq?.requestType === "DELETE" && nextStatus === "COMPLETED"
                    ? "destructive"
                    : "primary"
                }
                loading={processing}
                testId="confirm-process-dsar-btn"
              >
                {selectedReq?.requestType === "DELETE" && nextStatus === "COMPLETED"
                  ? "Execute Erasure & Complete"
                  : `Save & Mark ${nextStatus}`}
              </Button>
            </>
          )
        }
      >
        {selectedReq ? (
          <form id="process-dsar-form" onSubmit={handleProcessSubmit} className="space-y-4">
            <div className="rounded-control bg-subtle/70 border border-border-subtle p-3.5 text-xs space-y-1.5">
              <div className="flex justify-between">
                <span className="text-ink-muted">Current Status:</span>
                <span className="font-mono font-semibold text-ink">
                  {selectedReq.status}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-ink-muted">Submitted At:</span>
                <span className="font-mono text-ink">
                  {formatDateTime(selectedReq.requestedAt)}
                </span>
              </div>
              {selectedReq.reason ? (
                <div className="pt-1 border-t border-border-subtle">
                  <span className="text-ink-muted block">Requester Reason:</span>
                  <span className="text-ink">{selectedReq.reason}</span>
                </div>
              ) : null}
            </div>

            {selectedReq.status !== "COMPLETED" && selectedReq.status !== "REJECTED" ? (
              <>
                <div>
                  <label
                    htmlFor="dsar-next-status"
                    className="block text-sm font-medium text-ink-secondary mb-1.5"
                  >
                    Transition Status To
                  </label>
                  <select
                    id="dsar-next-status"
                    value={nextStatus}
                    onChange={(e) => setNextStatus(e.target.value as DataRequestStatus)}
                    data-testid="dsar-next-status-select"
                    className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-sm text-ink"
                  >
                    <option value="IN_REVIEW">IN_REVIEW (Identity &amp; Scope Check)</option>
                    <option value="APPROVED">APPROVED (Ready for Fulfillment)</option>
                    <option value="COMPLETED">COMPLETED (Fulfilled)</option>
                    <option value="REJECTED">REJECTED (Unverified / Exempt)</option>
                  </select>
                </div>

                {selectedReq.requestType === "DELETE" && nextStatus === "COMPLETED" ? (
                  <div
                    role="alert"
                    className="rounded-control bg-status-danger-bg border border-status-danger-border p-3.5 flex items-start gap-2.5 text-xs text-ink"
                  >
                    <AlertTriangle
                      className="h-4 w-4 text-status-danger-text shrink-0 mt-0.5"
                      aria-hidden="true"
                    />
                    <div>
                      <p className="font-semibold text-status-danger-text">
                        Irreversible PII Pseudonymization Protocol (ADR-007)
                      </p>
                      <p className="mt-1">
                        Completing this DELETE request immediately overwrites the user&apos;s
                        email to <span className="font-mono">deleted-*@redacted.consenthub.invalid</span>,
                        redacts their name, clears prior export snapshots, revokes optional
                        consents (<span className="font-mono">SYSTEM_ERASURE</span>), and
                        invalidates any active JWT cookie session.
                      </p>
                    </div>
                  </div>
                ) : null}

                <div>
                  <label
                    htmlFor="dsar-admin-notes"
                    className="block text-sm font-medium text-ink-secondary mb-1.5"
                  >
                    Governance Review Notes
                  </label>
                  <textarea
                    id="dsar-admin-notes"
                    rows={3}
                    value={adminNotes}
                    onChange={(e) => setAdminNotes(e.target.value)}
                    placeholder="Document verification basis or fulfillment summary..."
                    className="w-full p-3 rounded-control border border-border-control bg-surface text-sm text-ink"
                  />
                </div>
              </>
            ) : (
              <div className="text-xs text-ink-secondary space-y-1">
                <p>
                  This ticket is finalized (<strong className="font-mono">{selectedReq.status}</strong>).
                </p>
                {selectedReq.adminNotes ? <p>Notes: {selectedReq.adminNotes}</p> : null}
              </div>
            )}
          </form>
        ) : null}
      </Modal>
    </div>
  );
};
