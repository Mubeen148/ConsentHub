import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useMutation } from "@apollo/client";
import { useNavigate } from "react-router-dom";
import type {
  ConsentStatusBundle,
  PurposeDecisionInput,
  RegionCode,
  SduiAction,
  SduiNode,
  SduiScreen,
} from "../types/contracts";
import {
  ACCEPT_ALL_CONSENT,
  GET_MY_CONSENT_HISTORY,
  GET_MY_CONSENT_STATE,
  GET_SDUI_SCREEN,
  REJECT_OPTIONAL_CONSENT,
  SAVE_CONSENT_PREFERENCES,
} from "../graphql/operations";
import { isSafeNavigationUrl, normalizeAppRoute } from "../lib/security";
import { useToast } from "../components/ui/ToastProvider";
import {
  resolveSduiComponent,
  type SduiRenderContext,
  UnknownComponentFallback,
} from "./ComponentRegistry";

interface ExtractedPurposeMeta {
  slug: string;
  checked: boolean;
  required: boolean;
}

function collectPurposeNodes(node: SduiNode | null | undefined): ExtractedPurposeMeta[] {
  if (!node) {
    return [];
  }
  const results: ExtractedPurposeMeta[] = [];
  if (node.type === "PurposeToggleRow") {
    const slug = typeof node.props.purposeSlug === "string" ? node.props.purposeSlug : "";
    const required = Boolean(node.props.required);
    const checked = required ? true : Boolean(node.props.checked);
    if (slug) {
      results.push({ slug, checked, required });
    }
  }
  for (const child of node.children ?? []) {
    results.push(...collectPurposeNodes(child));
  }
  return results;
}

export interface SduiRendererProps {
  screen: SduiScreen;
  subjectKey?: string;
  region?: RegionCode;
  inlinePreview?: boolean;
  onConsentSaved?: (bundle: ConsentStatusBundle, actionType: string) => void;
  onDismissBanner?: () => void;
  onOpenPreferences?: () => void;
}

