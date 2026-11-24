import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    screens: {
      xs: "375px",
      md: "768px",
      lg: "1024px",
      xl: "1280px",
    },
    extend: {
      fontFamily: {
        display: ["var(--ch-font-display)"],
        sans: ["var(--ch-font-sans)"],
        mono: ["var(--ch-font-mono)"],
      },
      fontSize: {
        xs: ["var(--ch-text-xs)", { lineHeight: "1.4", letterSpacing: "0.01em" }],
        sm: ["var(--ch-text-sm)", { lineHeight: "1.5" }],
        base: ["var(--ch-text-base)", { lineHeight: "1.5" }],
        lg: ["var(--ch-text-lg)", { lineHeight: "1.25", letterSpacing: "-0.015em" }],
        xl: ["var(--ch-text-xl)", { lineHeight: "1.25", letterSpacing: "-0.02em" }],
        "2xl": ["var(--ch-text-2xl)", { lineHeight: "1.2", letterSpacing: "-0.025em" }],
      },
      colors: {
        canvas: "var(--ch-bg-canvas)",
        surface: "var(--ch-bg-surface)",
        subtle: "var(--ch-bg-subtle)",
        muted: "var(--ch-bg-muted)",
        inverse: "var(--ch-bg-inverse)",
        border: {
          subtle: "var(--ch-border-subtle)",
          DEFAULT: "var(--ch-border-default)",
          control: "var(--ch-border-control)",
          strong: "var(--ch-border-strong)",
        },
        ink: {
          DEFAULT: "var(--ch-text-primary)",
          secondary: "var(--ch-text-secondary)",
          muted: "var(--ch-text-muted)",
          inverse: "var(--ch-text-on-primary)",
        },
        action: {
          DEFAULT: "var(--ch-action-primary)",
          hover: "var(--ch-action-primary-hover)",
          active: "var(--ch-action-primary-active)",
        },
        brand: {
          DEFAULT: "var(--ch-accent-brand)",
          hover: "var(--ch-accent-brand-hover)",
          subtle: "var(--ch-accent-brand-subtle)",
        },
        status: {
          "success-bg": "var(--ch-status-success-bg)",
          "success-border": "var(--ch-status-success-border)",
          "success-text": "var(--ch-status-success-text)",
          "success-solid": "var(--ch-status-success-solid)",
          "warning-bg": "var(--ch-status-warning-bg)",
          "warning-border": "var(--ch-status-warning-border)",
          "warning-text": "var(--ch-status-warning-text)",
          "warning-solid": "var(--ch-status-warning-solid)",
          "danger-bg": "var(--ch-status-danger-bg)",
          "danger-border": "var(--ch-status-danger-border)",
          "danger-text": "var(--ch-status-danger-text)",
          "danger-solid": "var(--ch-status-danger-solid)",
          "danger-hover": "var(--ch-status-danger-hover)",
        },
        badge: {
          "gdpr-bg": "var(--ch-badge-gdpr-bg)",
          "gdpr-border": "var(--ch-badge-gdpr-border)",
          "gdpr-text": "var(--ch-badge-gdpr-text)",
          "ccpa-bg": "var(--ch-badge-ccpa-bg)",
          "ccpa-border": "var(--ch-badge-ccpa-border)",
          "ccpa-text": "var(--ch-badge-ccpa-text)",
          "default-bg": "var(--ch-badge-default-bg)",
          "default-border": "var(--ch-badge-default-border)",
          "default-text": "var(--ch-badge-default-text)",
          "essential-bg": "var(--ch-badge-essential-bg)",
          "essential-border": "var(--ch-badge-essential-border)",
          "essential-text": "var(--ch-badge-essential-text)",
        },
      },
      borderRadius: {
        control: "var(--ch-radius-control)",
        card: "var(--ch-radius-card)",
        modal: "var(--ch-radius-modal)",
        pill: "var(--ch-radius-pill)",
      },
      boxShadow: {
        sm: "var(--ch-shadow-sm)",
        md: "var(--ch-shadow-md)",
        lg: "var(--ch-shadow-lg)",
        banner: "var(--ch-shadow-banner)",
      },
      minHeight: {
        tap: "var(--ch-tap-target)",
      },
      minWidth: {
        tap: "var(--ch-tap-target)",
      },
      maxWidth: {
        portal: "var(--ch-portal-max-w)",
        admin: "var(--ch-admin-max-w)",
      },
      spacing: {
        sidebar: "var(--ch-sidebar-width)",
        topbar: "var(--ch-topbar-height)",
      },
    },
  },
  plugins: [],
} satisfies Config;
