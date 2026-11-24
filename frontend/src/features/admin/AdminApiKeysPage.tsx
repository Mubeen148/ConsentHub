import React, { useState } from "react";
import { useMutation, useQuery } from "@apollo/client";
import {
  AlertTriangle,
  Check,
  Copy,
  ExternalLink,
  KeyRound,
  Plus,
  ShieldOff,
} from "lucide-react";
import type { ApiKeyInfo, CreatedApiKeyResult } from "../../types/contracts";
import {
  CREATE_API_KEY,
  GET_ADMIN_API_KEYS,
  REVOKE_API_KEY,
} from "../../graphql/operations";
import { formatDateTime } from "../../lib/format";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Modal } from "../../components/ui/Modal";
import {
  EmptyState,
  ErrorState,
  TableSkeleton,
} from "../../components/ui/FeedbackStates";
import { useToast } from "../../components/ui/ToastProvider";

export const AdminApiKeysPage: React.FC = () => {
  const { showToast } = useToast();

  const { data, loading, error, refetch } = useQuery<{
    adminApiKeys: ApiKeyInfo[];
  }>(GET_ADMIN_API_KEYS);

  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [name, setName] = useState("");
  const [allowedOrigins, setAllowedOrigins] = useState("*");
  const [scopes, setScopes] = useState("sdk:read,sdk:write");

  const [createdSecret, setCreatedSecret] = useState<CreatedApiKeyResult | null>(null);
  const [copiedKey, setCopiedKey] = useState(false);
  const [copiedSnippet, setCopiedSnippet] = useState(false);

  const [revokeTarget, setRevokeTarget] = useState<ApiKeyInfo | null>(null);

  const [createApiKey, { loading: creating }] = useMutation<{
    createApiKey: CreatedApiKeyResult;
  }>(CREATE_API_KEY, {
    refetchQueries: [{ query: GET_ADMIN_API_KEYS }],
  });

  const [revokeApiKey, { loading: revoking }] = useMutation<{
    revokeApiKey: boolean;
  }>(REVOKE_API_KEY, {
    refetchQueries: [{ query: GET_ADMIN_API_KEYS }],
  });

  if (loading && !data) {
    return <TableSkeleton rows={4} columns={6} />;
  }

  if (error) {
    return (
      <ErrorState
        title="Unable to load SDK API keys"
        message={error.message}
        onRetry={() => void refetch()}
      />
    );
  }

  const keys = data?.adminApiKeys ?? [];

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      return;
    }

    try {
      const res = await createApiKey({
        variables: {
          input: {
            name: name.trim(),
            allowedOrigins: allowedOrigins.trim() || "*",
            scopes: scopes.trim() || "sdk:read,sdk:write",
          },
        },
      });
      if (res.data?.createApiKey) {
        setCreateModalOpen(false);
        setName("");
        setCreatedSecret(res.data.createApiKey);
        showToast({
          title: "SDK API key generated",
          description: "Copy the plain-text key now; only its SHA-256 hash is stored.",
          variant: "success",
        });
      }
    } catch (err) {
      showToast({
        title: "Failed to create API key",
        description: err instanceof Error ? err.message : "Please try again.",
        variant: "danger",
      });
    }
  };

  const handleConfirmRevoke = async () => {
    if (!revokeTarget) {
      return;
    }
    try {
      await revokeApiKey({ variables: { id: revokeTarget.id } });
      setRevokeTarget(null);
      showToast({
        title: "API key revoked",
        description: "Subsequent /api/v1/sdk/* calls with this key will return 401.",
        variant: "warning",
      });
    } catch (err) {
      showToast({
        title: "Failed to revoke API key",
        description: err instanceof Error ? err.message : "Please try again.",
        variant: "danger",
      });
    }
  };

  const embedSnippet = createdSecret
    ? `<script src="${window.location.origin}/consent.js"\n  data-api-key="${createdSecret.plainTextKey}"\n  data-api-base="${window.location.origin}"\n  data-region="EU"></script>`
    : "";

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold font-display text-ink">
            Third-Party SDK &amp; REST API Keys
          </h2>
          <p className="text-sm text-ink-secondary">
            Manage SHA-256 hashed publishable keys for the zero-dependency{" "}
            <code className="font-mono text-xs bg-subtle px-1.5 py-0.5 rounded">
              /consent.js
            </code>{" "}
            browser SDK.
          </p>
        </div>

        <div className="flex items-center gap-2.5 shrink-0">
          <a
            href="/sdk-demo.html"
            className="inline-flex items-center gap-1.5 min-h-tap px-3.5 rounded-control border border-border-control bg-surface hover:bg-canvas text-sm font-semibold text-ink transition-colors"
          >
            <span>Open Live SDK Demo</span>
            <ExternalLink className="h-4 w-4" aria-hidden="true" />
          </a>
          <Button
            variant="primary"
            onClick={() => setCreateModalOpen(true)}
            testId="create-api-key-btn"
            leftIcon={<Plus className="h-4 w-4" />}
          >
            Generate API Key
          </Button>
        </div>
      </div>

      {keys.length === 0 ? (
        <EmptyState
          icon={<KeyRound className="h-6 w-6" />}
          title="No SDK API keys registered"
          description="Generate an API key to embed ConsentHub banners on external properties."
          actionLabel="Generate First Key"
          onAction={() => setCreateModalOpen(true)}
        />
      ) : (
        <Card padding="flush" className="overflow-hidden">
          {/* Desktop Table */}
          <div className="hidden md:block overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-subtle border-b border-border text-xs font-semibold uppercase tracking-wider text-ink-secondary">
                  <th className="py-2.5 px-4">Key Name</th>
                  <th className="py-2.5 px-4">Key Prefix</th>
                  <th className="py-2.5 px-4">Allowed Origins</th>
                  <th className="py-2.5 px-4">Scopes</th>
                  <th className="py-2.5 px-4">Status</th>
                  <th className="py-2.5 px-4">Last Used</th>
                  <th className="py-2.5 px-4 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-subtle text-sm">
                {keys.map((k) => (
                  <tr key={k.id} className="hover:bg-canvas transition-colors">
                    <td className="py-3 px-4 font-semibold text-ink">{k.name}</td>
                    <td className="py-3 px-4 font-mono text-xs text-ink-secondary">
                      {k.keyPrefix}…
                    </td>
                    <td className="py-3 px-4 font-mono text-xs text-ink-secondary">
                      {k.allowedOrigins}
                    </td>
                    <td className="py-3 px-4 font-mono text-xs text-ink-muted">
                      {k.scopes}
                    </td>
                    <td className="py-3 px-4">
                      <Badge
                        label={k.active ? "Active" : "Revoked"}
                        variant={k.active ? "SUCCESS" : "DANGER"}
                      />
                    </td>
                    <td className="py-3 px-4 font-mono text-xs text-ink-muted tabular-nums">
                      {k.lastUsedAt ? formatDateTime(k.lastUsedAt) : "Never"}
                    </td>
                    <td className="py-3 px-4 text-right">
                      {k.active ? (
                        <Button
                          variant="ghost"
                          onClick={() => setRevokeTarget(k)}
                          testId={`revoke-key-btn-${k.id}`}
                          leftIcon={
                            <ShieldOff className="h-4 w-4 text-status-danger-text" />
                          }
                        >
                          Revoke
                        </Button>
                      ) : (
                        <span className="text-xs text-ink-muted pr-2">Deactivated</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile Stacked Cards */}
          <div className="md:hidden divide-y divide-border">
            {keys.map((k) => (
              <div key={k.id} className="p-4 space-y-2.5">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="text-sm font-semibold text-ink">{k.name}</p>
                    <p className="text-xs font-mono text-ink-muted">{k.keyPrefix}…</p>
                  </div>
                  <Badge
                    label={k.active ? "Active" : "Revoked"}
                    variant={k.active ? "SUCCESS" : "DANGER"}
                  />
                </div>
                <div className="text-xs font-mono text-ink-secondary space-y-1">
                  <p>Origins: {k.allowedOrigins}</p>
                  <p>Scopes: {k.scopes}</p>
                </div>
                {k.active ? (
                  <Button
                    variant="destructive"
                    fullWidth
                    onClick={() => setRevokeTarget(k)}
                  >
                    Revoke Key
                  </Button>
                ) : null}
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* Create Key Modal */}
      <Modal
        open={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        title="Generate Third-Party SDK API Key"
        subtitle="Only the SHA-256 digest is persisted in PostgreSQL; the secret is shown once."
        preventClose={creating}
        footer={
          <>
            <Button variant="secondary" onClick={() => setCreateModalOpen(false)}>
              Cancel
            </Button>
            <Button
              type="submit"
              form="create-api-key-form"
              variant="primary"
              loading={creating}
              testId="confirm-create-api-key-btn"
            >
              Generate Key
            </Button>
          </>
        }
      >
        <form id="create-api-key-form" onSubmit={handleCreateSubmit} className="space-y-4">
          <div>
            <label
              htmlFor="apikey-name"
              className="block text-sm font-medium text-ink-secondary mb-1"
            >
              Property / Integration Name
            </label>
            <input
              id="apikey-name"
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Lumina Creator Storefront (Prod)"
              className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-sm text-ink"
            />
          </div>

          <div>
            <label
              htmlFor="apikey-origins"
              className="block text-sm font-medium text-ink-secondary mb-1"
            >
              Allowed Origins (comma-separated or <code className="font-mono">*</code>)
            </label>
            <input
              id="apikey-origins"
              type="text"
              required
              value={allowedOrigins}
              onChange={(e) => setAllowedOrigins(e.target.value)}
              placeholder="https://store.example.com,http://localhost:5173"
              className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface font-mono text-sm text-ink"
            />
          </div>

          <div>
            <label
              htmlFor="apikey-scopes"
              className="block text-sm font-medium text-ink-secondary mb-1"
            >
              Permitted SDK Scopes
            </label>
            <select
              id="apikey-scopes"
              value={scopes}
              onChange={(e) => setScopes(e.target.value)}
              className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface font-mono text-sm text-ink"
            >
              <option value="sdk:read,sdk:write">sdk:read,sdk:write (Full Banner + Write)</option>
              <option value="sdk:read">sdk:read (Read-Only Consent Check)</option>
            </select>
          </div>
        </form>
      </Modal>

      {/* One-Time Plaintext Secret Reveal Modal */}
      <Modal
        open={Boolean(createdSecret)}
        onClose={() => setCreatedSecret(null)}
        size="lg"
        title="Copy Your New SDK API Key"
        subtitle="Store this key securely now—it will never be retrievable again after closing."
        footer={
          <Button variant="primary" onClick={() => setCreatedSecret(null)}>
            I Have Saved This Key
          </Button>
        }
      >
        {createdSecret ? (
          <div className="space-y-4">
            <div className="rounded-control bg-status-warning-bg border border-status-warning-border p-3.5 flex items-start gap-2.5 text-xs text-ink">
              <AlertTriangle
                className="h-4 w-4 text-status-warning-text shrink-0 mt-0.5"
                aria-hidden="true"
              />
              <span>
                ConsentHub stores only <code className="font-mono">SHA-256(key)</code> in{" "}
                <code className="font-mono">api_keys.key_hash</code>. If lost, revoke this key
                and issue a replacement.
              </span>
            </div>

            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-ink-secondary mb-1.5">
                One-Time Secret Key
              </p>
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 rounded-control bg-subtle border border-border p-3">
                <code
                  data-testid="created-plaintext-api-key"
                  className="text-xs font-mono font-semibold text-ink break-all"
                >
                  {createdSecret.plainTextKey}
                </code>
                <Button
                  variant="secondary"
                  onClick={async () => {
                    await navigator.clipboard.writeText(createdSecret.plainTextKey).catch(() => {});
                    setCopiedKey(true);
                    window.setTimeout(() => setCopiedKey(false), 2000);
                  }}
                  leftIcon={
                    copiedKey ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />
                  }
                >
                  {copiedKey ? "Copied" : "Copy Key"}
                </Button>
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between mb-1.5">
                <p className="text-xs font-semibold uppercase tracking-wider text-ink-secondary">
                  HTML Embed Snippet
                </p>
                <button
                  type="button"
                  onClick={async () => {
                    await navigator.clipboard.writeText(embedSnippet).catch(() => {});
                    setCopiedSnippet(true);
                    window.setTimeout(() => setCopiedSnippet(false), 2000);
                  }}
                  className="inline-flex items-center gap-1 min-h-tap px-2 text-xs font-semibold text-brand hover:underline"
                >
                  {copiedSnippet ? "Copied Snippet" : "Copy HTML"}
                </button>
              </div>
              <pre className="rounded-control bg-inverse text-ink-inverse p-3.5 text-xs font-mono overflow-x-auto">
                {embedSnippet}
              </pre>
            </div>
          </div>
        ) : null}
      </Modal>

      {/* Revoke Confirmation Modal */}
      <Modal
        open={Boolean(revokeTarget)}
        onClose={() => setRevokeTarget(null)}
        size="sm"
        title="Revoke SDK API Key?"
        subtitle={revokeTarget ? `${revokeTarget.name} (${revokeTarget.keyPrefix}…)` : ""}
        preventClose={revoking}
        footer={
          <>
            <Button variant="secondary" onClick={() => setRevokeTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              loading={revoking}
              onClick={() => void handleConfirmRevoke()}
              testId="confirm-revoke-key-btn"
            >
              Revoke Immediately
            </Button>
          </>
        }
      >
        <p className="text-sm text-ink-secondary">
          Any external host page embedding <code className="font-mono">/consent.js</code> with
          this API key will immediately receive <code className="font-mono">401 INVALID_API_KEY</code>.
        </p>
      </Modal>
    </div>
  );
};
