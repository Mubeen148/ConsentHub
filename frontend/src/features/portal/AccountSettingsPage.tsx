import React, { useEffect, useState } from "react";
import { useMutation } from "@apollo/client";
import { Link, useNavigate } from "react-router-dom";
import { Check, Copy, LogOut, RotateCcw, Save, UserCheck } from "lucide-react";
import type { RegionCode, UserProfile } from "../../types/contracts";
import { UPDATE_MY_PROFILE } from "../../graphql/operations";
import { usePrivacySession } from "../../context/PrivacySessionContext";
import { formatDateTime } from "../../lib/format";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { KpiGridSkeleton } from "../../components/ui/FeedbackStates";
import { useToast } from "../../components/ui/ToastProvider";

export const AccountSettingsPage: React.FC = () => {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const {
    user,
    authLoading,
    region: sessionRegion,
    anonSubjectKey,
    setRegion: setSessionRegion,
    updateCurrentUser,
    logout,
    resetVisitorIdentity,
  } = usePrivacySession();

  const [fullName, setFullName] = useState(user?.fullName ?? "");
  const [region, setRegion] = useState<RegionCode>(user?.region ?? sessionRegion);
  const [copiedSubject, setCopiedSubject] = useState(false);

  useEffect(() => {
    if (user) {
      setFullName(user.fullName);
      setRegion(user.region);
    }
  }, [user]);

  const [updateMyProfile, { loading: saving }] = useMutation<{
    updateMyProfile: UserProfile;
  }>(UPDATE_MY_PROFILE);

  if (authLoading) {
    return <KpiGridSkeleton count={2} />;
  }

  const activeSubject = user ? user.subjectKey : anonSubjectKey;

  const handleCopySubject = async () => {
    try {
      await navigator.clipboard.writeText(activeSubject);
      setCopiedSubject(true);
      window.setTimeout(() => setCopiedSubject(false), 2000);
    } catch {
      // Ignore clipboard failures
    }
  };

  if (!user) {
    return (
      <div className="max-w-2xl mx-auto space-y-6">
        <div className="space-y-1.5">
          <Badge label="Pseudonymous Visitor Session" variant="DEFAULT" />
          <h1 className="text-2xl font-bold font-display text-ink">
            Visitor Identity &amp; Session Settings
          </h1>
          <p className="text-sm text-ink-secondary">
            ConsentHub lets you manage preferences without registering an account. Sign in to sync
            preferences across devices or submit verified DSAR tickets.
          </p>
        </div>

        <Card header="Pseudonymous Subject Identifier">
          <div className="space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 rounded-control bg-subtle border border-border-subtle px-3.5 py-2.5">
              <span className="text-xs font-mono text-ink break-all">{anonSubjectKey}</span>
              <Button
                variant="secondary"
                onClick={() => void handleCopySubject()}
                leftIcon={
                  copiedSubject ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />
                }
              >
                {copiedSubject ? "Copied" : "Copy Key"}
              </Button>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <Button
                variant="secondary"
                onClick={() => {
                  resetVisitorIdentity();
                  showToast({
                    title: "Generated fresh visitor identity",
                    description: "Previous local anon:<uuid> replaced.",
                    variant: "info",
                  });
                }}
                leftIcon={<RotateCcw className="h-4 w-4" />}
              >
                Reset Visitor Identity
              </Button>
              <Link to="/login">
                <Button variant="primary">Sign In</Button>
              </Link>
              <Link to="/signup">
                <Button variant="ghost">Create Account</Button>
              </Link>
            </div>
          </div>
        </Card>
      </div>
    );
  }

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (fullName.trim().length < 2) {
      showToast({
        title: "Invalid name",
        description: "Full name must be at least 2 characters.",
        variant: "warning",
      });
      return;
    }
    try {
      const { data } = await updateMyProfile({
        variables: {
          input: {
            fullName: fullName.trim(),
            region,
          },
        },
      });
      if (data?.updateMyProfile) {
        updateCurrentUser(data.updateMyProfile);
        setSessionRegion(data.updateMyProfile.region);
        showToast({
          title: "Profile updated",
          description: `Default privacy region set to ${data.updateMyProfile.region}.`,
          variant: "success",
        });
      }
    } catch (err) {
      showToast({
        title: "Unable to update profile",
        description: err instanceof Error ? err.message : "Please try again.",
        variant: "danger",
      });
    }
  };

  const handleLogout = async () => {
    await logout();
    showToast({ title: "Signed out", variant: "success" });
    navigate("/portal");
  };

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="space-y-1.5">
        <div className="flex items-center gap-2">
          <Badge
            label={user.role === "ADMIN" ? "ROLE_ADMIN" : "ROLE_USER"}
            variant={user.role === "ADMIN" ? "GDPR" : "SUCCESS"}
          />
          <span className="text-xs font-mono text-ink-muted">
            Member since {formatDateTime(user.createdAt)}
          </span>
        </div>
        <h1 className="text-2xl font-bold font-display text-ink">
          Account &amp; Jurisdiction Settings
        </h1>
        <p className="text-sm text-ink-secondary">
          Manage minimal profile attributes collected under GDPR Art. 5(1)(c) data minimization.
        </p>
      </div>

      <Card
        header={
          <div className="flex items-center gap-2">
            <UserCheck className="h-4 w-4 text-brand" aria-hidden="true" />
            <span>Profile &amp; Default Privacy Region</span>
          </div>
        }
      >
        <form onSubmit={handleSaveProfile} className="space-y-4">
          <div>
            <label
              htmlFor="profile-email-readonly"
              className="block text-sm font-medium text-ink-secondary mb-1.5"
            >
              Verified email address
            </label>
            <input
              id="profile-email-readonly"
              type="email"
              value={user.email}
              disabled
              className="w-full min-h-tap px-3 rounded-control border border-border bg-subtle text-sm text-ink-secondary font-mono cursor-not-allowed"
            />
          </div>

          <div>
            <label
              htmlFor="profile-fullname"
              className="block text-sm font-medium text-ink-secondary mb-1.5"
            >
              Display name
            </label>
            <input
              id="profile-fullname"
              type="text"
              required
              minLength={2}
              maxLength={120}
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-base text-ink"
            />
          </div>

          <div>
            <label
              htmlFor="profile-region"
              className="block text-sm font-medium text-ink-secondary mb-1.5"
            >
              Default regulatory region
            </label>
            <select
              id="profile-region"
              value={region}
              onChange={(e) => setRegion(e.target.value as RegionCode)}
              className="w-full min-h-tap px-3 rounded-control border border-border-control bg-surface text-base text-ink"
            >
              <option value="EU">EU · GDPR Strict Opt-In</option>
              <option value="US_CA">US_CA · California CCPA Opt-Out</option>
              <option value="GLOBAL">GLOBAL · Default Baseline</option>
            </select>
          </div>

          <div className="pt-2 flex flex-wrap items-center justify-between gap-3">
            <Button
              type="submit"
              variant="primary"
              loading={saving}
              testId="save-profile-btn"
              leftIcon={<Save className="h-4 w-4" />}
            >
              Save Profile Settings
            </Button>

            <Button
              type="button"
              variant="secondary"
              onClick={() => void handleLogout()}
              leftIcon={<LogOut className="h-4 w-4" />}
            >
              Sign Out
            </Button>
          </div>
        </form>
      </Card>

      <Card header="Cryptographic Subject Binding">
        <div className="space-y-2">
          <p className="text-xs text-ink-secondary">
            Your consent ledger entries and DSAR portability exports are bound server-side to this
            canonical subject key:
          </p>
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 rounded-control bg-subtle border border-border-subtle px-3.5 py-2">
            <span className="text-xs font-mono text-ink break-all">{user.subjectKey}</span>
            <Button
              variant="ghost"
              onClick={() => void handleCopySubject()}
              leftIcon={
                copiedSubject ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />
              }
            >
              {copiedSubject ? "Copied" : "Copy"}
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
};
