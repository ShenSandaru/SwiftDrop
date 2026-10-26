/**
 * Typed HTTP client for the SwiftDrop Spring Boot REST API.
 * Handles base URL configuration, Bearer token injection, and RFC 9457 Problem Details error parsing.
 */

export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  invalidFields?: string[];
  [key: string]: unknown;
}

export interface UserResponse {
  id: string;
  email: string;
  fullName: string;
  roles: string[];
}

export interface AccessTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem?: ProblemDetail;

  constructor(status: number, message: string, problem?: ProblemDetail) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
  }
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  token?: string | null;
  body?: unknown;
}

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

export function onUnauthorized(handler: UnauthorizedHandler): void {
  unauthorizedHandler = handler;
}

export function getApiBaseUrl(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
}

export async function apiFetch<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const baseUrl = getApiBaseUrl().replace(/\/+$/, "");
  const path = endpoint.startsWith("/") ? endpoint : `/${endpoint}`;
  const url = `${baseUrl}${path}`;

  const headers = new Headers(options.headers);
  if (!headers.has("Content-Type") && options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  // Inject token if provided or if present in sessionStorage
  const token =
    options.token ??
    (typeof window !== "undefined" ? window.sessionStorage.getItem("swiftdrop_token") : null);

  if (token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(url, {
    ...options,
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  if (!response.ok) {
    if (response.status === 401 && unauthorizedHandler) {
      unauthorizedHandler();
    }

    let problem: ProblemDetail | undefined;
    const contentType = response.headers.get("content-type") || "";

    if (contentType.includes("json")) {
      try {
        problem = (await response.json()) as ProblemDetail;
      } catch {
        // Fall back to status text if JSON parsing fails
      }
    }

    const message = problem?.detail || problem?.title || `Request failed with status ${response.status}`;
    throw new ApiError(response.status, message, problem);
  }

  if (response.status === 204) {
    return undefined as unknown as T;
  }

  return (await response.json()) as T;
}

export const api = {
  get<T>(endpoint: string, options?: RequestOptions): Promise<T> {
    return apiFetch<T>(endpoint, { ...options, method: "GET" });
  },
  post<T>(endpoint: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return apiFetch<T>(endpoint, { ...options, method: "POST", body });
  },
  put<T>(endpoint: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return apiFetch<T>(endpoint, { ...options, method: "PUT", body });
  },
  patch<T>(endpoint: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return apiFetch<T>(endpoint, { ...options, method: "PATCH", body });
  },
  delete<T>(endpoint: string, options?: RequestOptions): Promise<T> {
    return apiFetch<T>(endpoint, { ...options, method: "DELETE" });
  },
};

/**
 * Returns a user-friendly error message from an unknown error without exposing raw error bodies.
 */
export function formatApiError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.problem?.detail) {
      return error.problem.detail;
    }
    if (error.status === 401) {
      return "Invalid email or password, or session expired.";
    }
    if (error.status === 403) {
      return "You do not have permission to access this resource.";
    }
    if (error.status === 404) {
      return "The requested resource was not found.";
    }
    if (error.status === 409) {
      return "An account with these details already exists or a conflict occurred.";
    }
    return `Error (${error.status}): ${error.message}`;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "An unexpected network error occurred. Please check your connection and try again.";
}
