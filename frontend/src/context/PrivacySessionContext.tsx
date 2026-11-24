import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { useApolloClient } from "@apollo/client";
import type { AuthSessionResponse, RegionCode, SduiScreen, UserProfile } from "../types/contracts";
import { GET_ME } from "../graphql/operations";
import { ensureCsrfToken } from "../lib/csrf";
import { apiLogout } from "../lib/restClient";
import {
  getOrCreateAnonymousSubjectKey,
  getStoredRegion,
  resetAnonymousSubjectKey,
  setStoredRegion,
} from "../lib/subject";

interface PrivacySessionContextValue {
  user: UserProfile | null;
  authLoading: boolean;
  region: RegionCode;
  anonSubjectKey: string;
  /** undefined when authenticated (so backend binds strictly to user:<principal.id> per SEC-G01), anon:<id> when unauthenticated */
  querySubjectKey: string | undefined;
  bannerForceOpen: boolean;
  bannerDismissedInSession: boolean;
  inspectedScreen: SduiScreen | null;
  setRegion: (next: RegionCode) => void;
  applyAuthSession: (session: AuthSessionResponse) => Promise<void>;
  updateCurrentUser: (profile: UserProfile) => void;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  openConsentBanner: () => void;
  dismissConsentBanner: () => void;
  clearBannerDismissal: () => void;
  resetVisitorIdentity: () => void;
  setInspectedScreen: (screen: SduiScreen | null) => void;
}

const PrivacySessionContext = createContext<PrivacySessionContextValue | null>(null);

export const PrivacySessionProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const apolloClient = useApolloClient();
  const [user, setUser] = useState<UserProfile | null>(null);
  const [authLoading, setAuthLoading] = useState<boolean>(true);
  const [region, setRegionState] = useState<RegionCode>(() => getStoredRegion());
  const [anonSubjectKey, setAnonSubjectKey] = useState<string>(() =>
    getOrCreateAnonymousSubjectKey()
  );
  const [bannerForceOpen, setBannerForceOpen] = useState<boolean>(false);
  const [bannerDismissedInSession, setBannerDismissedInSession] = useState<boolean>(false);
  const [inspectedScreen, setInspectedScreen] = useState<SduiScreen | null>(null);

  useEffect(() => {
    let active = true;

    (async () => {
      try {
        await ensureCsrfToken();
        const meRes = await apolloClient
          .query<{ me: UserProfile | null }>({
            query: GET_ME,
            fetchPolicy: "network-only",
          })
          .catch(() => null);
        const meProfile = meRes?.data?.me ?? null;
        if (!active) {
          return;
        }
        setUser(meProfile);
        if (meProfile && !window.localStorage.getItem("ch_region_override")) {
          setRegionState(meProfile.region);
          setStoredRegion(meProfile.region);
        }
      } finally {
        if (active) {
          setAuthLoading(false);
        }
      }
    })();

    return () => {
      active = false;
    };
  }, [apolloClient]);

  const setRegion = useCallback((next: RegionCode) => {
    setRegionState(next);
    setStoredRegion(next);
    setBannerDismissedInSession(false);
  }, []);

  const applyAuthSession = useCallback(
    async (session: AuthSessionResponse) => {
      setUser(session.user);
      if (!window.localStorage.getItem("ch_region_override")) {
        setRegionState(session.user.region);
        setStoredRegion(session.user.region);
      }
      setBannerDismissedInSession(false);
      await apolloClient.resetStore().catch(() => undefined);
    },
    [apolloClient]
  );

  const updateCurrentUser = useCallback((profile: UserProfile) => {
    setUser(profile);
    setRegionState(profile.region);
    setStoredRegion(profile.region);
  }, []);

  const logout = useCallback(async () => {
    await apiLogout();
    setUser(null);
    setBannerForceOpen(false);
    setBannerDismissedInSession(false);
    await apolloClient.clearStore().catch(() => undefined);
  }, [apolloClient]);

  const refreshUser = useCallback(async () => {
    await ensureCsrfToken();
    const meRes = await apolloClient
      .query<{ me: UserProfile | null }>({
        query: GET_ME,
        fetchPolicy: "network-only",
      })
      .catch(() => null);
    setUser(meRes?.data?.me ?? null);
  }, [apolloClient]);

  const openConsentBanner = useCallback(() => {
    setBannerDismissedInSession(false);
    setBannerForceOpen(true);
  }, []);

  const dismissConsentBanner = useCallback(() => {
    setBannerForceOpen(false);
    setBannerDismissedInSession(true);
  }, []);

  const clearBannerDismissal = useCallback(() => {
    setBannerForceOpen(false);
    setBannerDismissedInSession(false);
  }, []);

  const resetVisitorIdentity = useCallback(() => {
    const fresh = resetAnonymousSubjectKey();
    setAnonSubjectKey(fresh);
    setBannerDismissedInSession(false);
    setBannerForceOpen(true);
    void apolloClient.resetStore().catch(() => undefined);
  }, [apolloClient]);

  const querySubjectKey = user ? undefined : anonSubjectKey;

  const value = useMemo<PrivacySessionContextValue>(
    () => ({
      user,
      authLoading,
      region,
      anonSubjectKey,
      querySubjectKey,
      bannerForceOpen,
      bannerDismissedInSession,
      inspectedScreen,
      setRegion,
      applyAuthSession,
      updateCurrentUser,
      logout,
      refreshUser,
      openConsentBanner,
      dismissConsentBanner,
      clearBannerDismissal,
      resetVisitorIdentity,
      setInspectedScreen,
    }),
    [
      user,
      authLoading,
      region,
      anonSubjectKey,
      querySubjectKey,
      bannerForceOpen,
      bannerDismissedInSession,
      inspectedScreen,
      setRegion,
      applyAuthSession,
      updateCurrentUser,
      logout,
      refreshUser,
      openConsentBanner,
      dismissConsentBanner,
      clearBannerDismissal,
      resetVisitorIdentity,
    ]
  );

  return (
    <PrivacySessionContext.Provider value={value}>
      {children}
    </PrivacySessionContext.Provider>
  );
};

export function usePrivacySession(): PrivacySessionContextValue {
  const ctx = useContext(PrivacySessionContext);
  if (!ctx) {
    throw new Error("usePrivacySession must be used inside PrivacySessionProvider");
  }
  return ctx;
}
