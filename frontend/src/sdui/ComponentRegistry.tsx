import React, { useState } from "react";
import {
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Info,
  X,
} from "lucide-react";
import type { SduiAction, SduiNode } from "../types/contracts";
import { Badge, type BadgeVariant } from "../components/ui/Badge";
import { Button, type ButtonVariant } from "../components/ui/Button";
import { Card, type CardPadding, type CardTone } from "../components/ui/Card";
import { ConsentToggle } from "../components/ui/ConsentToggle";
import { SafeMarkdown } from "../components/ui/FeedbackStates";
import { formatShortDate } from "../lib/format";
import { UnknownComponentFallback } from "./UnknownComponentFallback";

export interface SduiRenderContext {
  purposeChoices: Record<string, boolean>;
  busyActionType: string | null;
  inlinePreview: boolean;
  onDispatchAction: (action: SduiAction, node: SduiNode, extra?: Record<string, unknown>) => void;
  onDismissBanner?: () => void;
  renderChildren: (children: SduiNode[]) => React.ReactNode;
}

export type SduiComponentRenderer = React.FC<{
  node: SduiNode;
  ctx: SduiRenderContext;
}>;

function asString(value: unknown, fallback = ""): string {
  return typeof value === "string" ? value : fallback;
}

function asBoolean(value: unknown, fallback = false): boolean {
  return typeof value === "boolean" ? value : fallback;
}

const ContainerComponent: SduiComponentRenderer = ({ node, ctx }) => {
  const maxWidth = asString(node.props.maxWidth, "full");
  const padding = asString(node.props.padding, "none");

  const maxWClass =
    maxWidth === "portal"
      ? "max-w-portal mx-auto"
      : maxWidth === "admin"
        ? "max-w-admin mx-auto"
        : maxWidth === "narrow"
          ? "max-w-3xl mx-auto"
          : "w-full";

  const padClass =
    padding === "sm"
      ? "p-3"
      : padding === "md"
        ? "p-4 md:p-6"
        : padding === "lg"
          ? "p-6 md:p-8"
          : "p-0";

  return (
    <div data-sdui-id={node.id} className={`${maxWClass} ${padClass}`}>
      {ctx.renderChildren(node.children ?? [])}
    </div>
  );
};

const CardComponent: SduiComponentRenderer = ({ node, ctx }) => {
  const paddingRaw = asString(node.props.padding, "default");
  const padding: CardPadding =
    paddingRaw === "compact" || paddingRaw === "flush" ? paddingRaw : "default";
  const tone: CardTone = node.props.tone === "subtle" ? "subtle" : "surface";

  return (
    <div data-sdui-id={node.id}>
      <Card padding={padding} tone={tone}>
        {ctx.renderChildren(node.children ?? [])}
      </Card>
    </div>
  );
};

const StackComponent: SduiComponentRenderer = ({ node, ctx }) => {
  const direction = asString(node.props.direction, "vertical");
  const gap = asString(node.props.gap, "md");
  const align = asString(node.props.align, "stretch");
  const wrap = asBoolean(node.props.wrap, false);

  const dirClass = direction === "horizontal" ? "flex-row" : "flex-col";
  const gapClass =
    gap === "xs"
      ? "gap-1.5"
      : gap === "sm"
        ? "gap-2.5"
        : gap === "lg"
          ? "gap-6"
          : gap === "xl"
            ? "gap-8"
            : "gap-4";

  const alignClass =
    direction === "horizontal"
      ? align === "between"
        ? "items-center justify-between"
        : align === "center"
          ? "items-center"
          : align === "end"
            ? "items-end justify-end"
            : align === "start"
              ? "items-start"
              : "items-stretch"
      : align === "center"
        ? "items-center"
        : align === "end"
          ? "items-end"
          : align === "start"
            ? "items-start"
            : "items-stretch";

  return (
    <div
      data-sdui-id={node.id}
      className={[
        "flex",
        dirClass,
        gapClass,
        alignClass,
        wrap ? "flex-wrap" : "",
      ]
        .filter(Boolean)
        .join(" ")}
    >
      {ctx.renderChildren(node.children ?? [])}
    </div>
  );
};

