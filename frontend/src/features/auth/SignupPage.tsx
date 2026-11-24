import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ShieldCheck } from "lucide-react";
import type { RegionCode } from "../../types/contracts";
import { apiSignup, RestApiError } from "../../lib/restClient";
import { usePrivacySession } from "../../context/PrivacySessionContext";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { ErrorState } from "../../components/ui/FeedbackStates";
import { useToast } from "../../components/ui/ToastProvider";

const PASSWORD_POLICY_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*[\d\W]).{8,128}$/;

export const SignupPage: React.FC = () => {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const { region: currentRegion, anonSubjectKey, applyAuthSession } = usePrivacySession();

  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [region, setRegion] = useState<RegionCode>(currentRegion);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [correlationId, setCorrelationId] = useState<string | undefined>(undefined);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setCorrelationId(undefined);

    const errors: Record<string, string> = {};
    if (fullName.trim().length < 2 || fullName.trim().length > 120) {
      errors.fullName = "Full name must be between 2 and 120 characters.";
    }
    if (!email.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      errors.email = "Please enter a valid email address.";
    }
    if (!PASSWORD_POLICY_REGEX.test(password)) {
      errors.password =
        "Must be at least 8 characters and include uppercase, lowercase, and a number or symbol.";
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }
    setFieldErrors({});

    try {
      setSubmitting(true);
      const session = await apiSignup({
        fullName: fullName.trim(),
        email: email.trim().toLowerCase(),
        password,
        region,
        anonymousSubjectKey: anonSubjectKey,
      });
      await applyAuthSession(session);
      showToast({
        title: "Account created",
        description:
          "Any prior visitor consent preferences have been linked to your profile.",
        variant: "success",
      });
      navigate("/portal", { replace: true });
    } catch (err) {
      if (err instanceof RestApiError) {
        setErrorMessage(err.message);
        setCorrelationId(err.correlationId);
        if (err.fieldErrors) {
          setFieldErrors(err.fieldErrors);
        }
      } else {
        setErrorMessage("Unable to create account right now. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-canvas flex flex-col justify-center px-4 py-12">
      <div className="w-full max-w-[480px] mx-auto space-y-6">
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
            Create your Privacy Center account
          </h1>
          <p className="text-sm text-ink-secondary">
            Manage cross-device preferences, cryptographic consent proofs, and DSAR portability.
          </p>
        </div>

        <Card>
          <div className="space-y-5">
            {errorMessage ? (
              <ErrorState
                title="Registration could not be completed"
                message={errorMessage}
                correlationId={correlationId}
              />
            ) : null}

            <form onSubmit={handleSubmit} noValidate className="space-y-4">
              <div>
                <label
                  htmlFor="signup-fullname"
                  className="block text-sm font-medium text-ink-secondary mb-1.5"
                >
                  Full name
                </label>
                <input
                  id="signup-fullname"
                  type="text"
                  autoComplete="name"
                  required
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  aria-invalid={Boolean(fieldErrors.fullName)}
                  aria-describedby={fieldErrors.fullName ? "signup-fullname-err" : undefined}
                  placeholder="Claire Dubois"
                  className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-base text-ink placeholder:text-ink-muted"
                />
                {fieldErrors.fullName ? (
                  <p id="signup-fullname-err" className="mt-1.5 text-xs text-status-danger-text">
                    {fieldErrors.fullName}
                  </p>
                ) : null}
              </div>

              <div>
                <label
                  htmlFor="signup-email"
                  className="block text-sm font-medium text-ink-secondary mb-1.5"
                >
                  Email address
                </label>
                <input
                  id="signup-email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  aria-invalid={Boolean(fieldErrors.email)}
                  aria-describedby={fieldErrors.email ? "signup-email-err" : undefined}
                  placeholder="claire.dubois@example.com"
                  className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-base text-ink placeholder:text-ink-muted"
                />
                {fieldErrors.email ? (
                  <p id="signup-email-err" className="mt-1.5 text-xs text-status-danger-text">
                    {fieldErrors.email}
                  </p>
                ) : null}
              </div>

              <div>
                <label
                  htmlFor="signup-region"
                  className="block text-sm font-medium text-ink-secondary mb-1.5"
                >
                  Default privacy jurisdiction
                </label>
                <select
                  id="signup-region"
                  value={region}
                  onChange={(e) => setRegion(e.target.value as RegionCode)}
                  className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-base text-ink"
                >
                  <option value="EU">European Union / EEA (GDPR Opt-In)</option>
                  <option value="US_CA">California, USA (CCPA / CPRA Opt-Out)</option>
                  <option value="GLOBAL">Global Baseline (Standard Notice)</option>
                </select>
              </div>

              <div>
                <label
                  htmlFor="signup-password"
                  className="block text-sm font-medium text-ink-secondary mb-1.5"
                >
                  Password
                </label>
                <input
                  id="signup-password"
                  type="password"
                  autoComplete="new-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  aria-invalid={Boolean(fieldErrors.password)}
                  aria-describedby="signup-password-help"
                  placeholder="StrongUser123!"
                  className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-base text-ink placeholder:text-ink-muted"
                />
                <p
                  id="signup-password-help"
                  className={`mt-1.5 text-xs ${
                    fieldErrors.password ? "text-status-danger-text" : "text-ink-muted"
                  }`}
                >
                  {fieldErrors.password ??
                    "8+ characters with uppercase, lowercase, and at least 1 digit or symbol."}
                </p>
              </div>

              <Button
                type="submit"
                variant="primary"
                fullWidth
                loading={submitting}
                testId="signup-submit-btn"
              >
                Create Account
              </Button>
            </form>

            <div className="pt-3 border-t border-border-subtle flex flex-col sm:flex-row items-center justify-between gap-2 text-sm text-ink-secondary">
              <span>Already have an account?</span>
              <Link
                to="/login"
                className="inline-flex items-center min-h-tap px-2 font-semibold text-brand hover:underline"
              >
                Sign in instead
              </Link>
            </div>
          </div>
        </Card>
      </div>
    </div>
  );
};
