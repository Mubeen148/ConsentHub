import React, { useState } from "react";
import { useMutation, useQuery } from "@apollo/client";
import { Link } from "react-router-dom";
import {
  AlertTriangle,
  Download,
  FileSpreadsheet,
  Lock,
  Trash2,
} from "lucide-react";
import type { DataRequest, DataRequestStatus } from "../../types/contracts";
import { CREATE_DATA_REQUEST, GET_MY_DATA_REQUESTS } from "../../graphql/operations";
import { apiDownloadPrivacyExport, RestApiError } from "../../lib/restClient";
import { usePrivacySession } from "../../context/PrivacySessionContext";
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

const LIFECYCLE_STEPS: DataRequestStatus[] = [
  "PENDING",
  "IN_REVIEW",
  "APPROVED",
  "COMPLETED",
];

function statusBadgeVariant(status: DataRequestStatus): BadgeVariant {
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

export const DataRequestsPage: React.FC = () => {
  const { showToast } = useToast();
  const { user, authLoading } = usePrivacySession();

  const [exporting, setExporting] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [confirmationText, setConfirmationText] = useState("");
  const [deleteReason, setDeleteReason] = useState("");
  const [deleteFormError, setDeleteFormError] = useState<string | null>(null);

  const { data, loading, error, refetch } = useQuery<{
    myDataRequests: DataRequest[];
  }>(GET_MY_DATA_REQUESTS, {
    skip: !user,
    fetchPolicy: "cache-and-network",
  });

  const [createDataRequest, { loading: creatingDelete }] = useMutation<{
    createDataRequest: DataRequest;
  }>(CREATE_DATA_REQUEST, {
    refetchQueries: [{ query: GET_MY_DATA_REQUESTS }],
  });

  if (authLoading) {
    return <TableSkeleton rows={4} columns={5} />;
  }

  if (!user) {
    return (
      <div className="max-w-2xl mx-auto py-6">
        <Card>
          <div className="flex flex-col items-center text-center space-y-4 py-4">
            <div className="h-12 w-12 rounded-card bg-subtle flex items-center justify-center text-ink-secondary">
              <Lock className="h-6 w-6" aria-hidden="true" />
            </div>
            <div className="space-y-1.5 max-w-md">
              <h1 className="text-xl font-bold font-display text-ink">
                Sign in to exercise account DSAR rights
              </h1>
              <p className="text-sm text-ink-secondary">
                Identity verification is required under GDPR Art. 12(6) &amp; CCPA §1798.130 before
                exporting personal account data or erasing profile records. Note: you can always
                withdraw optional cookie consent anonymously in the{" "}
                <Link to="/portal/preferences" className="text-brand font-semibold underline">
                  Preference Center
                </Link>
                .
              </p>
            </div>
            <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
              <Link to="/login" state={{ from: "/portal/requests" }}>
                <Button variant="primary">Sign In to Continue</Button>
              </Link>
              <Link to="/signup">
                <Button variant="secondary">Create Account</Button>
              </Link>
            </div>
          </div>
        </Card>
      </div>
    );
  }

  const requests = data?.myDataRequests ?? [];
  const hasActiveDeleteTicket = requests.some(
    (r) =>
      r.requestType === "DELETE" &&
      (r.status === "PENDING" || r.status === "IN_REVIEW" || r.status === "APPROVED")
  );

  const handleExportDownload = async () => {
    try {
      setExporting(true);
      const result = await apiDownloadPrivacyExport();
      await refetch();
      showToast({
        title: "Portability JSON archive downloaded",
        description: `Saved ${result.filename} (${Math.max(1, Math.round(result.byteLength / 1024))} KB) and logged COMPLETED export receipt.`,
        variant: "success",
      });
    } catch (err) {
      const msg =
        err instanceof RestApiError
          ? err.message
          : "Could not download portability archive.";
      showToast({
        title: "Export failed",
        description: msg,
        variant: "danger",
      });
    } finally {
      setExporting(false);
    }
  };

  const handleSubmitDeleteRequest = async (e: React.FormEvent) => {
    e.preventDefault();
    setDeleteFormError(null);

    if (confirmationText.trim() !== "DELETE MY DATA") {
      setDeleteFormError('Please type exact phrase "DELETE MY DATA" to confirm.');
      return;
    }

    try {
      await createDataRequest({
        variables: {
          input: {
            requestType: "DELETE",
            confirmationText: "DELETE MY DATA",
            reason: deleteReason.trim() || undefined,
          },
        },
      });
      setDeleteModalOpen(false);
      setConfirmationText("");
      setDeleteReason("");
      showToast({
        title: "Data erasure request submitted",
        description:
          "Your ticket is now PENDING in the Privacy Governance DSAR queue.",
        variant: "warning",
      });
    } catch (err) {
      setDeleteFormError(
        err instanceof Error ? err.message : "Failed to submit erasure request."
      );
    }
  };

  return (
    <div className="space-y-8 max-w-4xl mx-auto">
      <div className="space-y-1.5">
        <div className="flex flex-wrap items-center gap-2">
          <Badge label="GDPR Art. 15, 17 & 20" variant="GDPR" />
          <Badge label="CCPA Right to Know & Delete" variant="CCPA" />
        </div>
        <h1 className="text-2xl font-bold font-display text-ink">
          Data Subject Access Requests (DSAR)
        </h1>
        <p className="text-sm text-ink-secondary max-w-2xl">
          Download a complete machine-readable JSON copy of your ConsentHub data instantaneously,
          or initiate a tracked account erasure request.
        </p>
      </div>

      {/* Action Cards: Portability Export + Danger Zone Delete */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <Card
          header="Download My Data (Portability)"
          headerAction={<Badge label="Instant JSON" variant="SUCCESS" showDot={false} />}
        >
          <div className="space-y-4">
            <p className="text-sm text-ink-secondary">
              Exports your profile, current consent decisions, SHA-256 consent ledger receipts, A/B
              experiment assignments, and historical DSAR logs in structured UTF-8 JSON format.
            </p>
            <Button
              variant="primary"
              fullWidth
              loading={exporting}
              onClick={() => void handleExportDownload()}
              testId="download-my-data-btn"
              leftIcon={<Download className="h-4 w-4" />}
            >
              Download My Data (.json)
            </Button>
          </div>
        </Card>

        <div className="bg-status-danger-bg/55 border border-status-danger-border rounded-card p-6 flex flex-col justify-between space-y-4">
          <div className="space-y-2">
            <div className="flex items-center justify-between gap-2">
              <h2 className="text-base font-semibold text-status-danger-text font-display">
                Danger Zone · Delete My Data
              </h2>
              <Badge label="Right to Erasure" variant="DANGER" showDot={false} />
            </div>
            <p className="text-sm text-ink">
              Submits a formal erasure request. Once completed by Governance review, your name and
              email are permanently redacted, optional consents are revoked, and active sessions are
              invalidated.
            </p>
          </div>

          <Button
            variant="destructive"
            fullWidth
            disabled={hasActiveDeleteTicket}
            onClick={() => {
              setDeleteFormError(null);
              setConfirmationText("");
              setDeleteModalOpen(true);
            }}
            testId="open-delete-data-modal-btn"
            leftIcon={<Trash2 className="h-4 w-4" />}
          >
            {hasActiveDeleteTicket
              ? "Erasure Request Currently In Progress"
              : "Request Account & Data Erasure"}
          </Button>
        </div>
      </div>

      {/* Live DSAR Lifecycle Tracker */}
      <div className="space-y-4">
        <h2 className="text-lg font-semibold font-display text-ink">
          Your DSAR Request History &amp; Live Status Tracker
        </h2>

        {loading && !data ? (
          <TableSkeleton rows={3} columns={5} />
        ) : error ? (
          <ErrorState
            title="Unable to load your DSAR tickets"
            message={error.message}
            onRetry={() => void refetch()}
          />
        ) : requests.length === 0 ? (
          <EmptyState
            icon={<FileSpreadsheet className="h-6 w-6" />}
            title="No data requests submitted yet"
            description="Download your JSON archive above to generate your first portability record."
            actionLabel="Download My Data"
            onAction={() => void handleExportDownload()}
          />
        ) : (
          <div className="space-y-4">
            {requests.map((req) => {
              const currentStepIndex = LIFECYCLE_STEPS.indexOf(req.status);

              return (
                <Card key={req.id} padding="default">
                  <div
                    data-testid={`dsar-ticket-${req.requestType.toLowerCase()}`}
                    className="space-y-4"
                  >
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge
                          label={
                            req.requestType === "EXPORT"
                              ? "PORTABILITY EXPORT"
                              : "DATA ERASURE (DELETE)"
                          }
                          variant={req.requestType === "DELETE" ? "DANGER" : "GDPR"}
                          showDot={false}
                        />
                        <Badge
                          label={req.status}
                          variant={statusBadgeVariant(req.status)}
                        />
                        <span className="text-xs font-mono text-ink-muted">
                          #{req.id.slice(0, 8)}
                        </span>
                      </div>
                      <span className="text-xs font-mono text-ink-muted tabular-nums">
                        Requested {formatDateTime(req.requestedAt)}
                      </span>
                    </div>

                    {/* Visual 4-Stage Lifecycle Progress Bar */}
                    {req.status !== "REJECTED" ? (
                      <div
                        aria-label={`Request status: ${req.status}`}
                        className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1"
                      >
                        {LIFECYCLE_STEPS.map((step, idx) => {
                          const reached = currentStepIndex >= idx;
                          return (
                            <div
                              key={step}
                              className={[
                                "rounded-control border px-3 py-2 text-xs font-semibold flex items-center gap-2",
                                reached
                                  ? "bg-status-success-bg border-status-success-border text-status-success-text"
                                  : "bg-subtle/50 border-border-subtle text-ink-muted",
                              ].join(" ")}
                            >
                              <span
                                className={[
                                  "h-2 w-2 rounded-pill shrink-0",
                                  reached ? "bg-status-success-solid" : "bg-border-control",
                                ].join(" ")}
                                aria-hidden="true"
                              />
                              <span>{step.replace("_", " ")}</span>
                            </div>
                          );
                        })}
                      </div>
                    ) : null}

                    {req.reason || req.adminNotes || req.completedAt ? (
                      <div className="rounded-control bg-subtle/70 border border-border-subtle px-3.5 py-2.5 text-xs space-y-1 text-ink-secondary">
                        {req.reason ? (
                          <p>
                            <strong className="text-ink">Submitted Reason:</strong> {req.reason}
                          </p>
                        ) : null}
                        {req.adminNotes ? (
                          <p>
                            <strong className="text-ink">Governance Notes:</strong>{" "}
                            {req.adminNotes}
                          </p>
                        ) : null}
                        {req.completedAt ? (
                          <p className="font-mono text-ink-muted">
                            Completed at {formatDateTime(req.completedAt)}
                            {req.processedByEmail ? ` by ${req.processedByEmail}` : ""}
                          </p>
                        ) : null}
                      </div>
                    ) : null}
                  </div>
                </Card>
              );
            })}
          </div>
        )}
      </div>

      {/* Delete My Data Confirmation Modal */}
      <Modal
        open={deleteModalOpen}
        onClose={() => setDeleteModalOpen(false)}
        size="md"
        title="Confirm Personal Data Erasure Request"
        subtitle="GDPR Art. 17 Right to Erasure / CCPA Right to Delete"
        preventClose={creatingDelete}
        footer={
          <>
            <Button
              variant="secondary"
              disabled={creatingDelete}
              onClick={() => setDeleteModalOpen(false)}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              form="delete-dsar-form"
              variant="destructive"
              loading={creatingDelete}
              disabled={confirmationText.trim() !== "DELETE MY DATA"}
              testId="confirm-delete-data-btn"
            >
              Submit Erasure Request
            </Button>
          </>
        }
      >
        <form id="delete-dsar-form" onSubmit={handleSubmitDeleteRequest} className="space-y-4">
          <div className="rounded-control bg-status-warning-bg border border-status-warning-border p-3.5 flex items-start gap-2.5 text-xs text-ink">
            <AlertTriangle
              className="h-4 w-4 text-status-warning-text shrink-0 mt-0.5"
              aria-hidden="true"
            />
            <div>
              <p className="font-semibold text-status-warning-text">
                Permanent Account Pseudonymization Impact
              </p>
              <p className="mt-1">
                Upon fulfillment, your email, full name, and credentials are irreversibly scrubbed,
                all optional consents are revoked, and active login cookies are invalidated while
                retaining anonymized statutory compliance proof.
              </p>
            </div>
          </div>

          {deleteFormError ? (
            <p role="alert" className="text-xs font-semibold text-status-danger-text">
              {deleteFormError}
            </p>
          ) : null}

          <div>
            <label
              htmlFor="delete-reason-input"
              className="block text-sm font-medium text-ink-secondary mb-1.5"
            >
              Reason for erasure (optional)
            </label>
            <textarea
              id="delete-reason-input"
              rows={3}
              value={deleteReason}
              onChange={(e) => setDeleteReason(e.target.value)}
              placeholder="e.g., Closing account and exercising GDPR Art. 17 erasure right."
              className="w-full p-3 rounded-control border border-border-control bg-surface text-sm text-ink placeholder:text-ink-muted"
            />
          </div>

          <div>
            <label
              htmlFor="delete-confirm-text"
              className="block text-sm font-medium text-ink-secondary mb-1.5"
            >
              Type <span className="font-mono font-bold text-ink">DELETE MY DATA</span> to confirm
            </label>
            <input
              id="delete-confirm-text"
              type="text"
              required
              autoComplete="off"
              value={confirmationText}
              onChange={(e) => setConfirmationText(e.target.value)}
              placeholder="DELETE MY DATA"
              data-testid="delete-confirmation-input"
              className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface font-mono text-sm text-ink"
            />
          </div>
        </form>
      </Modal>
    </div>
  );
};
