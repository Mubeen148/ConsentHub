// =============================================================================
// ConsentHub Client Security Guardrails (SEC-G04)
// - Strict URL scheme validation for SDUI NAVIGATE / OPEN_PREFERENCES actions
// - Safe React-only Markdown block parser (zero raw HTML / zero dangerouslySetInnerHTML)
// =============================================================================

const SAFE_NAVIGATION_PATTERN = /^(\/(?![\/\\])[^\s\\]*|https:\/\/[^\s\\]+)$/;

/**
 * Validates that a navigation target is either a single-slash app-relative route
 * (e.g. "/portal/preferences") or an explicit "https://" URL.
 * Blocks javascript:, data:, vbscript:, file:, http://, and // protocol-relative URLs.
 */
export function isSafeNavigationUrl(url: unknown): url is string {
  if (typeof url !== "string") {
    return false;
  }
  const trimmed = url.trim();
  if (trimmed.length === 0 || trimmed.length > 2048) {
    return false;
  }
  return SAFE_NAVIGATION_PATTERN.test(trimmed);
}

/**
 * Normalizes legacy/contract `/privacy/*` routes to canonical `/portal/*` SPA routes
 * while preserving all other safe internal routes.
 */
export function normalizeAppRoute(path: string): string {
  const trimmed = path.trim();
  if (trimmed === "/privacy" || trimmed === "/privacy/") {
    return "/portal";
  }
  if (trimmed.startsWith("/privacy/")) {
    return `/portal/${trimmed.slice("/privacy/".length)}`;
  }
  return trimmed;
}

export interface SafeMarkdownBlock {
  kind: "h1" | "h2" | "h3" | "bullet" | "paragraph";
  text: string;
}

/**
 * Parses markdown text into safe structured blocks rendered strictly via React text nodes.
 */
export function parseSafeMarkdown(markdown: string | null | undefined): SafeMarkdownBlock[] {
  if (!markdown) {
    return [];
  }
  const lines = markdown.split(/\r?\n/);
  const blocks: SafeMarkdownBlock[] = [];

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line) {
      continue;
    }
    if (line.startsWith("### ")) {
      blocks.push({ kind: "h3", text: line.slice(4).trim() });
    } else if (line.startsWith("## ")) {
      blocks.push({ kind: "h2", text: line.slice(3).trim() });
    } else if (line.startsWith("# ")) {
      blocks.push({ kind: "h1", text: line.slice(2).trim() });
    } else if (line.startsWith("- ") || line.startsWith("* ")) {
      blocks.push({ kind: "bullet", text: line.slice(2).trim() });
    } else {
      blocks.push({ kind: "paragraph", text: line });
    }
  }

  return blocks;
}
