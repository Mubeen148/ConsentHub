import type { RegionCode } from "../types/contracts";

const ANON_SUBJECT_STORAGE_KEY = "ch_anon_id";
const REGION_STORAGE_KEY = "ch_region_override";
const VALID_ANON_PATTERN = /^anon:[a-zA-Z0-9_-]{8,112}$/;

function generateRandomId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  if (typeof crypto !== "undefined" && typeof crypto.getRandomValues === "function") {
    crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < bytes.length; i += 1) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * Returns a persistent pseudonymous subject key (`anon:<uuid>`) for unauthenticated visitors.
 * Never persists JWTs or raw user PII in browser storage (PRIV-05).
 */
export function getOrCreateAnonymousSubjectKey(): string {
  try {
    const existing = window.localStorage.getItem(ANON_SUBJECT_STORAGE_KEY);
    if (existing && VALID_ANON_PATTERN.test(existing)) {
      return existing;
    }
    const created = `anon:${generateRandomId()}`;
    window.localStorage.setItem(ANON_SUBJECT_STORAGE_KEY, created);
    return created;
  } catch {
    return `anon:${generateRandomId()}`;
  }
}

export function resetAnonymousSubjectKey(): string {
  const fresh = `anon:${generateRandomId()}`;
  try {
    window.localStorage.setItem(ANON_SUBJECT_STORAGE_KEY, fresh);
  } catch {
    // Ignore storage restriction errors in strict private modes
  }
  return fresh;
}

export function normalizeRegionCode(input: string | null | undefined): RegionCode {
  if (!input) {
    return "EU";
  }
  const upper = input.trim().toUpperCase().replace("-", "_");
  if (upper === "EU" || upper === "US_CA" || upper === "GLOBAL") {
    return upper;
  }
  return "GLOBAL";
}

export function getStoredRegion(): RegionCode {
  try {
    const urlParams = new URLSearchParams(window.location.search);
    const queryRegion = urlParams.get("region");
    if (queryRegion) {
      const normalized = normalizeRegionCode(queryRegion);
      window.localStorage.setItem(REGION_STORAGE_KEY, normalized);
      return normalized;
    }
    const saved = window.localStorage.getItem(REGION_STORAGE_KEY);
    if (saved === "EU" || saved === "US_CA" || saved === "GLOBAL") {
      return saved;
    }
  } catch {
    // Ignore storage access errors
  }
  return "EU";
}

export function setStoredRegion(region: RegionCode): void {
  try {
    window.localStorage.setItem(REGION_STORAGE_KEY, region);
  } catch {
    // Ignore storage access errors
  }
}
