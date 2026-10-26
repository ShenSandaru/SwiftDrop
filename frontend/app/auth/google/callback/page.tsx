"use client";

import React, { Suspense, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { cn } from "cn";
import { api, ApiError, formatApiError, type AccessTokenResponse } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";

/**
 * Standard allowlist of OAuth error codes and user-facing messages defined by ADR 0005.
 * Arbitrary error codes or raw provider messages are never rendered directly.
 */
const ALLOWED_ERROR_MESSAGES: Record<string, string> = {
  conflict_email_exists:
    "An account with this email already exists. Please sign in with your email and password.",
  unverified_email:
    "Your Google account email address is not verified. Please verify your email with Google and try again.",
  account_disabled:
    "Your account has been deactivated. Please contact support.",
  cancelled:
    "Google sign-in was cancelled.",
  oauth_failed:
    "Google sign-in failed. Please try again.",
};

const DEFAULT_POST_LOGIN_PATH = "/customer";

/**
 * Validates and sanitizes the post-login destination retrieved from sessionStorage (ADR 0005).
 * Rejects external origins, protocol-relative URLs ('//'), backslash tricks ('/\'),
 * control characters, and navigation back to '/login', falling back safely to '/customer'.
 */
function sanitizeRedirectTarget(target: string | null | undefined): string {
  if (!target || typeof target !== "string") {
    return DEFAULT_POST_LOGIN_PATH;
  }

  const trimmed = target.trim();

  if (trimmed === "" || trimmed === "/" || trimmed === "/login") {
    return DEFAULT_POST_LOGIN_PATH;
  }

  // Must begin with a single '/' and not be followed by '/' or '\'
  if (!trimmed.startsWith("/") || trimmed.startsWith("//") || trimmed.startsWith("/\\")) {
    return DEFAULT_POST_LOGIN_PATH;
  }

  // Reject CRLF or control characters
  if (/[\x00-\x1F\x7F]/.test(trimmed)) {
    return DEFAULT_POST_LOGIN_PATH;
  }

  try {
    const parsed = new URL(trimmed, "http://localhost");
    // Ensure the host authority was not parsed, origin remains unchanged, and destination is not a login loop
    if (
      parsed.origin !== "http://localhost" ||
      !parsed.pathname.startsWith("/") ||
      parsed.pathname === "/login"
    ) {
      return DEFAULT_POST_LOGIN_PATH;
    }
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return DEFAULT_POST_LOGIN_PATH;
  }
}

function GoogleCallbackContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { login } = useAuth();

  const errorParam = searchParams.get("error");
  const codeParam = searchParams.get("code");

  const initialError = errorParam
    ? ALLOWED_ERROR_MESSAGES[errorParam] || "Google sign-in could not be completed. Please try again."
    : !codeParam
      ? "No authorization code was provided. Please return to sign in and try again."
      : null;

  const [errorMessage, setErrorMessage] = useState<string | null>(initialError);
  const [isProcessing, setIsProcessing] = useState<boolean>(!initialError);

  // Guard against React StrictMode double-execution and repeated effect triggers
  const hasProcessedRef = useRef(false);

  useEffect(() => {
    // Clear temporary parameters from browser address bar immediately without losing extracted values
    if (typeof window !== "undefined" && (errorParam || codeParam)) {
      window.history.replaceState({}, document.title, window.location.pathname);
    }

    if (initialError || !codeParam) {
      return;
    }

    if (hasProcessedRef.current) {
      return;
    }
    hasProcessedRef.current = true;

    async function exchangeCode() {
      try {
        const response = await api.post<AccessTokenResponse>("/api/v1/auth/google/exchanges", {
          code: codeParam,
        });

        // Use standard AuthContext session management (saves token and loads user profile)
        await login(response.accessToken);

        // Verify that authentication state was successfully established
        const hasSession =
          typeof window !== "undefined" && Boolean(window.sessionStorage.getItem("swiftdrop_token"));
        if (!hasSession) {
          setIsProcessing(false);
          setErrorMessage(
            "Authentication succeeded, but your user profile could not be loaded. Please return to sign in and try again."
          );
          return;
        }

        // Retrieve and double-sanitize post-login destination from sessionStorage (ADR 0005)
        let destination = DEFAULT_POST_LOGIN_PATH;
        if (typeof window !== "undefined") {
          const storedDestination = window.sessionStorage.getItem("swiftdrop_auth_redirect");
          window.sessionStorage.removeItem("swiftdrop_auth_redirect");
          destination = sanitizeRedirectTarget(storedDestination);
        }

        router.push(destination);
      } catch (err: unknown) {
        setIsProcessing(false);
        if (err instanceof ApiError && err.status === 401) {
          setErrorMessage(
            "The authorization exchange code is invalid, expired, or has already been used. Please try signing in again."
          );
        } else {
          setErrorMessage(formatApiError(err));
        }
      }
    }

    exchangeCode();
  }, [codeParam, errorParam, initialError, login, router]);

  return (
    <Card className="w-full max-w-md rounded-xl bg-card border-border shadow-xs text-center">
      <CardHeader className="space-y-1">
        <div className="inline-flex items-center justify-center gap-1.5 mx-auto mb-2 text-xs font-semibold uppercase tracking-wider text-primary">
          <span className="size-1.5 rounded-full bg-primary" />
          SwiftDrop Account
        </div>
        <CardTitle className="text-2xl font-bold tracking-tight text-foreground">
          {errorMessage ? "Sign In Failed" : "Signing You In"}
        </CardTitle>
        <CardDescription className="text-sm text-muted-foreground">
          {errorMessage
            ? "We were unable to complete authentication with Google."
            : "Completing Google authentication and verifying your credentials..."}
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-4 py-4">
        {isProcessing && (
          <div className="flex flex-col items-center justify-center gap-3 py-6" aria-live="polite">
            <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            <p className="text-xs text-muted-foreground">Exchanging authentication code...</p>
          </div>
        )}

        {errorMessage && (
          <div
            role="alert"
            className="rounded-lg border border-destructive/20 bg-destructive/10 p-3 text-xs text-destructive font-medium text-left"
          >
            {errorMessage}
          </div>
        )}
      </CardContent>

      {errorMessage && (
        <CardFooter className="flex flex-col gap-2 pt-2 border-t border-border/60">
          <Link
            href="/login"
            className={cn(buttonVariants({ variant: "default" }), "w-full font-semibold shadow-xs")}
          >
            Return to Sign In
          </Link>
        </CardFooter>
      )}
    </Card>
  );
}

export default function GoogleCallbackPage() {
  return (
    <main className="container mx-auto flex-1 flex items-center justify-center px-4 py-12 min-h-[calc(100vh-3.5rem)]">
      <Suspense
        fallback={
          <div className="h-64 w-full max-w-md animate-pulse rounded-xl bg-card border border-border" />
        }
      >
        <GoogleCallbackContent />
      </Suspense>
    </main>
  );
}