const BannerShellComponent: SduiComponentRenderer = ({ node, ctx }) => {
  const bannerStyle = asString(node.props.bannerStyle, "BANNER");
  const ariaLabel = asString(node.props.ariaLabel, "Privacy & Consent Choices");
  const dismissible = asBoolean(node.props.dismissible, bannerStyle !== "MODAL");

  const dismissAction = (node.actions ?? []).find((a) => a.type === "DISMISS");

  const handleDismiss = () => {
    if (!dismissible) {
      return;
    }
    if (dismissAction) {
      ctx.onDispatchAction(dismissAction, node);
    } else if (ctx.onDismissBanner) {
      ctx.onDismissBanner();
    }
  };

  if (ctx.inlinePreview) {
    return (
      <section
        data-sdui-id={node.id}
        data-banner-style={bannerStyle}
        aria-label={ariaLabel}
        className="relative bg-surface border border-border-strong rounded-card p-5 md:p-6 shadow-sm"
      >
        {dismissible && ctx.onDismissBanner ? (
          <button
            type="button"
            onClick={handleDismiss}
            aria-label="Dismiss notice"
            className="absolute top-3 right-3 inline-flex items-center justify-center min-w-tap min-h-tap rounded-control text-ink-muted hover:text-ink hover:bg-subtle"
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        ) : null}
        {ctx.renderChildren(node.children ?? [])}
      </section>
    );
  }

  if (bannerStyle === "MODAL") {
    return (
      <div
        className="fixed inset-0 z-50 flex items-end md:items-center justify-center bg-[rgba(20,21,25,0.52)] backdrop-blur-[4px] md:p-6"
        data-testid="consent-banner-overlay"
      >
        <section
          role="dialog"
          aria-modal="true"
          aria-label={ariaLabel}
          data-sdui-id={node.id}
          data-banner-style="MODAL"
          className="relative w-full md:max-w-[680px] max-h-[90dvh] overflow-y-auto bg-surface border border-border-strong rounded-t-modal md:rounded-modal p-5 md:p-6 shadow-lg"
        >
          {dismissible ? (
            <button
              type="button"
              onClick={handleDismiss}
              aria-label="Dismiss consent dialog"
              className="absolute top-3 right-3 inline-flex items-center justify-center min-w-tap min-h-tap rounded-control text-ink-muted hover:text-ink hover:bg-subtle"
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          ) : null}
          {ctx.renderChildren(node.children ?? [])}
        </section>
      </div>
    );
  }

  const widthClass =
    bannerStyle === "COMPACT"
      ? "md:w-[min(760px,calc(100vw-48px))]"
      : "md:w-[min(960px,calc(100vw-48px))]";

  return (
    <section
      role="region"
      aria-label={ariaLabel}
      data-testid="consent-banner-overlay"
      data-sdui-id={node.id}
      data-banner-style={bannerStyle}
      className={[
        "fixed z-50 bottom-0 left-0 right-0 md:bottom-6 md:left-1/2 md:right-auto md:-translate-x-1/2",
        widthClass,
        "max-h-[88dvh] overflow-y-auto bg-surface border-t md:border border-border-strong",
        "rounded-t-modal md:rounded-card px-4 py-5 md:p-6 shadow-banner",
      ].join(" ")}
    >
      {dismissible ? (
        <button
          type="button"
          onClick={handleDismiss}
          aria-label="Dismiss privacy notice"
          className="absolute top-2.5 right-2.5 inline-flex items-center justify-center min-w-tap min-h-tap rounded-control text-ink-muted hover:text-ink hover:bg-subtle"
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      ) : null}
      {ctx.renderChildren(node.children ?? [])}
    </section>
  );
};

const HeaderComponent: SduiComponentRenderer = ({ node }) => {
  const title = asString(node.props.title, "");
  const subtitle = asString(node.props.subtitle, "");
  const eyebrow = asString(node.props.eyebrow, "");
  const level = typeof node.props.level === "number" ? node.props.level : 2;

  return (
    <div data-sdui-id={node.id} className="space-y-1.5">
      {eyebrow ? (
        <p className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
          {eyebrow}
        </p>
      ) : null}
      {level === 1 ? (
        <h1 className="text-xl md:text-2xl font-bold text-ink font-display">{title}</h1>
      ) : level === 3 ? (
        <h3 className="text-base font-semibold text-ink font-display">{title}</h3>
      ) : (
        <h2 className="text-lg font-semibold text-ink font-display">{title}</h2>
      )}
      {subtitle ? <p className="text-sm text-ink-secondary leading-relaxed">{subtitle}</p> : null}
    </div>
  );
};

