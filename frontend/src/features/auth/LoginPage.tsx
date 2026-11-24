import React, { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { KeyRound, ShieldCheck, UserCheck } from "lucide-react";
import { apiLogin, RestApiError } from "../../lib/restClient";
import { usePrivacySession } from "../../context/PrivacySessionContext";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { ErrorState } from "../../components/ui/FeedbackStates";
import { useToast } from "../../components/ui/ToastProvider";

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { showToast } = useToast();
  const { anonSubjectKey, applyAuthSession } = usePrivacySession();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [correlationId, setCorrelationId] = useState<string | undefined>(undefined);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const fromPath =
    typeof (location.state as { from?: unknown } | null)?.from === "string"
      ? ((location.state as { from: string }).from)
      : null;

  const fillDemoUser = () => {
    setEmail("user@consenthub.io");
    setPassword("UserPass123!");
    setErrorMessage(null);
    setFieldErrors({});
  };

  const fillDemoAdmin = () => {
    setEmail("admin@consenthub.io");
    setPassword("AdminPass123!");
    setErrorMessage(null);
    setFieldErrors({});
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setCorrelationId(undefined);

    const nextFieldErrors: Record<string, string> = {};
    if (!email.trim()) {
      nextFieldErrors.email = "Email address is required.";
    }
    if (!password) {
      nextFieldErrors.password = "Password is required.";
    }
    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors);
      return;
    }
    setFieldErrors({});

    try {
      setSubmitting(true);
      const session = await apiLogin({
        email: email.trim().toLowerCase(),
        password,
        anonymousSubjectKey: anonSubjectKey,
      });
      await applyAuthSession(session);
      showToast({
        title: `Welcome back, ${session.user.fullName}`,
        description: `Signed in as ${session.user.role === "ADMIN" ? "Governance Administrator" : "Privacy Center Member"}.`,
        variant: "success",
      });
      if (fromPath && fromPath.startsWith("/") && !fromPath.startsWith("//")) {
        navigate(fromPath, { replace: true });
      } else if (session.user.role === "ADMIN") {
        navigate("/admin", { replace: true });
      } else {
        navigate("/portal", { replace: true });
      }
    } catch (err) {
      if (err instanceof RestApiError) {
        setErrorMessage(err.message);
        setCorrelationId(err.correlationId);
        if (err.fieldErrors) {
          setFieldErrors(err.fieldErrors);
        }
      } else {
        setErrorMessage("Unable to sign in right now. Please verify the backend is running.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-canvas flex flex-col justify-center px-4 py-12">
      <div className="w-full max-w-[460px] mx-auto space-y-6">
        <div className="text-center space-y-2">
          <Link
            to="/portal"
            className="inline-flex items-center gap-2.5 min-h-tap px-2 rounded-control"
          >
            <span className="h-9 w-9 rounded-control bg-action text-ink-inverse inline-flex items-center justify-center">
              <ShieldCheck className="h-5 w-5 text-status-success-border" aria-hidden="true" />
            </span>
            <span className="text-xl font-bold font-display text-ink">ConsentHub</span>
          </Link>
          <h1 className="text-xl font-bold font-display text-ink">
            Sign in to your account
          </h1>
          <p className="text-sm text-ink-secondary">
            Access your cryptographic consent ledger, DSAR portability exports, or Admin Governance.
          </p>
        </div>

        <Card>
          <div className="space-y-5">
            {/* Seeded Demo Quick-Fill Convenience Pills */}
            <div className="rounded-control bg-subtle/80 border border-border-subtle p-3 space-y-2">
              <p className="text-xs font-semibold uppercase tracking-wider text-ink-secondary">
                Quick-Fill Seeded Accounts
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={fillDemoUser}
                  data-testid="fill-demo-user-btn"
                  className="inline-flex items-center justify-center gap-1.5 min-h-tap px-3 rounded-control border border-border-control bg-surface hover:bg-canvas text-xs font-semibold text-ink transition-colors"
                >
                  <UserCheck className="h-3.5 w-3.5 text-brand" aria-hidden="true" />
                  <span>Fill Demo User</span>
                </button>
                <button
                  type="button"
                  onClick={fillDemoAdmin}
                  data-testid="fill-demo-admin-btn"
                  className="inline-flex items-center justify-center gap-1.5 min-h-tap px-3 rounded-control border border-border-control bg-surface hover:bg-canvas text-xs font-semibold text-ink transition-colors"
                >
                  <KeyRound className="h-3.5 w-3.5 text-brand" aria-hidden="true" />
                  <span>Fill Demo Admin</span>
                </button>
              </div>
            </div>

            {errorMessage ? (
              <ErrorState
                title="Authentication failed"
                message={errorMessage}
                correlationId={correlationId}
              />
            ) : null}

            <form onSubmit={handleSubmit} noValidate className="space-y-4">
              <div>
                <label
                  htmlFor="login-email"
                  className="block text-sm font-medium text-ink-secondary mb-1.5"
                >
                  Email address
                </label>
                <input
                  id="login-email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  aria-invalid={Boolean(fieldErrors.email)}
                  aria-describedby={fieldErrors.email ? "login-email-err" : undefined}
                  placeholder="user@consenthub.io"
                  className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-base text-ink placeholder:text-ink-muted"
                />
                {fieldErrors.email ? (
                  <p id="login-email-err" className="mt-1.5 text-xs text-status-danger-text">
                    {fieldErrors.email}
                  </p>
                ) : null}
              </div>

              <div>
                <label
                  htmlFor="login-password"
                  className="block text-sm font-medium text-ink-secondary mb-1.5"
                >
                  Password
                </label>
                <input
                  id="login-password"
                  type="password"
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  aria-invalid={Boolean(fieldErrors.password)}
                  aria-describedby={fieldErrors.password ? "login-password-err" : undefined}
                  placeholder="••••••••••••"
                  className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-base text-ink placeholder:text-ink-muted"
                />
                {fieldErrors.password ? (
                  <p id="login-password-err" className="mt-1.5 text-xs text-status-danger-text">
                    {fieldErrors.password}
                  </p>
                ) : null}
              </div>

              <Button
                type="submit"
                variant="primary"
                fullWidth
                loading={submitting}
                testId="login-submit-btn"
              >
                Sign In
              </Button>
            </form>

            <div className="pt-3 border-t border-border-subtle flex flex-col sm:flex-row items-center justify-between gap-2 text-sm text-ink-secondary">
              <span>New to ConsentHub?</span>
              <Link
                to="/signup"
                className="inline-flex items-center min-h-tap px-2 font-semibold text-brand hover:underline"
              >
                Create an account
              </Link>
            </div>
          </div>
        </Card>

        <div className="text-center">
          <Link
            to="/portal"
            className="inline-flex items-center min-h-tap px-3 text-xs font-medium text-ink-secondary hover:text-ink underline underline-offset-4"
          >
            Continue anonymously to Privacy Center
          </Link>
        </div>
      </div>
    </div>
  );
};
