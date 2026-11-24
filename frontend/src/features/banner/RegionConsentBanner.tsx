import React from "react";
import { useQuery } from "@apollo/client";
import { useLocation } from "react-router-dom";
import { Code2 } from "lucide-react";
import type { ConsentStatusBundle, SduiScreen } from "../../types/contracts";
import { GET_MY_CONSENT_STATE, GET_SDUI_SCREEN } from "../../graphql/operations";
import { usePrivacySession } from "../../context/PrivacySessionContext";
import { SduiRenderer } from "../../sdui/SduiRenderer";

export const RegionConsentBanner: React.FC = () => {
  const location = useLocation();
  const {
    region,
    querySubjectKey,
    bannerForceOpen,
    bannerDismissedInSession,
    dismissConsentBanner,
    clearBannerDismissal,
    setInspectedScreen,
  } = usePrivacySession();

  const isAuthOrAdminRoute =
    location.pathname.startsWith("/login") ||
    location.pathname.startsWith("/signup") ||
    location.pathname.startsWith("/admin");

  const { data: stateData } = useQuery<{ myConsentState: ConsentStatusBundle }>(
    GET_MY_CONSENT_STATE,
    {
      variables: {
        subjectKey: querySubjectKey,
        region,
      },
      skip: isAuthOrAdminRoute && !bannerForceOpen,
    }
  );

  const consentState = stateData?.myConsentState;
  const requiresPrompt = Boolean(
    consentState?.needsReconsent || consentState?.shouldShowBanner
  );

  const shouldRenderBanner =
    bannerForceOpen ||
    (!isAuthOrAdminRoute &&
      requiresPrompt &&
      (!bannerDismissedInSession || consentState?.regionRule.bannerStyle === "MODAL"));

  const { data: screenData } = useQuery<{ screen: SduiScreen }>(GET_SDUI_SCREEN, {
    variables: {
      name: "CONSENT_BANNER",
      context: {
        subjectKey: querySubjectKey,
        region,
        locale: "en-US",
        deviceType:
          typeof window !== "undefined" && window.innerWidth < 768
            ? "MOBILE"
            : "DESKTOP",
      },
    },
    skip: !shouldRenderBanner,
    fetchPolicy: "network-only",
  });

  if (!shouldRenderBanner || !screenData?.screen) {
    return null;
  }

  const screen = screenData.screen;

  return (
    <div className="relative">
      <SduiRenderer
        screen={screen}
        subjectKey={querySubjectKey}
        region={region}
        onConsentSaved={() => {
          clearBannerDismissal();
        }}
        onDismissBanner={() => {
          dismissConsentBanner();
        }}
      />
      <div className="fixed bottom-3 right-4 z-[55] hidden md:block">
        <button
          type="button"
          onClick={() => setInspectedScreen(screen)}
          data-testid="banner-inspect-sdui-btn"
          className="inline-flex items-center gap-1.5 min-h-tap px-3 py-2 rounded-pill bg-inverse text-ink-inverse text-xs font-mono shadow-md hover:opacity-95 transition-opacity"
        >
          <Code2 className="h-3.5 w-3.5" aria-hidden="true" />
          <span>Inspect Banner SDUI</span>
        </button>
      </div>
    </div>
  );
};