const TextComponent: SduiComponentRenderer = ({ node }) => {
  const content = asString(node.props.content, "");
  const variant = asString(node.props.variant, "body-sm");
  const tone = asString(node.props.tone, "primary");
  const weight = asString(node.props.weight, "regular");

  const variantClass =
    variant === "body-base"
      ? "text-base"
      : variant === "caption-xs"
        ? "text-xs"
        : variant === "mono-xs"
          ? "text-xs font-mono"
          : "text-sm";

  const toneClass =
    tone === "secondary"
      ? "text-ink-secondary"
      : tone === "muted"
        ? "text-ink-muted"
        : "text-ink";

  const weightClass =
    weight === "semibold"
      ? "font-semibold"
      : weight === "medium"
        ? "font-medium"
        : "font-normal";

  return (
    <p
      data-sdui-id={node.id}
      className={`${variantClass} ${toneClass} ${weightClass}`}
    >
      {content}
    </p>
  );
};

const VALID_BADGE_VARIANTS = new Set<BadgeVariant>([
  "GDPR",
  "CCPA",
  "DEFAULT",
  "ESSENTIAL",
  "SUCCESS",
  "WARNING",
  "DANGER",
]);

const BadgeComponent: SduiComponentRenderer = ({ node }) => {
  const label = asString(node.props.label, "");
  const rawVariant = asString(node.props.variant, "DEFAULT") as BadgeVariant;
  const variant: BadgeVariant = VALID_BADGE_VARIANTS.has(rawVariant) ? rawVariant : "DEFAULT";

  return (
    <span data-sdui-id={node.id} className="inline-flex">
      <Badge label={label} variant={variant} />
    </span>
  );
};

const DividerComponent: SduiComponentRenderer = ({ node }) => {
  const spacing = asString(node.props.spacing, "md");
  const spaceClass =
    spacing === "sm" ? "my-2" : spacing === "lg" ? "my-6" : "my-4";

  return (
    <hr
      data-sdui-id={node.id}
      className={`border-0 border-t border-border-subtle ${spaceClass}`}
    />
  );
};

const PurposeToggleRowComponent: SduiComponentRenderer = ({ node, ctx }) => {
  const purposeSlug = asString(node.props.purposeSlug, "");
  const title = asString(node.props.title, purposeSlug);
  const description = asString(node.props.description, "");
  const legalBasisLabel = asString(node.props.legalBasisLabel, "");
  const required = asBoolean(node.props.required, false);
  const badgeLabel = asString(node.props.badgeLabel, "");
  const defaultChecked = asBoolean(node.props.checked, false);

  const currentChecked = required
    ? true
    : purposeSlug in ctx.purposeChoices
      ? Boolean(ctx.purposeChoices[purposeSlug])
      : defaultChecked;

  const handleToggle = (nextChecked: boolean) => {
    const toggleAction = (node.actions ?? []).find((a) => a.trigger === "onToggle") ?? {
      trigger: "onToggle",
      type: "TOGGLE_PURPOSE",
      payload: { purposeSlug },
    };
    ctx.onDispatchAction(toggleAction, node, {
      purposeSlug,
      granted: nextChecked,
    });
  };

  return (
    <div
      data-sdui-id={node.id}
      className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 py-4 px-4 md:px-5 rounded-card border border-border-subtle bg-surface"
    >
      <div className="space-y-1 min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-base font-semibold text-ink">{title}</span>
          {legalBasisLabel ? (
            <Badge
              label={legalBasisLabel}
              variant={required ? "ESSENTIAL" : "DEFAULT"}
              showDot={false}
            />
          ) : null}
        </div>
        {description ? (
          <p className="text-sm text-ink-secondary leading-relaxed">{description}</p>
        ) : null}
      </div>
      <ConsentToggle
        label={title}
        checked={currentChecked}
        required={required}
        badgeLabel={badgeLabel || undefined}
        onChange={handleToggle}
        testId={`sdui-toggle-${purposeSlug}`}
      />
    </div>
  );
};

