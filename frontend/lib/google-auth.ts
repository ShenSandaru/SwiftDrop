import { getApiBaseUrl } from "./api";
import { sanitizeRedirectTarget } from "./role-redirect";

/**
 * Returns true if Google OAuth is explicitly enabled via environment variable.
 */
export function isGoogleLoginEnabled(): boolean {
  return process.env.NEXT_PUBLIC_ENABLE_GOOGLE_LOGIN === "true";
}

/**
 * Initiates the Google OAuth flow by storing the optional post-auth redirect target
 * in sessionStorage (per ADR 0005) and redirecting the browser to the backend
 * authorization endpoint.
 */
export function initiateGoogleAuth(redirectTarget?: string | null): void {
  if (typeof window !== "undefined") {
    const targetPath = sanitizeRedirectTarget(redirectTarget, "/");
    if (targetPath && targetPath !== "/" && targetPath !== "/login") {
      window.sessionStorage.setItem("swiftdrop_auth_redirect", targetPath);
    } else {
      window.sessionStorage.removeItem("swiftdrop_auth_redirect");
    }
  }

  const target = new URL("/oauth2/authorization/google", getApiBaseUrl()).href;
  window.location.assign(target);
}

