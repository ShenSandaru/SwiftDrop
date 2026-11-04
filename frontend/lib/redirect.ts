/**
 * Validates and sanitizes internal redirect paths to prevent open redirect vulnerabilities.
 * Accepts only safe relative paths starting with a single '/' (e.g. "/customer", "/track?code=123").
 * Rejects protocol-relative URLs ('//'), backslash tricks ('/\\'), scheme prefixes ('https:', 'javascript:'),
 * and control characters, falling back to the default fallback path.
 */
export function sanitizeInternalRedirect(target: string | null | undefined, fallback = "/"): string {
  if (!target || typeof target !== "string") {
    return fallback;
  }

  const trimmed = target.trim();
  if (trimmed === "" || trimmed === "/") {
    return fallback;
  }

  // Must begin with a single '/' and not start with '//' or '/\'
  if (!trimmed.startsWith("/") || trimmed.startsWith("//") || trimmed.startsWith("/\\")) {
    return fallback;
  }

  // Reject CRLF or control characters
  if (/[\x00-\x1F\x7F]/.test(trimmed)) {
    return fallback;
  }

  try {
    const parsed = new URL(trimmed, "http://localhost");
    // Ensure the host authority was not parsed, origin is local, and destination is not a login loop
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
 * Maps user roles to the corresponding primary dashboard path.
 * Priority: DISPATCHER_ADMIN -> /dispatch, COURIER -> /courier, MERCHANT -> /merchant, CUSTOMER -> /customer.
 * Default fallback is /customer.
 */
export function getDefaultDashboardForRoles(roles?: string[] | null): string {
  if (!roles || roles.length === 0) {
    return "/customer";
  }
  if (roles.includes("DISPATCHER_ADMIN")) {
    return "/dispatch";
  }
  if (roles.includes("COURIER")) {
    return "/courier";
  }
  if (roles.includes("MERCHANT")) {
    return "/merchant";
  }
  if (roles.includes("CUSTOMER")) {
    return "/customer";
  }
  return "/customer";
}