export const SduiRenderer: React.FC<SduiRendererProps> = ({
  screen,
  subjectKey,
  region,
  inlinePreview = false,
  onConsentSaved,
  onDismissBanner,
  onOpenPreferences,
}) => {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [busyActionType, setBusyActionType] = useState<string | null>(null);

  const initialPurposes = useMemo(() => collectPurposeNodes(screen.root), [screen.root]);

  const [purposeChoices, setPurposeChoices] = useState<Record<string, boolean>>(() => {
    const map: Record<string, boolean> = {};
    for (const item of initialPurposes) {
      map[item.slug] = item.required ? true : item.checked;
    }
    return map;
  });

  useEffect(() => {
    const map: Record<string, boolean> = {};
    for (const item of initialPurposes) {
      map[item.slug] = item.required ? true : item.checked;
    }
    setPurposeChoices(map);
  }, [initialPurposes]);

  const refetchQueries = useMemo(
    () => [
      {
        query: GET_MY_CONSENT_STATE,
        variables: { subjectKey, region: region ?? screen.regionCode },
      },
      { query: GET_MY_CONSENT_HISTORY, variables: { subjectKey, limit: 50 } },
      {
        query: GET_SDUI_SCREEN,
        variables: {
          name: "CONSENT_BANNER",
          context: { subjectKey, region: region ?? screen.regionCode },
        },
      },
      {
        query: GET_SDUI_SCREEN,
        variables: {
          name: "PRIVACY_OVERVIEW",
          context: { subjectKey, region: region ?? screen.regionCode },
        },
      },
      {
        query: GET_SDUI_SCREEN,
        variables: {
          name: "PREFERENCE_CENTER",
          context: { subjectKey, region: region ?? screen.regionCode },
        },
      },
    ],
    [subjectKey, region, screen.regionCode]
  );

  const [acceptAllMutation] = useMutation<{ acceptAllConsent: ConsentStatusBundle }>(
    ACCEPT_ALL_CONSENT,
    { refetchQueries }
  );
  const [rejectOptionalMutation] = useMutation<{
    rejectOptionalConsent: ConsentStatusBundle;
  }>(REJECT_OPTIONAL_CONSENT, { refetchQueries });
  const [savePreferencesMutation] = useMutation<{
    saveConsentPreferences: ConsentStatusBundle;
  }>(SAVE_CONSENT_PREFERENCES, { refetchQueries });

  const handleDispatchAction = useCallback(
    async (
      action: SduiAction,
      _node: SduiNode,
      extra?: Record<string, unknown>
    ): Promise<void> => {
      const payload = action.payload ?? {};
      const actionType = action.type;

      if (actionType === "TOGGLE_PURPOSE") {
        const slug =
          typeof extra?.purposeSlug === "string"
            ? extra.purposeSlug
            : typeof payload.purposeSlug === "string"
              ? payload.purposeSlug
              : "";
        if (!slug) {
          return;
        }
        const meta = initialPurposes.find((p) => p.slug === slug);
        if (meta?.required) {
          return;
        }
        setPurposeChoices((prev) => ({
          ...prev,
          [slug]:
            typeof extra?.granted === "boolean" ? extra.granted : !Boolean(prev[slug]),
        }));
        return;
      }

      if (actionType === "DISMISS") {
        onDismissBanner?.();
        return;
      }

      if (actionType === "OPEN_PREFERENCES") {
        const rawTarget =
          typeof payload.to === "string" ? payload.to : "/portal/preferences";
        if (!isSafeNavigationUrl(rawTarget)) {
          showToast({
            title: "Blocked unsafe navigation target",
            description: "Navigation target failed URL security policy.",
            variant: "warning",
          });
          return;
        }
        onOpenPreferences?.();
        onDismissBanner?.();
        if (rawTarget.startsWith("/")) {
          navigate(normalizeAppRoute(rawTarget));
        }
        return;
      }

      if (actionType === "NAVIGATE") {
        const rawTarget = payload.to ?? payload.href ?? payload.url;
        if (!isSafeNavigationUrl(rawTarget)) {
          showToast({
            title: "Blocked unsafe navigation URL",
            description: "Only internal paths (/) and https:// links are allowed.",
            variant: "warning",
          });
          return;
        }
        if (rawTarget.startsWith("/")) {
          onDismissBanner?.();
          navigate(normalizeAppRoute(rawTarget));
        } else if (rawTarget.startsWith("https://")) {
          window.open(rawTarget, "_blank", "noopener,noreferrer");
        }
        return;
      }

      if (inlinePreview) {
        showToast({
          title: `Preview Action: ${actionType}`,
          description:
            "Admin preview mode isolates variant inspection without mutating live visitor consent.",
          variant: "info",
        });
        return;
      }

      const effectiveRegion =
        (typeof payload.region === "string"
          ? (payload.region as RegionCode)
          : undefined) ??
        region ??
        screen.regionCode;

      const defaultSource =
        screen.screenName === "CONSENT_BANNER" ? "BANNER" : "PREFERENCE_CENTER";
      const source = typeof payload.source === "string" ? payload.source : defaultSource;
      const experimentId =
        (typeof payload.experimentId === "string" ? payload.experimentId : undefined) ??
        screen.experiment?.experimentId;
      const variantKey =
        (typeof payload.variantKey === "string" ? payload.variantKey : undefined) ??
        screen.experiment?.variantKey;

      try {
        setBusyActionType(actionType);

        if (actionType === "ACCEPT_ALL") {
          const { data } = await acceptAllMutation({
            variables: {
              subjectKey,
              region: effectiveRegion,
              source,
              experimentId,
              variantKey,
            },
          });
          if (data?.acceptAllConsent) {
            showToast({
              title: "All consent purposes enabled",
              description: `Recorded under Policy ${data.acceptAllConsent.activePolicyVersion.versionLabel} (${effectiveRegion}).`,
              variant: "success",
            });
            onConsentSaved?.(data.acceptAllConsent, actionType);
          }
          return;
        }

        if (actionType === "REJECT_OPTIONAL" || actionType === "REJECT_NON_ESSENTIAL") {
          const { data } = await rejectOptionalMutation({
            variables: {
              subjectKey,
              region: effectiveRegion,
              source,
              experimentId,
              variantKey,
            },
          });
          if (data?.rejectOptionalConsent) {
            showToast({
              title: "Optional processing declined",
              description:
                "Only strictly necessary security & session cookies remain active.",
              variant: "success",
            });
            onConsentSaved?.(data.rejectOptionalConsent, actionType);
          }
          return;
        }

        if (actionType === "SUBMIT_CONSENT") {
          const decisions: PurposeDecisionInput[] = initialPurposes.map((item) => ({
            purposeSlug: item.slug,
            granted: item.required ? true : Boolean(purposeChoices[item.slug]),
          }));
          const { data } = await savePreferencesMutation({
            variables: {
              input: {
                subjectKey,
                region: effectiveRegion,
                source,
                experimentId,
                variantKey,
                decisions,
              },
            },
          });
          if (data?.saveConsentPreferences) {
            showToast({
              title: "Privacy preferences saved",
              description: `Cryptographic receipt recorded for Policy ${data.saveConsentPreferences.activePolicyVersion.versionLabel}.`,
              variant: "success",
            });
            onConsentSaved?.(data.saveConsentPreferences, actionType);
          }
        }
      } catch (err) {
        const message =
          err instanceof Error ? err.message : "Failed to record consent preferences.";
        showToast({
          title: "Unable to save consent choice",
          description: message,
          variant: "danger",
        });
      } finally {
        setBusyActionType(null);
      }
    },
    [
      initialPurposes,
      onDismissBanner,
      onOpenPreferences,
      navigate,
      showToast,
      inlinePreview,
      region,
      screen.regionCode,
      screen.screenName,
      screen.experiment,
      acceptAllMutation,
      subjectKey,
      onConsentSaved,
      rejectOptionalMutation,
      purposeChoices,
      savePreferencesMutation,
    ]
  );

  const renderNode = useCallback(
    (node: SduiNode, ctx: SduiRenderContext): React.ReactNode => {
      if (!node || typeof node.type !== "string") {
        return null;
      }
      const Component = resolveSduiComponent(node.type);
      if (!Component) {
        return <UnknownComponentFallback key={node.id} node={node} />;
      }
      return <Component key={node.id} node={node} ctx={ctx} />;
    },
    []
  );

  const renderContext = useMemo<SduiRenderContext>(() => {
    const ctx: SduiRenderContext = {
      purposeChoices,
      busyActionType,
      inlinePreview,
      onDispatchAction: (action, node, extra) => {
        void handleDispatchAction(action, node, extra);
      },
      onDismissBanner,
      renderChildren: (children: SduiNode[]) =>
        (children ?? []).map((child) => renderNode(child, ctx)),
    };
    return ctx;
  }, [
    purposeChoices,
    busyActionType,
    inlinePreview,
    handleDispatchAction,
    onDismissBanner,
    renderNode,
  ]);

  if (!screen?.root) {
    return null;
  }

  return <>{renderNode(screen.root, renderContext)}</>;
};
