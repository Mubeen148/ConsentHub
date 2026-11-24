import React from "react";
import { BrowserRouter, Link, Navigate, Route, Routes } from "react-router-dom";
import { ApolloProvider } from "@apollo/client";
import { apolloClient } from "./lib/apollo";
import { ToastProvider } from "./components/ui/ToastProvider";
import {
  PrivacySessionProvider,
  usePrivacySession,
} from "./context/PrivacySessionContext";
import { PortalLayout } from "./components/layout/PortalLayout";
import { AdminLayout } from "./components/layout/AdminLayout";
import { RegionConsentBanner } from "./features/banner/RegionConsentBanner";
import { SduiJsonInspectorModal } from "./features/banner/SduiJsonInspectorModal";
import { LoginPage } from "./features/auth/LoginPage";
import { SignupPage } from "./features/auth/SignupPage";
import { PortalOverviewPage } from "./features/portal/PortalOverviewPage";
import { PreferenceCenterPage } from "./features/portal/PreferenceCenterPage";
import { ConsentHistoryPage } from "./features/portal/ConsentHistoryPage";
import { DataRequestsPage } from "./features/portal/DataRequestsPage";
import { AccountSettingsPage } from "./features/portal/AccountSettingsPage";
import { AdminAnalyticsPage } from "./features/admin/AdminAnalyticsPage";
import { AdminPoliciesPage } from "./features/admin/AdminPoliciesPage";
import { AdminRegionsPage } from "./features/admin/AdminRegionsPage";
import { AdminExperimentsPage } from "./features/admin/AdminExperimentsPage";
import { AdminRequestsPage } from "./features/admin/AdminRequestsPage";
import { AdminApiKeysPage } from "./features/admin/AdminApiKeysPage";
import { AdminAuditPage } from "./features/admin/AdminAuditPage";
import { Button } from "./components/ui/Button";
import { Card } from "./components/ui/Card";

const GlobalOverlays: React.FC = () => {
  const { inspectedScreen, setInspectedScreen } = usePrivacySession();

  return (
    <>
      <RegionConsentBanner />
      <SduiJsonInspectorModal
        screen={inspectedScreen}
        onClose={() => setInspectedScreen(null)}
      />
    </>
  );
};

const NotFoundPage: React.FC = () => (
  <div className="min-h-screen bg-canvas flex items-center justify-center p-6">
    <div className="max-w-md w-full">
      <Card>
        <div className="text-center space-y-4 py-2">
          <p className="text-xs font-mono font-semibold uppercase tracking-wider text-ink-muted">
            404 · Route Not Found
          </p>
          <h1 className="text-xl font-bold font-display text-ink">
            This page could not be found
          </h1>
          <p className="text-sm text-ink-secondary">
            Return to the Privacy Center to inspect consent settings, proof receipts, or DSAR
            requests.
          </p>
          <Link to="/portal" className="inline-flex">
            <Button variant="primary">Go to Privacy Center</Button>
          </Link>
        </div>
      </Card>
    </div>
  </div>
);

export const AppRoutes: React.FC = () => (
  <>
    <Routes>
      {/* Auth Routes */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />

      {/* End-User Privacy Center Shell */}
      <Route element={<PortalLayout />}>
        <Route path="/" element={<PortalOverviewPage />} />
        <Route path="/portal" element={<PortalOverviewPage />} />
        <Route path="/portal/preferences" element={<PreferenceCenterPage />} />
        <Route path="/portal/history" element={<ConsentHistoryPage />} />
        <Route path="/portal/requests" element={<DataRequestsPage />} />
        <Route path="/portal/settings" element={<AccountSettingsPage />} />
      </Route>

      {/* Contract Alias Redirects (/privacy/* -> /portal/*) */}
      <Route path="/privacy" element={<Navigate to="/portal" replace />} />
      <Route
        path="/privacy/preferences"
        element={<Navigate to="/portal/preferences" replace />}
      />
      <Route
        path="/privacy/history"
        element={<Navigate to="/portal/history" replace />}
      />
      <Route
        path="/privacy/requests"
        element={<Navigate to="/portal/requests" replace />}
      />
      <Route
        path="/privacy/settings"
        element={<Navigate to="/portal/settings" replace />}
      />

      {/* Admin Governance & Experimentation Console (Guarded for ROLE_ADMIN) */}
      <Route path="/admin" element={<AdminLayout />}>
        <Route index element={<AdminAnalyticsPage />} />
        <Route path="policies" element={<AdminPoliciesPage />} />
        <Route path="regions" element={<AdminRegionsPage />} />
        <Route path="experiments" element={<AdminExperimentsPage />} />
        <Route path="requests" element={<AdminRequestsPage />} />
        <Route path="sdk-keys" element={<AdminApiKeysPage />} />
        <Route path="api-keys" element={<Navigate to="/admin/sdk-keys" replace />} />
        <Route path="audit" element={<AdminAuditPage />} />
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
    <GlobalOverlays />
  </>
);

export const App: React.FC = () => (
  <ApolloProvider client={apolloClient}>
    <ToastProvider>
      <PrivacySessionProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </PrivacySessionProvider>
    </ToastProvider>
  </ApolloProvider>
);

export default App;
