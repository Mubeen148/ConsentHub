import React, { useState } from "react";
import { useMutation, useQuery } from "@apollo/client";
import {
  ChevronDown,
  ChevronUp,
  Edit3,
  FilePlus2,
  Plus,
  Sparkles,
} from "lucide-react";
import type {
  ConsentPurpose,
  LegalBasis,
  PolicyVersion,
} from "../../types/contracts";
import {
  GET_MY_CONSENT_STATE,
  GET_PURPOSES_AND_POLICIES,
  PUBLISH_POLICY_VERSION,
  UPSERT_PURPOSE,
} from "../../graphql/operations";
import { formatDateTime, formatLegalBasis } from "../../lib/format";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { ConsentToggle } from "../../components/ui/ConsentToggle";
import { Modal } from "../../components/ui/Modal";
import {
  EmptyState,
  ErrorState,
  SafeMarkdown,
  TableSkeleton,
} from "../../components/ui/FeedbackStates";
import { useToast } from "../../components/ui/ToastProvider";

export const AdminPoliciesPage: React.FC = () => {
  const { showToast } = useToast();

  const { data, loading, error, refetch } = useQuery<{
    purposes: ConsentPurpose[];
    policyVersions: PolicyVersion[];
  }>(GET_PURPOSES_AND_POLICIES, {
    variables: { includeInactive: true },
  });

  // Purpose Modal State
  const [purposeModalOpen, setPurposeModalOpen] = useState(false);
  const [editingPurposeId, setEditingPurposeId] = useState<string | undefined>(undefined);
  const [slug, setSlug] = useState("");
  const [purposeName, setPurposeName] = useState("");
  const [description, setDescription] = useState("");
  const [legalBasis, setLegalBasis] = useState<LegalBasis>("CONSENT");
  const [isEssential, setIsEssential] = useState(false);
  const [active, setActive] = useState(true);
  const [displayOrder, setDisplayOrder] = useState(6);

  // Policy Version Modal State
  const [policyModalOpen, setPolicyModalOpen] = useState(false);
  const [versionLabel, setVersionLabel] = useState("");
  const [policyTitle, setPolicyTitle] = useState("");
  const [policySummary, setPolicySummary] = useState("");
  const [contentMarkdown, setContentMarkdown] = useState("");
  const [requiresReconsent, setRequiresReconsent] = useState(true);
  const [activateImmediately, setActivateImmediately] = useState(true);

  const [expandedPolicyId, setExpandedPolicyId] = useState<string | null>(null);

  const [upsertPurpose, { loading: savingPurpose }] = useMutation(UPSERT_PURPOSE, {
    refetchQueries: [
      { query: GET_PURPOSES_AND_POLICIES, variables: { includeInactive: true } },
    ],
  });

  const [publishPolicyVersion, { loading: publishingPolicy }] = useMutation(
    PUBLISH_POLICY_VERSION,
    {
      refetchQueries: [
        { query: GET_PURPOSES_AND_POLICIES, variables: { includeInactive: true } },
        { query: GET_MY_CONSENT_STATE },
      ],
    }
  );

  if (loading && !data) {
    return (
      <div className="space-y-6">
        <TableSkeleton rows={5} columns={5} />
        <TableSkeleton rows={3} columns={4} />
      </div>
    );
  }

  if (error) {
    return (
      <ErrorState
        title="Unable to load purposes & policy versions"
        message={error.message}
        onRetry={() => void refetch()}
      />
    );
  }

  const purposes = data?.purposes ?? [];
  const policies = data?.policyVersions ?? [];

  const openNewPurposeModal = () => {
    setEditingPurposeId(undefined);
    setSlug("");
    setPurposeName("");
    setDescription("");
    setLegalBasis("CONSENT");
    setIsEssential(false);
    setActive(true);
    setDisplayOrder(purposes.length + 1);
    setPurposeModalOpen(true);
  };

  const openEditPurposeModal = (p: ConsentPurpose) => {
    setEditingPurposeId(p.id);
    setSlug(p.slug);
    setPurposeName(p.name);
    setDescription(p.description);
    setLegalBasis(p.legalBasis);
    setIsEssential(p.isEssential);
    setActive(p.active);
    setDisplayOrder(p.displayOrder);
    setPurposeModalOpen(true);
  };

  const handleSavePurpose = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!/^[a-z][a-z0-9_]{1,63}$/.test(slug.trim())) {
      showToast({
        title: "Invalid purpose slug",
        description: "Slug must be lowercase letters, numbers, or underscores (e.g., ai_training).",
        variant: "warning",
      });
      return;
    }

    try {
      await upsertPurpose({
        variables: {
          input: {
            id: editingPurposeId,
            slug: slug.trim(),
            name: purposeName.trim(),
            description: description.trim(),
            legalBasis: isEssential ? "STRICTLY_NECESSARY" : legalBasis,
            isEssential,
            active: isEssential ? true : active,
            displayOrder: Number(displayOrder) || 1,
          },
        },
      });
      setPurposeModalOpen(false);
      showToast({
        title: editingPurposeId ? "Purpose updated" : "Purpose created",
        description: `Saved purpose "${purposeName.trim()}" (${slug.trim()}).`,
        variant: "success",
      });
    } catch (err) {
      showToast({
        title: "Failed to save purpose",
        description: err instanceof Error ? err.message : "Please verify inputs.",
        variant: "danger",
      });
    }
  };

  const openPublishPolicyModal = () => {
    const highestVersion = policies[0]?.versionNumber ?? 2;
    const nextMajor = `v${highestVersion + 1}.0`;
    setVersionLabel(nextMajor);
    setPolicyTitle(`${new Date().getFullYear()} Updated Privacy & Data Residency Notice`);
    setPolicySummary(
      "Expanded transparency on cross-border telemetry retention, cryptographic consent receipts, and self-service DSAR rights."
    );
    setContentMarkdown(
      `# ${nextMajor} Privacy & Consent Policy\n\n## 1. Purpose Limitation\nWe process personal data strictly for the granular purposes you explicitly authorize.\n\n## 2. Cryptographic Accountability\n- Every consent transition generates an append-only SHA-256 receipt.\n- Withdrawing optional consent is always available in one click.\n\n## 3. Data Subject Rights\nAuthenticated users may export a full JSON archive or request permanent PII erasure at any time.`
    );
    setRequiresReconsent(true);
    setActivateImmediately(true);
    setPolicyModalOpen(true);
  };

  const handlePublishPolicy = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!versionLabel.trim() || !policyTitle.trim() || !policySummary.trim()) {
      showToast({
        title: "Missing policy fields",
        description: "Version label, title, and summary are required.",
        variant: "warning",
      });
      return;
    }
    try {
      await publishPolicyVersion({
        variables: {
          input: {
            versionLabel: versionLabel.trim(),
            title: policyTitle.trim(),
            summary: policySummary.trim(),
            contentMarkdown: contentMarkdown.trim(),
            requiresReconsent,
            activateImmediately,
          },
        },
      });
      setPolicyModalOpen(false);
      showToast({
        title: `Published Policy ${versionLabel.trim()}`,
        description: requiresReconsent
          ? "Users on older policy versions will immediately be prompted to re-consent."
          : "Published without forcing banner re-consent.",
        variant: "success",
      });
    } catch (err) {
      showToast({
        title: "Failed to publish policy version",
        description: err instanceof Error ? err.message : "Check version label uniqueness.",
        variant: "danger",
      });
    }
  };

  return (
    <div className="space-y-8">
      {/* Section 1: Consent Purposes */}
      <section className="space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h2 className="text-xl font-bold font-display text-ink">
              Data Processing Purposes
            </h2>
            <p className="text-sm text-ink-secondary">
              Define granular consent categories, legal bases, and strictly-necessary locks.
            </p>
          </div>
          <Button
            variant="secondary"
            onClick={openNewPurposeModal}
            testId="new-purpose-btn"
            leftIcon={<Plus className="h-4 w-4" />}
          >
            Add Purpose
          </Button>
        </div>

        {purposes.length === 0 ? (
          <EmptyState
            title="No purposes configured"
            description="Create your first data processing purpose."
            actionLabel="Add Purpose"
            onAction={openNewPurposeModal}
          />
        ) : (
          <Card padding="flush" className="overflow-hidden">
            {/* Desktop Table */}
            <div className="hidden md:block overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-subtle border-b border-border text-xs font-semibold uppercase tracking-wider text-ink-secondary">
                    <th className="py-2.5 px-4">Order</th>
                    <th className="py-2.5 px-4">Purpose &amp; Slug</th>
                    <th className="py-2.5 px-4">Legal Basis</th>
                    <th className="py-2.5 px-4">Status</th>
                    <th className="py-2.5 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-subtle text-sm">
                  {purposes.map((p) => (
                    <tr key={p.id} className="hover:bg-canvas transition-colors">
                      <td className="py-3 px-4 font-mono tabular-nums text-ink-muted">
                        #{p.displayOrder}
                      </td>
                      <td className="py-3 px-4">
                        <div className="font-semibold text-ink">{p.name}</div>
                        <div className="text-xs font-mono text-ink-muted">{p.slug}</div>
                        <div className="text-xs text-ink-secondary mt-0.5 max-w-md">
                          {p.description}
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <Badge
                          label={formatLegalBasis(p.legalBasis)}
                          variant={p.isEssential ? "ESSENTIAL" : "DEFAULT"}
                          showDot={false}
                        />
                      </td>
                      <td className="py-3 px-4">
                        <Badge
                          label={p.active ? "Active" : "Inactive"}
                          variant={p.active ? "SUCCESS" : "WARNING"}
                        />
                      </td>
                      <td className="py-3 px-4 text-right">
                        <Button
                          variant="ghost"
                          onClick={() => openEditPurposeModal(p)}
                          aria-label={`Edit purpose ${p.name}`}
                          leftIcon={<Edit3 className="h-4 w-4" />}
                        >
                          Edit
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Mobile Stacked Cards */}
            <div className="md:hidden divide-y divide-border">
              {purposes.map((p) => (
                <div key={p.id} className="p-4 space-y-3">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <p className="text-sm font-semibold text-ink">{p.name}</p>
                      <p className="text-xs font-mono text-ink-muted">{p.slug}</p>
                    </div>
                    <Badge
                      label={p.active ? "Active" : "Inactive"}
                      variant={p.active ? "SUCCESS" : "WARNING"}
                    />
                  </div>
                  <p className="text-xs text-ink-secondary">{p.description}</p>
                  <div className="flex items-center justify-between gap-2">
                    <Badge
                      label={formatLegalBasis(p.legalBasis)}
                      variant={p.isEssential ? "ESSENTIAL" : "DEFAULT"}
                      showDot={false}
                    />
                    <Button
                      variant="secondary"
                      onClick={() => openEditPurposeModal(p)}
                      leftIcon={<Edit3 className="h-4 w-4" />}
                    >
                      Edit
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </Card>
        )}
      </section>

      {/* Section 2: Policy Versions */}
      <section className="space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h2 className="text-xl font-bold font-display text-ink">
              Immutable Privacy Policy Versions
            </h2>
            <p className="text-sm text-ink-secondary">
              Publishing a major policy version with Re-Consent enabled automatically prompts users
              on older versions.
            </p>
          </div>
          <Button
            variant="primary"
            onClick={openPublishPolicyModal}
            testId="publish-policy-modal-btn"
            leftIcon={<FilePlus2 className="h-4 w-4" />}
          >
            Publish New Policy Version
          </Button>
        </div>

        {policies.length === 0 ? (
          <EmptyState
            title="No policy versions published"
            description="Publish v1.0 to activate consent collection."
            actionLabel="Publish Policy"
            onAction={openPublishPolicyModal}
          />
        ) : (
          <div className="space-y-3">
            {policies.map((pv) => {
              const isExpanded = expandedPolicyId === pv.id;
              return (
                <Card key={pv.id} padding="compact">
                  <div className="space-y-3">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-sm font-mono font-bold px-2.5 py-0.5 rounded bg-subtle border border-border text-ink">
                          {pv.versionLabel}
                        </span>
                        <span className="text-base font-semibold text-ink">{pv.title}</span>
                        {pv.isActive ? (
                          <Badge label="Active Policy" variant="SUCCESS" />
                        ) : (
                          <Badge label="Superseded" variant="DEFAULT" showDot={false} />
                        )}
                        {pv.requiresReconsent ? (
                          <Badge label="Requires Re-Consent" variant="GDPR" showDot={false} />
                        ) : null}
                      </div>
                      <span className="text-xs font-mono text-ink-muted tabular-nums">
                        Published {formatDateTime(pv.publishedAt)}
                      </span>
                    </div>

                    <p className="text-sm text-ink-secondary">{pv.summary}</p>

                    <div className="flex items-center justify-between pt-1">
                      <span className="text-xs text-ink-muted font-mono">
                        Version #{pv.versionNumber}
                        {pv.publishedByEmail ? ` · Author: ${pv.publishedByEmail}` : ""}
                      </span>
                      <button
                        type="button"
                        aria-expanded={isExpanded}
                        onClick={() =>
                          setExpandedPolicyId((prev) => (prev === pv.id ? null : pv.id))
                        }
                        className="inline-flex items-center gap-1 min-h-tap px-2.5 rounded-control text-xs font-semibold text-brand hover:bg-brand-subtle"
                      >
                        <span>{isExpanded ? "Hide Markdown" : "View Policy Text"}</span>
                        {isExpanded ? (
                          <ChevronUp className="h-4 w-4" />
                        ) : (
                          <ChevronDown className="h-4 w-4" />
                        )}
                      </button>
                    </div>

                    {isExpanded ? (
                      <div className="p-4 rounded-control bg-subtle/60 border border-border-subtle">
                        <SafeMarkdown content={pv.contentMarkdown} />
                      </div>
                    ) : null}
                  </div>
                </Card>
              );
            })}
          </div>
        )}
      </section>

      {/* Upsert Purpose Modal */}
      <Modal
        open={purposeModalOpen}
        onClose={() => setPurposeModalOpen(false)}
        title={editingPurposeId ? "Edit Consent Purpose" : "Create Consent Purpose"}
        subtitle="Changes immediately propagate to SDUI screens and SDK configurations."
        preventClose={savingPurpose}
        footer={
          <>
            <Button variant="secondary" onClick={() => setPurposeModalOpen(false)}>
              Cancel
            </Button>
            <Button
              type="submit"
              form="upsert-purpose-form"
              variant="primary"
              loading={savingPurpose}
              testId="save-purpose-submit-btn"
            >
              {editingPurposeId ? "Update Purpose" : "Create Purpose"}
            </Button>
          </>
        }
      >
        <form id="upsert-purpose-form" onSubmit={handleSavePurpose} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div className="sm:col-span-2">
              <label
                htmlFor="purpose-slug-input"
                className="block text-sm font-medium text-ink-secondary mb-1"
              >
                Slug identifier
              </label>
              <input
                id="purpose-slug-input"
                type="text"
                required
                disabled={Boolean(editingPurposeId)}
                value={slug}
                onChange={(e) => setSlug(e.target.value.toLowerCase())}
                placeholder="ai_telemetry"
                className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface font-mono text-sm text-ink disabled:bg-subtle"
              />
            </div>
            <div>
              <label
                htmlFor="purpose-order-input"
                className="block text-sm font-medium text-ink-secondary mb-1"
              >
                Display order
              </label>
              <input
                id="purpose-order-input"
                type="number"
                min={1}
                max={99}
                required
                value={displayOrder}
                onChange={(e) => setDisplayOrder(Number(e.target.value))}
                className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-sm text-ink"
              />
            </div>
          </div>

          <div>
            <label
              htmlFor="purpose-name-input"
              className="block text-sm font-medium text-ink-secondary mb-1"
            >
              Display title
            </label>
            <input
              id="purpose-name-input"
              type="text"
              required
              value={purposeName}
              onChange={(e) => setPurposeName(e.target.value)}
              placeholder="AI Model Diagnostics"
              className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-sm text-ink"
            />
          </div>

          <div>
            <label
              htmlFor="purpose-desc-input"
              className="block text-sm font-medium text-ink-secondary mb-1"
            >
              Plain-language description
            </label>
            <textarea
              id="purpose-desc-input"
              rows={3}
              required
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Explain clearly what data is collected and why."
              className="w-full p-3 rounded-control border border-border-control bg-surface text-sm text-ink"
            />
          </div>

          <div>
            <label
              htmlFor="purpose-basis-select"
              className="block text-sm font-medium text-ink-secondary mb-1"
            >
              Regulatory legal basis
            </label>
            <select
              id="purpose-basis-select"
              value={isEssential ? "STRICTLY_NECESSARY" : legalBasis}
              disabled={isEssential}
              onChange={(e) => setLegalBasis(e.target.value as LegalBasis)}
              className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-sm text-ink disabled:bg-subtle"
            >
              <option value="CONSENT">CONSENT (Prior Opt-In)</option>
              <option value="LEGITIMATE_INTEREST_OPT_OUT">
                LEGITIMATE_INTEREST_OPT_OUT (CCPA / Opt-Out)
              </option>
              <option value="STRICTLY_NECESSARY">
                STRICTLY_NECESSARY (Essential Only)
              </option>
            </select>
          </div>

          <div className="flex items-center justify-between rounded-control border border-border-subtle p-3">
            <div>
              <p className="text-sm font-semibold text-ink">Strictly Necessary (Essential)</p>
              <p className="text-xs text-ink-secondary">
                Locks toggle as Always Active across all regions.
              </p>
            </div>
            <ConsentToggle
              label="Strictly Necessary"
              checked={isEssential}
              onChange={(next) => {
                setIsEssential(next);
                if (next) {
                  setLegalBasis("STRICTLY_NECESSARY");
                  setActive(true);
                } else if (legalBasis === "STRICTLY_NECESSARY") {
                  setLegalBasis("CONSENT");
                }
              }}
            />
          </div>

          <div className="flex items-center justify-between rounded-control border border-border-subtle p-3">
            <div>
              <p className="text-sm font-semibold text-ink">Purpose Active</p>
              <p className="text-xs text-ink-secondary">
                Include in active banners, preference centers, and SDK payloads.
              </p>
            </div>
            <ConsentToggle
              label="Purpose Active"
              checked={isEssential ? true : active}
              disabled={isEssential}
              onChange={setActive}
            />
          </div>
        </form>
      </Modal>

      {/* Publish Policy Version Modal */}
      <Modal
        open={policyModalOpen}
        onClose={() => setPolicyModalOpen(false)}
        size="lg"
        title="Publish Immutable Privacy Policy Version"
        subtitle="Creates a permanent versioned snapshot in policy_versions and logs POLICY_PUBLISHED."
        preventClose={publishingPolicy}
        footer={
          <>
            <Button variant="secondary" onClick={() => setPolicyModalOpen(false)}>
              Cancel
            </Button>
            <Button
              type="submit"
              form="publish-policy-form"
              variant="primary"
              loading={publishingPolicy}
              testId="confirm-publish-policy-btn"
              leftIcon={<Sparkles className="h-4 w-4" />}
            >
              Publish Policy {versionLabel}
            </Button>
          </>
        }
      >
        <form id="publish-policy-form" onSubmit={handlePublishPolicy} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label
                htmlFor="policy-version-label"
                className="block text-sm font-medium text-ink-secondary mb-1"
              >
                Semantic Version Label
              </label>
              <input
                id="policy-version-label"
                type="text"
                required
                value={versionLabel}
                onChange={(e) => setVersionLabel(e.target.value)}
                placeholder="v3.0"
                data-testid="policy-version-label-input"
                className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface font-mono text-sm text-ink"
              />
            </div>
            <div className="sm:col-span-2">
              <label
                htmlFor="policy-title-input"
                className="block text-sm font-medium text-ink-secondary mb-1"
              >
                Policy Title
              </label>
              <input
                id="policy-title-input"
                type="text"
                required
                value={policyTitle}
                onChange={(e) => setPolicyTitle(e.target.value)}
                className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-sm text-ink"
              />
            </div>
          </div>

          <div>
            <label
              htmlFor="policy-summary-input"
              className="block text-sm font-medium text-ink-secondary mb-1"
            >
              Plain-Language Banner Summary
            </label>
            <textarea
              id="policy-summary-input"
              rows={2}
              required
              value={policySummary}
              onChange={(e) => setPolicySummary(e.target.value)}
              className="w-full p-3 rounded-control border border-border-control bg-surface text-sm text-ink"
            />
          </div>

          <div>
            <label
              htmlFor="policy-markdown-input"
              className="block text-sm font-medium text-ink-secondary mb-1"
            >
              Full Policy Notice (Markdown)
            </label>
            <textarea
              id="policy-markdown-input"
              rows={6}
              required
              value={contentMarkdown}
              onChange={(e) => setContentMarkdown(e.target.value)}
              className="w-full p-3 rounded-control border border-border-control bg-surface font-mono text-xs text-ink"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="flex items-center justify-between rounded-control border border-border-subtle p-3">
              <div>
                <p className="text-sm font-semibold text-ink">
                  Require Re-Consent from All Users
                </p>
                <p className="text-xs text-ink-secondary">
                  Triggers needsReconsent=true for subjects on older versions.
                </p>
              </div>
              <ConsentToggle
                label="Require Re-Consent from All Users"
                checked={requiresReconsent}
                onChange={setRequiresReconsent}
                testId="policy-requires-reconsent-toggle"
              />
            </div>

            <div className="flex items-center justify-between rounded-control border border-border-subtle p-3">
              <div>
                <p className="text-sm font-semibold text-ink">Activate Immediately</p>
                <p className="text-xs text-ink-secondary">
                  Replaces current active policy version immediately.
                </p>
              </div>
              <ConsentToggle
                label="Activate Immediately"
                checked={activateImmediately}
                onChange={setActivateImmediately}
              />
            </div>
          </div>
        </form>
      </Modal>
    </div>
  );
};
