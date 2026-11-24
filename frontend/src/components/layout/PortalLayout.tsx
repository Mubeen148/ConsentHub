import React from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  ExternalLink,
  Globe,
  History,
  LayoutDashboard,
  LogOut,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  UserCheck,
  FileSpreadsheet,
} from "lucide-react";
import type { RegionCode } from "../../types/contracts";
import { usePrivacySession } from "../../context/PrivacySessionContext";
import { Badge } from "../ui/Badge";
import { Button } from "../ui/Button";
import { useToast } from "../ui/ToastProvider";

const PORTAL_TABS = [
  { to: "/portal", label: "Overview", end: true, icon: Sparkles },
  { to: "/portal/preferences", label: "Preferences", end: false, icon: SlidersHorizontal },
  { to: "/portal/history", label: "Consent Ledger", end: false, icon: History },
  { to: "/portal/requests", label: "Data Rights (DSAR)", end: false, icon: FileSpreadsheet },
  { to: "/portal/settings", label: "Account", end: false, icon: UserCheck },
];

export const PortalLayout: React.FC = () => {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const {
    user,
    region,
    setRegion,
    logout,
    openConsentBanner,
  } = usePrivacySession();

  const handleRegionChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const nextRegion = e.target.value as RegionCode;
    setRegion(nextRegion);
    showToast({
      title: `Region switched to ${
        nextRegion === "EU"
          ? "EU · GDPR"
          : nextRegion === "US_CA"
            ? "California · CCPA"
            : "Global · Default"
      }`,
      description: "SDUI screens and consent rules now reflect this regulatory regime.",
      variant: "info",
    });
  };

  const handleLogout = async () => {
    try {
      await logout();
      showToast({
        title: "Signed out",
        description: "Your session cookie has been cleared.",
        variant: "success",
      });
      navigate("/portal");
    } catch {
      showToast({
        title: "Sign out failed",
        description: "Please try signing out again.",
        variant: "danger",
      });
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-canvas text-ink">
      <header className="sticky top-0 z-30 bg-surface border-b border-border">
        <div className="max-w-portal mx-auto h-topbar px-4 md:px-6 flex items-center justify-between gap-3">
          <div className="flex items-center gap-6 min-w-0">
            <Link
              to="/portal"
              className="inline-flex items-center gap-2.5 min-h-tap shrink-0 rounded-control px-1"
            >
              <span className="h-8 w-8 rounded-control bg-action text-ink-inverse inline-flex items-center justify-center">
                <ShieldCheck className="h-4 w-4 text-status-success-border" aria-hidden="true" />
              </span>
              <span className="text-base font-bold font-display tracking-tight text-ink">
                ConsentHub
              </span>
            </Link>

            <nav
              aria-label="Privacy Center sections"
              className="hidden lg:flex items-center h-topbar gap-1"
            >
              {PORTAL_TABS.map((tab) => (
                <NavLink
                  key={tab.to}
                  to={tab.to}
                  end={tab.end}
                  className={({ isActive }) =>
                    [
                      "inline-flex items-center h-topbar px-3 text-sm border-b-2 transition-colors whitespace-nowrap",
                      isActive
                        ? "border-border-strong text-ink font-semibold"
                        : "border-transparent text-ink-secondary hover:text-ink font-medium",
                    ].join(" ")
                  }
                >
                  {tab.label}
                </NavLink>
              ))}
            </nav>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <div className="relative flex items-center">
              <Globe
                className="h-4 w-4 text-ink-secondary pointer-events-none absolute left-3"
                aria-hidden="true"
              />
              <label htmlFor="header-region-select" className="sr-only">
                Simulate Regulatory Region
              </label>
              <select
                id="header-region-select"
                data-testid="region-switcher-select"
                aria-label="Simulate Regulatory Region"
                value={region}
                onChange={handleRegionChange}
                className="min-h-tap pl-8 pr-7 py-2 rounded-pill border border-border-control bg-surface text-xs font-semibold text-ink hover:border-border-strong cursor-pointer"
              >
                <option value="EU">EU · GDPR</option>
                <option value="US_CA">California · CCPA</option>
                <option value="GLOBAL">Global · Default</option>
              </select>
            </div>

            <button
              type="button"
              onClick={openConsentBanner}
              data-testid="open-banner-btn"
              className="hidden sm:inline-flex items-center justify-center min-h-tap px-3 rounded-control border border-border bg-subtle/70 hover:bg-subtle text-xs font-semibold text-ink transition-colors"
            >
              Banner
            </button>

            <a
              href="/sdk-demo.html"
              data-testid="nav-sdk-demo-link"
              className="hidden md:inline-flex items-center gap-1.5 min-h-tap px-3 rounded-control text-xs font-semibold text-brand hover:bg-brand-subtle transition-colors"
            >
              <span>Live SDK Demo</span>
              <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
            </a>

            {user?.role === "ADMIN" ? (
              <Link
                to="/admin"
                data-testid="nav-admin-console-link"
                className="inline-flex items-center gap-1.5 min-h-tap px-3 rounded-control bg-brand-subtle border border-badge-gdpr-border text-xs font-semibold text-brand-hover hover:bg-badge-gdpr-bg transition-colors"
              >
                <LayoutDashboard className="h-3.5 w-3.5" aria-hidden="true" />
                <span className="hidden sm:inline">Admin</span>
              </Link>
            ) : null}

            {user ? (
              <div className="flex items-center gap-1.5">
                <Link
                  to="/portal/settings"
                  className="hidden xl:inline-flex items-center gap-2 min-h-tap px-2.5 rounded-control hover:bg-subtle text-xs font-medium text-ink-secondary"
                >
                  <Badge
                    label={user.fullName.split(" ")[0] || user.email}
                    variant="DEFAULT"
                    showDot={false}
                  />
                </Link>
                <button
                  type="button"
                  onClick={handleLogout}
                  aria-label="Sign out"
                  data-testid="logout-btn"
                  className="inline-flex items-center justify-center gap-1.5 min-h-tap min-w-tap px-2.5 rounded-control text-xs font-semibold text-ink-secondary hover:text-ink hover:bg-subtle transition-colors"
                >
                  <LogOut className="h-4 w-4" aria-hidden="true" />
                  <span className="hidden sm:inline">Sign Out</span>
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-1.5">
                <Link
                  to="/login"
                  data-testid="nav-login-link"
                  className="inline-flex items-center justify-center min-h-tap px-3.5 rounded-control text-sm font-semibold text-ink hover:bg-subtle transition-colors"
                >
                  Sign In
                </Link>
                <Link to="/signup" className="hidden sm:inline-flex">
                  <Button variant="primary">Sign Up</Button>
                </Link>
              </div>
            )}
          </div>
        </div>

        {/* Mobile & Tablet Horizontal Scrollable Tab Bar (< 1024px) */}
        <nav
          aria-label="Privacy Center mobile navigation"
          className="lg:hidden border-t border-border-subtle bg-surface px-4 py-1.5 overflow-x-auto flex items-center gap-1.5"
        >
          {PORTAL_TABS.map((tab) => {
            const Icon = tab.icon;
            return (
              <NavLink
                key={tab.to}
                to={tab.to}
                end={tab.end}
                className={({ isActive }) =>
                  [
                    "inline-flex items-center gap-1.5 min-h-tap px-3.5 rounded-pill text-xs font-semibold whitespace-nowrap transition-colors",
                    isActive
                      ? "bg-action text-ink-inverse"
                      : "bg-subtle/70 text-ink-secondary hover:text-ink hover:bg-subtle",
                  ].join(" ")
                }
              >
                <Icon className="h-3.5 w-3.5" aria-hidden="true" />
                <span>{tab.label}</span>
              </NavLink>
            );
          })}
          <button
            type="button"
            onClick={openConsentBanner}
            className="sm:hidden inline-flex items-center min-h-tap px-3.5 rounded-pill text-xs font-semibold whitespace-nowrap bg-subtle/70 text-ink-secondary hover:text-ink"
          >
            Consent Banner
          </button>
          <a
            href="/sdk-demo.html"
            className="md:hidden inline-flex items-center gap-1 min-h-tap px-3.5 rounded-pill text-xs font-semibold whitespace-nowrap bg-brand-subtle text-brand-hover"
          >
            <span>Live SDK Demo</span>
            <ExternalLink className="h-3 w-3" aria-hidden="true" />
          </a>
        </nav>
      </header>

      <main className="flex-1 w-full max-w-portal mx-auto px-4 pt-6 pb-20 md:px-6 md:pt-8 md:pb-20">
        <Outlet />
      </main>

      <footer className="border-t border-border bg-surface py-6 px-4 md:px-6">
        <div className="max-w-portal mx-auto flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-ink-muted">
          <div className="flex items-center gap-2">
            <ShieldCheck className="h-4 w-4 text-ink-secondary" aria-hidden="true" />
            <span>
              ConsentHub Privacy Governance · Symmetric Choice Architecture · SHA-256 Proof Ledger
            </span>
          </div>
          <div className="flex items-center gap-4">
            <button
              type="button"
              onClick={openConsentBanner}
              className="min-h-tap px-2 font-medium text-ink-secondary hover:text-ink underline underline-offset-4"
            >
              Manage Cookie &amp; Privacy Banner
            </button>
            <a
              href="/sdk-demo.html"
              className="min-h-tap inline-flex items-center gap-1 px-2 font-medium text-brand hover:underline underline-offset-4"
            >
              <span>Third-Party SDK Demo</span>
              <ExternalLink className="h-3 w-3" aria-hidden="true" />
            </a>
          </div>
        </div>
      </footer>
    </div>
  );
};
