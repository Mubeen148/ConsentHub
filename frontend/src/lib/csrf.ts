// =============================================================================
// Double-Submit Cookie + Custom Header CSRF Manager (rest-endpoints.md §1.2)
// =============================================================================

let cachedCsrfToken: string | null = null;
let inflightCsrfPromise: Promise<string | null> | null = null;

export function readCookie(name: string): string | null {
  if (typeof document === "undefined" || !document.cookie) {
    return null;
  }
  const cookies = document.cookie.split(";");
  for (const part of cookies) {
    const trimmed = part.trim();
    if (trimmed.startsWith(`${name}=`)) {
      const rawValue = trimmed.slice(name.length + 1);
      try {
        return decodeURIComponent(rawValue);
      } catch {
        return rawValue;
      }
    }
  }
  return null;
}

export function getCsrfTokenSync(): string | null {
  const fromCookie = readCookie("ch_csrf_token");
  if (fromCookie && fromCookie.length > 0) {
    cachedCsrfToken = fromCookie;
    return fromCookie;
  }
  return cachedCsrfToken;
}

export function setCachedCsrfToken(token: string | null): void {
  cachedCsrfToken = token;
}

export async function ensureCsrfToken(forceRefresh = false): Promise<string | null> {
  if (!forceRefresh) {
    const existing = getCsrfTokenSync();
    if (existing) {
      return existing;
    }
  }

  if (inflightCsrfPromise) {
    return inflightCsrfPromise;
  }

  inflightCsrfPromise = (async () => {
    try {
      const response = await fetch("/api/v1/auth/csrf", {
        method: "GET",
        credentials: "include",
        headers: {
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        return getCsrfTokenSync();
      }
      const data = (await response.json()) as { csrfToken?: string };
      if (data.csrfToken) {
        cachedCsrfToken = data.csrfToken;
        return data.csrfToken;
      }
      return getCsrfTokenSync();
    } catch {
      return getCsrfTokenSync();
    } finally {
      inflightCsrfPromise = null;
    }
  })();

  return inflightCsrfPromise;
}