const PolicySummaryAccordionComponent: SduiComponentRenderer = ({ node }) => {
  const versionLabel = asString(node.props.versionLabel, "v1.0");
  const title = asString(node.props.title, "Privacy Policy Summary");
  const summary = asString(node.props.summary, "");
  const contentMarkdown = asString(node.props.contentMarkdown, "");
  const publishedAt = asString(node.props.publishedAt, "");
  const defaultExpanded = asBoolean(node.props.defaultExpanded, false);

  const [expanded, setExpanded] = useState<boolean>(defaultExpanded);

  return (
    <div
      data-sdui-id={node.id}
      className="rounded-control border border-border-subtle bg-subtle/60 overflow-hidden"
    >
      <button
        type="button"
        aria-expanded={expanded}
        onClick={() => setExpanded((prev) => !prev)}
        className="w-full min-h-tap px-4 py-2.5 flex items-center justify-between gap-3 text-left hover:bg-subtle transition-colors"
      >
        <div className="flex flex-wrap items-center gap-2 min-w-0">
          <span className="text-xs font-mono font-semibold px-2 py-0.5 rounded bg-surface border border-border text-ink">
            {versionLabel}
          </span>
          <span className="text-sm font-medium text-ink truncate">{title}</span>
        </div>
        <span className="inline-flex items-center gap-1 text-xs font-medium text-ink-secondary shrink-0">
          <span>{expanded ? "Hide details" : "Policy details"}</span>
          {expanded ? (
            <ChevronUp className="h-4 w-4" aria-hidden="true" />
          ) : (
            <ChevronDown className="h-4 w-4" aria-hidden="true" />
          )}
        </span>
      </button>

      {expanded ? (
        <div className="px-4 py-3.5 border-t border-border-subtle bg-surface space-y-2.5">
          {summary ? <p className="text-sm text-ink-secondary">{summary}</p> : null}
          {contentMarkdown ? <SafeMarkdown content={contentMarkdown} /> : null}
          {publishedAt ? (
            <p className="text-xs text-ink-muted font-mono">
              Published {formatShortDate(publishedAt)}
            </p>
          ) : null}
        </div>
      ) : null}
    </div>
  );
};

const ActionBarComponent: SduiComponentRenderer = ({ node, ctx }) => {
  const layout = asString(node.props.layout, "symmetric-inline");
  const sticky = asBoolean(node.props.sticky, false);

  const layoutClass =
    layout === "stacked"
      ? "flex flex-col gap-2 w-full"
      : layout === "space-between"
        ? "flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2.5"
        : "flex flex-col md:flex-row md:items-center md:flex-wrap gap-2.5 [&>div]:w-full md:[&>div]:w-auto [&>button]:w-full md:[&>button]:w-auto";

  return (
    <div
      data-sdui-id={node.id}
      className={[
        layoutClass,
        sticky
          ? "sticky bottom-0 bg-surface/95 backdrop-blur py-3 border-t border-border-subtle"
          : "pt-1",
      ]
        .filter(Boolean)
        .join(" ")}
    >
      {ctx.renderChildren(node.children ?? [])}
    </div>
  );
};

const VALID_BUTTON_VARIANTS = new Set<ButtonVariant>([
  "primary",
  "secondary",
  "ghost",
  "destructive",
]);

const ButtonComponent: SduiComponentRenderer = ({ node, ctx }) => {
  const label = asString(node.props.label, "Continue");
  const rawVariant = asString(node.props.variant, "primary") as ButtonVariant;
  const variant: ButtonVariant = VALID_BUTTON_VARIANTS.has(rawVariant)
    ? rawVariant
    : "primary";
  const fullWidth = asBoolean(node.props.fullWidth, false);
  const disabled = asBoolean(node.props.disabled, false);
  const testId = asString(node.props.testId, "") || undefined;

  const clickActions = (node.actions ?? []).filter((a) => a.trigger === "onClick");
  const primaryActionType = clickActions[0]?.type ?? null;
  const isBusy = Boolean(primaryActionType && ctx.busyActionType === primaryActionType);

  const handleClick = () => {
    for (const action of clickActions) {
      ctx.onDispatchAction(action, node);
    }
  };

  return (
    <Button
      data-sdui-id={node.id}
      variant={variant}
      fullWidth={fullWidth}
      disabled={disabled || (ctx.busyActionType !== null && !isBusy)}
      loading={isBusy}
      testId={testId}
      onClick={handleClick}
    >
      {label}
    </Button>
  );
};

