import type {
  ApiErrorEnvelope,
  AuthSessionResponse,
  RegionCode,
  UserProfile,
} from "../types/contracts";
import { ensureCsrfToken, getCsrfTokenSync, setCachedCsrfToken } from "./csrf";

export class RestApiError extends Error {
  public readonly status: number;
  public readonly code: string;
  public readonly correlationId?: string;
  public readonly fieldErrors?: Record<string, string>;

  constructor(envelope: ApiErrorEnvelope) {
    super(envelope.message || "Request failed. Please try again.");
    this.name = "RestApiError";
    this.status = envelope.status;
    this.code = envelope.code || "API_ERROR";
    this.correlationId = envelope.correlationId;
    this.fieldErrors = envelope.fieldErrors;
  }
}

async function parseErrorResponse(response: Response): Promise<RestApiError> {
  try {
    const body = (await response.json()) as Partial<ApiErrorEnvelope>;
    return new RestApiError({
      status: body.status ?? response.status,
      code: body.code ?? `HTTP_${response.status}`,
      message:
        body.message ??
        (response.status === 429
          ? "Too many requests. Please wait a moment before trying again."
          : `Request failed with status ${response.status}.`),
      correlationId: body.correlationId,
      fieldErrors: body.fieldErrors,
    });
  } catch {
    return new RestApiError({
      status: response.status,
      code: `HTTP_${response.status}`,
      message:
        response.status === 429
          ? "Too many requests. Please wait a moment before trying again."
          : `Unable to reach ConsentHub server (HTTP ${response.status}).`,
    });
  }
}

export interface LoginPayload {
  email: string;
  password: string;
  anonymousSubjectKey?: string;
}

export interface SignupPayload {
  email: string;
  password: string;
  fullName: string;
  region: RegionCode;
  anonymousSubjectKey?: string;
}

export async function apiLogin(payload: LoginPayload): Promise<AuthSessionResponse> {
  const csrf = getCsrfTokenSync();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json",
  };
  if (csrf) {
    headers["X-CSRF-Token"] = csrf;
  }

  const response = await fetch("/api/v1/auth/login", {
    method: "POST",
    credentials: "include",
    headers,
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw await parseErrorResponse(response);
  }

  const session = (await response.json()) as AuthSessionResponse;
  if (session.csrfToken) {
    setCachedCsrfToken(session.csrfToken);
  }
  return session;
}

export async function apiSignup(payload: SignupPayload): Promise<AuthSessionResponse> {
  const csrf = getCsrfTokenSync();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json",
  };
  if (csrf) {
    headers["X-CSRF-Token"] = csrf;
  }

  const response = await fetch("/api/v1/auth/signup", {
    method: "POST",
    credentials: "include",
    headers,
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw await parseErrorResponse(response);
  }

  const session = (await response.json()) as AuthSessionResponse;
  if (session.csrfToken) {
    setCachedCsrfToken(session.csrfToken);
  }
  return session;
}

export async function apiLogout(): Promise<void> {
  const csrf = (await ensureCsrfToken()) ?? "";
  const response = await fetch("/api/v1/auth/logout", {
    method: "POST",
    credentials: "include",
    headers: {
      ...(csrf ? { "X-CSRF-Token": csrf } : {}),
    },
  });

  if (!response.ok && response.status !== 204 && response.status !== 401) {
    throw await parseErrorResponse(response);
  }

  setCachedCsrfToken(null);
  await ensureCsrfToken(true);
}

export async function apiGetMe(): Promise<UserProfile | null> {
  const response = await fetch("/api/v1/auth/me", {
    method: "GET",
    credentials: "include",
    headers: {
      Accept: "application/json",
    },
  });

  if (response.status === 401) {
    return null;
  }

  if (!response.ok) {
    throw await parseErrorResponse(response);
  }

  return (await response.json()) as UserProfile;
}

export interface ExportDownloadResult {
  filename: string;
  byteLength: number;
}

export async function apiDownloadPrivacyExport(): Promise<ExportDownloadResult> {
  const response = await fetch("/api/v1/privacy/export", {
    method: "GET",
    credentials: "include",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw await parseErrorResponse(response);
  }

  const disposition = response.headers.get("Content-Disposition") ?? "";
  const filenameMatch = disposition.match(/filename="?([^";]+)"?/i);
  const fallbackDate = new Date().toISOString().slice(0, 10);
  const filename = filenameMatch?.[1] ?? `consenthub-data-export-${fallbackDate}.json`;

  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  try {
    const anchor = document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = filename;
    anchor.rel = "noopener";
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
  } finally {
    setTimeout(() => URL.revokeObjectURL(objectUrl), 1500);
  }

  return {
    filename,
    byteLength: blob.size,
  };
}
