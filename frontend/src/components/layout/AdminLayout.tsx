import React, { useEffect, useState } from "react";
import {
  Link,
  NavLink,
  Navigate,
  Outlet,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { useQuery } from "@apollo/client";
import {
  ArrowLeft,
  BarChart3,
  ExternalLink,
  FileCheck2,
  FlaskConical,
  Globe2,
  Inbox,
  KeyRound,
  LogOut,
  Menu,
  ScrollText,
  ShieldAlert,
  ShieldCheck,
  X,
} from "lucide-react";
import type { DataRequest } from "../../types/contracts";
import { GET_ADMIN_DATA_REQUESTS } from "../../graphql/operations";
import { usePrivacySession } from "../../context/PrivacySessionContext";
import { Badge } from "../ui/Badge";
import { Button } from "../ui/Button";
import { Card } from "../ui/Card";
import { KpiGridSkeleton } from "../ui/FeedbackStates";
import { useToast } from "../ui/ToastProvider";

interface AdminNavItem {
  to: string;
  label: string;
  end?: boolean;
  icon: React.FC<{ className?: string }>;
  showPendingCount?: boolean;
}

const ADMIN_NAV_ITEMS: AdminNavItem[] = [
  { to: "/admin", label: "Overview & Analytics", end: true, icon: BarChart3 },
  { to: "/admin/policies", label: "Purposes & Policies", icon: FileCheck2 },
  { to: "/admin/regions", label: "Regional Rules", icon: Globe2 },
  { to: "/admin/experiments", label: "SDUI & Experiments", icon: FlaskConical },
  {
    to: "/admin/requests",
    label: "DSAR Queue",
    icon: Inbox,
    showPendingCount: true,
  },
  { to: "/admin/sdk-keys", label: "API Keys & SDK", icon: KeyRound },
  { to: "/admin/audit", label: "Audit Ledger", icon: ScrollText },
];

export const AdminLayout: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const { user, authLoading, logout } = usePrivacySession();
  const [drawerOpen, setDrawerOpen] = useState(false);

  const isAdmin = user?.role === "ADMIN";

  const { data: pendingData } = useQuery<{ adminDataRequests: DataRequest[] }>(
    GET_ADMIN_DATA_REQUESTS,
    {
      variables: { status: "PENDING" },
      skip: !isAdmin,
      pollInterval: 30000,
    }
  );

  const pendingCount = pendingData?.adminDataRequests?.length ?? 0;

  useEffect(() => {
    setDrawerOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!drawerOpen) {
      return;
    }
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setDrawerOpen(false);
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [drawerOpen]);

  if (authLoading) {
    return (
      <div className="min-h-screen bg-canvas p-6 md:p-10 max-w-admin mx-auto space-y-6">
        <KpiGridSkeleton count={4} />
      </div>
    );
  }

  if (!user) {
    return (
      <Navigate
        to="/login"
        state={{ from: location.pathname }}
        replace
      />
    );
  }

  if (user.role !== "ADMIN") {
    return (
      <div className="min-h-screen bg-canvas flex items-center justify-center p-6">
        <div className="max-w-md w-full">
          <Card>
            <div className="flex flex-col items-center text-center space-y-4">
              <div className="h-12 w-12 rounded-card bg-status-danger-bg border border-status-danger-border flex items-center justify-center text-status-danger-text">
                <ShieldAlert className="h-6 w-6" aria-hidden="true" />
              </div>
              <div className="space-y-1">
                <h1 className="text-lg font-bold text-ink font-display">
                  Administrator Role Required
                </h1>
                <p className="text-sm text-ink-secondary">
                  Signed in as <span className="font-mono">{user.email}</span> ({user.role}).
                  Governance controls are restricted to <span className="font-mono">ROLE_ADMIN</span>.
                </p>
              </div>
              <Button variant="primary" fullWidth onClick={() => navigate("/portal")}>
                Return to Privacy Center
              </Button>
            </div>
          </Card>
        </div>
      </div>
    );
  }

  const currentSection =
    ADMIN_NAV_ITEMS.find((item) =>
      item.end
        ? location.pathname === item.to
        : location.pathname.startsWith(item.to)
    )?.label ?? "Governance Console";

  const handleLogout = async () => {
    try {
      await logout();
      showToast({
        title: "Signed out of Admin Console",
        variant: "success",
      });
      navigate("/login");
    } catch {
      showToast({
        title: "Sign out failed",
        variant: "danger",
      });
    }
  };

  const renderSidebarContent = () => (
    <div className="flex flex-col h-full justify-between">
      <div className="space-y-5">
        <div className="flex items-center justify-between px-2 pt-1">
          <Link
            to="/admin"
            className="inline-flex items-center gap-2.5 min-h-tap rounded-control px-1"
          >
            <span className="h-8 w-8 rounded-control bg-action text-ink-inverse inline-flex items-center justify-center">
              <ShieldCheck
                className="h-4 w-4 text-status-success-border"
                aria-hidden="true"
              />
            </span>
            <div>
              <span className="block text-sm font-bold font-display text-ink leading-none">
                ConsentHub
              </span>
              <span className="block text-[11px] font-mono text-ink-muted mt-0.5">
                Governance Console
              </span>
            </div>
          </Link>
          {drawerOpen ? (
            <button
              type="button"
              onClick={() => setDrawerOpen(false)}
              aria-label="Close navigation menu"
              className="lg:hidden inline-flex items-center justify-center min-w-tap min-h-tap rounded-control text-ink-secondary hover:bg-subtle"
            >
              <X className="h-5 w-5" aria-hidden="true" />
            </button>
          ) : null}
        </div>

        <nav aria-label="Admin Governance navigation" className="space-y-1">
          {ADMIN_NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  [
                    "flex items-center justify-between min-h-tap px-3 rounded-control text-sm transition-colors",
                    isActive
                      ? "bg-brand-subtle text-brand-hover font-semibold"
                      : "text-ink-secondary hover:text-ink hover:bg-subtle font-medium",
                  ].join(" ")
                }
              >
                <span className="inline-flex items-center gap-2.5 truncate">
                  <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                  <span className="truncate">{item.label}</span>
                </span>
                {item.showPendingCount && pendingCount > 0 ? (
                  <span
                    aria-label={`${pendingCount} pending requests`}
                    className="inline-flex items-center justify-center h-5 min-w-[20px] px-1.5 rounded-pill bg-status-warning-bg border border-status-warning-border text-status-warning-text text-xs font-semibold tabular-nums"
                  >
                    {pendingCount}
                  </span>
                ) : null}
              </NavLink>
            );
          })}
        </nav>
      </div>

      <div className="space-y-3 pt-4 border-t border-border-subtle">
        <div className="space-y-1">
          <Link
            to="/portal"
            className="flex items-center gap-2 min-h-tap px-3 rounded-control text-xs font-semibold text-ink-secondary hover:text-ink hover:bg-subtle transition-colors"
          >
            <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
            <span>Switch to Privacy Center</span>
          </Link>
          <a
            href="/sdk-demo.html"
            className="flex items-center justify-between min-h-tap px-3 rounded-control text-xs font-semibold text-brand hover:bg-brand-subtle transition-colors"
          >
            <span>Live Third-Party SDK Demo</span>
            <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
          </a>
        </div>

        <div className="rounded-control bg-subtle/80 border border-border-subtle p-3 space-y-2">
          <div className="min-w-0">
            <p className="text-xs font-semibold text-ink truncate">{user.fullName}</p>
            <p className="text-[11px] font-mono text-ink-muted truncate">{user.email}</p>
          </div>
          <Button
            variant="secondary"
            fullWidth
            onClick={handleLogout}
            leftIcon={<LogOut className="h-3.5 w-3.5" />}
          >
            Sign Out
          </Button>
        </div>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-canvas text-ink">
      {/* Desktop Fixed Sidebar (>= 1024px) */}
      <aside className="hidden lg:block fixed inset-y-0 left-0 w-sidebar bg-surface border-r border-border px-3 py-4 z-30">
        {renderSidebarContent()}
      </aside>

      {/* Mobile / Tablet Slide-Over Drawer (< 1024px) */}
      {drawerOpen ? (
        <div
          className="lg:hidden fixed inset-0 z-50 bg-[rgba(20,21,25,0.52)] backdrop-blur-[2px]"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) {
              setDrawerOpen(false);
            }
          }}
        >
          <aside
            role="dialog"
            aria-modal="true"
            aria-label="Admin navigation drawer"
            className="w-[280px] max-w-[85vw] h-full bg-surface border-r border-border px-3 py-4 shadow-lg"
          >
            {renderSidebarContent()}
          </aside>
        </div>
      ) : null}

      {/* Main Content Area */}
      <div className="lg:ml-sidebar min-h-screen flex flex-col">
        <header className="sticky top-0 z-20 h-topbar border-b border-border bg-surface/95 backdrop-blur px-4 md:px-6 xl:px-8 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3 min-w-0">
            <button
              type="button"
              onClick={() => setDrawerOpen(true)}
              aria-label="Open navigation menu"
              data-testid="admin-mobile-menu-btn"
              className="lg:hidden inline-flex items-center justify-center min-w-tap min-h-tap -ml-2 rounded-control text-ink hover:bg-subtle"
            >
              <Menu className="h-5 w-5" aria-hidden="true" />
            </button>
            <h1 className="text-base md:text-lg font-bold font-display text-ink truncate">
              {currentSection}
            </h1>
          </div>

          <div className="flex items-center gap-2.5 shrink-0">
            <Badge label="ROLE_ADMIN" variant="GDPR" />
            <Link
              to="/portal"
              className="hidden sm:inline-flex items-center min-h-tap px-3 rounded-control border border-border bg-surface hover:bg-subtle text-xs font-semibold text-ink transition-colors"
            >
              User Portal
            </Link>
          </div>
        </header>

        <main className="flex-1 w-full max-w-admin mx-auto px-4 py-6 md:p-6 xl:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