const NoticeCalloutComponent: SduiComponentRenderer = ({ node }) => {
  const title = asString(node.props.title, "");
  const message = asString(node.props.message, "");
  const tone = asString(node.props.tone, "info");

  const styleMap: Record<
    string,
    { box: string; title: string; icon: React.ReactNode }
  > = {
    success: {
      box: "bg-status-success-bg border-status-success-border",
      title: "text-status-success-text",
      icon: (
        <CheckCircle2
          className="h-5 w-5 text-status-success-text shrink-0 mt-0.5"
          aria-hidden="true"
        />
      ),
    },
    warning: {
      box: "bg-status-warning-bg border-status-warning-border",
      title: "text-status-warning-text",
      icon: (
        <AlertTriangle
          className="h-5 w-5 text-status-warning-text shrink-0 mt-0.5"
          aria-hidden="true"
        />
      ),
    },
    danger: {
      box: "bg-status-danger-bg border-status-danger-border",
      title: "text-status-danger-text",
      icon: (
        <AlertCircle
          className="h-5 w-5 text-status-danger-text shrink-0 mt-0.5"
          aria-hidden="true"
        />
      ),
    },
    info: {
      box: "bg-brand-subtle border-badge-gdpr-border",
      title: "text-brand-hover",
      icon: (
        <Info className="h-5 w-5 text-brand-hover shrink-0 mt-0.5" aria-hidden="true" />
      ),
    },
  };

  const selected = styleMap[tone] ?? styleMap.info!;

  return (
    <div
      data-sdui-id={node.id}
      role={tone === "warning" || tone === "danger" ? "alert" : "status"}
      className={`rounded-card border px-4 py-3.5 flex items-start gap-3 ${selected.box}`}
    >
      {selected.icon}
      <div className="min-w-0">
        <p className={`text-sm font-semibold ${selected.title}`}>{title}</p>
        <p className="mt-0.5 text-sm text-ink">{message}</p>
      </div>
    </div>
  );
};

const StatGridComponent: SduiComponentRenderer = ({ node, ctx }) => {
  const columns = typeof node.props.columns === "number" ? node.props.columns : 3;
  const gridCols =
    columns === 2
      ? "grid-cols-1 md:grid-cols-2"
      : columns === 4
        ? "grid-cols-1 md:grid-cols-2 lg:grid-cols-4"
        : "grid-cols-1 md:grid-cols-3";

  return (
    <div data-sdui-id={node.id} className={`grid ${gridCols} gap-4`}>
      {ctx.renderChildren(node.children ?? [])}
    </div>
  );
};

const StatItemComponent: SduiComponentRenderer = ({ node }) => {
  const label = asString(node.props.label, "");
  const value = asString(node.props.value, "—");
  const sublabel = asString(node.props.sublabel, "");
  const tone = asString(node.props.tone, "default");

  const valueTone =
    tone === "success"
      ? "text-status-success-text"
      : tone === "warning"
        ? "text-status-warning-text"
        : tone === "brand"
          ? "text-brand"
          : "text-ink";

  return (
    <div
      data-sdui-id={node.id}
      className="bg-surface border border-border rounded-card p-5 flex flex-col justify-between gap-1.5"
    >
      <span className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
        {label}
      </span>
      <span className={`text-xl font-bold font-display tabular-nums ${valueTone}`}>
        {value}
      </span>
      {sublabel ? <span className="text-xs text-ink-secondary">{sublabel}</span> : null}
    </div>
  );
};

export const SDUI_COMPONENT_REGISTRY: Record<string, SduiComponentRenderer> = {
  Container: ContainerComponent,
  Card: CardComponent,
  Stack: StackComponent,
  BannerShell: BannerShellComponent,
  Header: HeaderComponent,
  Text: TextComponent,
  Badge: BadgeComponent,
  Divider: DividerComponent,
  PurposeToggleRow: PurposeToggleRowComponent,
  PolicySummaryAccordion: PolicySummaryAccordionComponent,
  ActionBar: ActionBarComponent,
  Button: ButtonComponent,
  NoticeCallout: NoticeCalloutComponent,
  StatGrid: StatGridComponent,
  StatItem: StatItemComponent,
};

export function resolveSduiComponent(type: string): SduiComponentRenderer | null {
  if (!type || !Object.prototype.hasOwnProperty.call(SDUI_COMPONENT_REGISTRY, type)) {
    return null;
  }
  return SDUI_COMPONENT_REGISTRY[type] ?? null;
}

export { UnknownComponentFallback };
