import { api, type UserResponse } from "./api";

/**
 * Validates and sanitizes a post-auth redirect path to prevent open redirect vulnerabilities.
 * Accepts only safe relative paths starting with a single '/' (e.g. "/customer", "/track?code=123").
 * Rejects protocol-relative URLs ('//'), backslash tricks ('/\'), scheme prefixes ('https:', 'javascript:'),
 * and control characters, falling back safely to the specified fallback.
 */
export function sanitizeRedirectTarget(
  target: string | null | undefined,
  fallback = "/"
): string {
  if (!target || typeof target !== "string") {
    return fallback;
  }

  const trimmed = target.trim();

  if (trimmed === "" || trimmed === "/" || trimmed === "/login") {
    return fallback;
  }

  // Must begin with a single '/' and not be followed by '/' or '\'
  if (!trimmed.startsWith("/") || trimmed.startsWith("//") || trimmed.startsWith("/\\")) {
    return fallback;
  }

  // Reject CRLF or control characters
  if (/[\x00-\x1F\x7F]/.test(trimmed)) {
    return fallback;
  }

  try {
    const parsed = new URL(trimmed, "http://localhost");
    // Ensure the host authority was not parsed, origin remains unchanged, and destination is not a login loop
    if (
      parsed.origin !== "http://localhost" ||
      !parsed.pathname.startsWith("/") ||
      parsed.pathname === "/login"
    ) {
      return fallback;
    }
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return fallback;
  }
}

/**
 * Returns true if a given path is an internal staff or merchant destination where
 * Google OAuth is not permitted (only email/password authentication is allowed).
 */
export function isNonCustomerDestination(path: string | null | undefined): boolean {
  if (!path) return false;
  const normalized = path.split("?")[0].split("#")[0];
  return (
    normalized === "/merchant" ||
    normalized.startsWith("/merchant/") ||
    normalized === "/admin" ||
    normalized.startsWith("/admin/") ||
    normalized === "/dispatch" ||
    normalized.startsWith("/dispatch/") ||
    normalized === "/courier" ||
    normalized.startsWith("/courier/")
  );
}

/**
 * Computes the post-authentication destination based on user roles and profile completion state.
 * Standardizes navigation across manual email/password sign-in, Google sign-in, and Google registration.
 */
export async function getPostAuthRedirect(
  user: UserResponse,
  token: string,
  rawRedirect?: string | null
): Promise<string> {
  const safeRedirect = sanitizeRedirectTarget(rawRedirect, "/");

  if (user.roles?.includes("MERCHANT")) {
    try {
      const statusRes = await api.get<{ status: string }>("/api/v1/merchants/me", { token });
      if (statusRes.status === "APPROVED") {
        return safeRedirect === "/" ? "/merchant" : safeRedirect;
      }
      return "/merchant/status";
    } catch {
      return "/merchant/status";
    }
  }

  if (user.roles?.includes("ADMIN")) {
    return safeRedirect === "/" ? "/admin" : safeRedirect;
  }

  if (user.roles?.includes("DISPATCHER")) {
    return safeRedirect === "/" || safeRedirect.startsWith("/admin")
      ? "/dispatch"
      : safeRedirect;
  }

  if (user.roles?.includes("COURIER")) {
    return safeRedirect === "/" ? "/courier" : safeRedirect;
  }

  // Default: CUSTOMER role
  // If destination explicitly directs to /customer/profile, honor it if profile is incomplete
  if (safeRedirect === "/customer/profile") {
    return user.profileCompleted ? "/customer" : "/customer/profile";
  }

  // Otherwise, honor safe destination (e.g. /customer/new) or default to /customer
  return safeRedirect === "/" ? "/customer" : safeRedirect;
}

