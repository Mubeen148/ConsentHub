import React from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { MockedProvider } from "@apollo/client/testing";
import { ToastProvider } from "../components/ui/ToastProvider";
import { ConsentToggle } from "../components/ui/ConsentToggle";
import { SduiRenderer } from "../sdui/SduiRenderer";
import { isSafeNavigationUrl, normalizeAppRoute, parseSafeMarkdown } from "../lib/security";
import { EmptyState, ErrorState, KpiGridSkeleton } from "../components/ui/FeedbackStates";
import type { SduiScreen } from "../types/contracts";

function renderWithProviders(ui: React.ReactElement) {
  return render(
    <MockedProvider mocks={[]}>
      <ToastProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </ToastProvider>
    </MockedProvider>
  );
}

describe("ConsentHub Client Security & URL Guardrails (SEC-G04)", () => {
  it("blocks javascript:, data:, http://, backslash /\\, and protocol-relative // URIs while permitting safe internal & https URLs", () => {
    expect(isSafeNavigationUrl("javascript:alert(document.cookie)")).toBe(false);
    expect(isSafeNavigationUrl("data:text/html,<script>alert(1)</script>")).toBe(false);
    expect(isSafeNavigationUrl("//evil.example/redirect")).toBe(false);
    expect(isSafeNavigationUrl("/\\evil.example/redirect")).toBe(false);
    expect(isSafeNavigationUrl("http://insecure.example")).toBe(false);
    expect(isSafeNavigationUrl("/portal/preferences")).toBe(true);
    expect(isSafeNavigationUrl("/privacy/history")).toBe(true);
    expect(isSafeNavigationUrl("https://consenthub.io/docs")).toBe(true);
  });

  it("normalizes /privacy/* contract aliases to /portal/* SPA routes and parses markdown without HTML injection", () => {
    expect(normalizeAppRoute("/privacy")).toBe("/portal");
    expect(normalizeAppRoute("/privacy/preferences")).toBe("/portal/preferences");
    expect(normalizeAppRoute("/portal/requests")).toBe("/portal/requests");

    const blocks = parseSafeMarkdown("# Policy v2.0\n## Minimization\n- Salted IP hashes\nPlain body");
    expect(blocks).toHaveLength(4);
    expect(blocks[0]).toEqual({ kind: "h1", text: "Policy v2.0" });
    expect(blocks[2]).toEqual({ kind: "bullet", text: "Salted IP hashes" });
  });
});

describe("ConsentToggle & Feedback UI States", () => {
  it("locks Essential toggles to Always Active and prevents disabling", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    render(
      <ConsentToggle
        label="Essential Security"
        checked={true}
        required={true}
        onChange={onChange}
        testId="toggle-essential"
      />
    );

    expect(screen.getByText("Always Active")).toBeInTheDocument();
    const switchEl = screen.getByTestId("toggle-essential");
    expect(switchEl).toHaveAttribute("aria-checked", "true");
    await user.click(switchEl);
    expect(onChange).not.toHaveBeenCalled();
  });

  it("toggles optional consent switches cleanly when clicked", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    render(
      <ConsentToggle
        label="Product Analytics"
        checked={false}
        required={false}
        onChange={onChange}
        testId="toggle-analytics"
      />
    );

    await user.click(screen.getByTestId("toggle-analytics"));
    expect(onChange).toHaveBeenCalledWith(true);
  });

  it("renders skeleton, empty, and retryable error states with accessible controls", async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();

    const { rerender } = render(<KpiGridSkeleton count={2} />);
    expect(document.querySelectorAll(".ch-skeleton").length).toBeGreaterThan(0);

    rerender(
      <EmptyState
        title="No Data Requests Yet"
        description="Submit an export or erasure request above."
      />
    );
    expect(screen.getByText("No Data Requests Yet")).toBeInTheDocument();

    rerender(
      <ErrorState
        title="Unable to Load Consent Ledger"
        message="Network timeout"
        onRetry={onRetry}
      />
    );
    await user.click(screen.getByRole("button", { name: /retry/i }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});

describe("Server-Driven UI (SDUI) Component Registry & Fallback Resilience", () => {
  it("renders canonical SDUI trees and gracefully falls back on unknown server component types", () => {
    const mixedScreen: SduiScreen = {
      schemaVersion: "1.0",
      screenName: "PRIVACY_OVERVIEW",
      policyVersion: "v2.0",
      needsReconsent: false,
      regionCode: "EU",
      regime: "GDPR",
      experiment: {
        experimentId: "44444444-4444-4444-8444-444444444444",
        experimentSlug: "eu-banner-clarity-q4",
        variantKey: "concise_trust",
      },
      root: {
        id: "root-container",
        type: "Container",
        props: { maxWidth: "portal" },
        actions: [],
        children: [
          {
            id: "hero-card",
            type: "Card",
            props: { padding: "default", tone: "surface" },
            actions: [],
            children: [
              {
                id: "regime-badge",
                type: "Badge",
                props: { label: "GDPR · EU Strict Opt-In", variant: "GDPR" },
                actions: [],
                children: [],
              },
              {
                id: "hero-header",
                type: "Header",
                props: {
                  title: "Server-Driven Privacy Overview",
                  subtitle: "Rendered dynamically from JSON component tree.",
                  level: 1,
                },
                actions: [],
                children: [],
              },
              {
                id: "future-widget",
                type: "FutureQuantumConsentWidgetV9",
                props: { experimental: true },
                actions: [],
                children: [],
              },
            ],
          },
        ],
      },
    };

    renderWithProviders(<SduiRenderer screen={mixedScreen} inlinePreview={true} />);

    expect(screen.getByText("GDPR · EU Strict Opt-In")).toBeInTheDocument();
    expect(screen.getByText("Server-Driven Privacy Overview")).toBeInTheDocument();
    // Unknown component must render safe fallback without crashing surrounding tree
    expect(screen.getByText(/FutureQuantumConsentWidgetV9/i)).toBeInTheDocument();
  });
});
