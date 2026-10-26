"use client";

import React, { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { api, formatApiError, getApiBaseUrl, type AccessTokenResponse } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

/**
 * Validates and sanitizes a post-login redirect path to prevent open redirect vulnerabilities.
 * Accepts only safe relative paths starting with a single '/' (e.g. "/customer", "/track?code=123").
 * Rejects protocol-relative URLs ('//'), backslash tricks ('/\'), scheme prefixes ('https:', 'javascript:'),
 * and control characters, falling back to the default home path.
 */
function sanitizeRedirectTarget(target: string | null | undefined): string {
  if (!target || typeof target !== "string") {
    return "/";
  }

  const trimmed = target.trim();

  if (trimmed === "/") {
    return "/";
  }

  // Must begin with a single '/' and not be followed by '/' or '\'
  if (!trimmed.startsWith("/") || trimmed.startsWith("//") || trimmed.startsWith("/\\")) {
    return "/";
  }

  // Reject CRLF or control characters
  if (/[\x00-\x1F\x7F]/.test(trimmed)) {
    return "/";
  }

  try {
    const parsed = new URL(trimmed, "http://localhost");
    // Ensure the host authority was not parsed and origin remains unchanged
    if (parsed.origin !== "http://localhost" || !parsed.pathname.startsWith("/")) {
      return "/";
    }
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return "/";
  }
}

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { login } = useAuth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const registered = searchParams.get("registered") === "true";
  const expired = searchParams.get("expired") === "true";
  const redirectTarget = sanitizeRedirectTarget(searchParams.get("redirect"));

  // Google button is enabled only when explicitly configured via environment flag (AC-2)
  const isGoogleLoginEnabled = process.env.NEXT_PUBLIC_ENABLE_GOOGLE_LOGIN === "true";

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (isLoading) return;

    setErrorMessage(null);

    // Client-side validation hints
    const trimmedEmail = email.trim();
    if (!trimmedEmail) {
      setErrorMessage("Please enter your email address.");
      return;
    }
    if (!password) {
      setErrorMessage("Please enter your password.");
      return;
    }

    setIsLoading(true);

    try {
      const response = await api.post<AccessTokenResponse>("/api/v1/auth/tokens", {
        email: trimmedEmail,
        password,
      });

      await login(response.accessToken);
      router.push(redirectTarget);
    } catch (err: unknown) {
      setErrorMessage(formatApiError(err));
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <Card className="w-full max-w-md rounded-xl bg-card border-border shadow-xs">
      <CardHeader className="space-y-1 text-center">
        <div className="inline-flex items-center justify-center gap-1.5 mx-auto mb-2 text-xs font-semibold uppercase tracking-wider text-primary">
          <span className="size-1.5 rounded-full bg-primary" />
          SwiftDrop Account
        </div>
        <CardTitle className="text-2xl font-bold tracking-tight text-foreground">
          Welcome back
        </CardTitle>
        <CardDescription className="text-sm text-muted-foreground">
          Sign in to access your parcel deliveries and workspaces.
        </CardDescription>
      </CardHeader>

      <form onSubmit={handleSubmit} noValidate>
        <CardContent className="space-y-4">
          {registered && (
            <div
              role="status"
              className="rounded-lg border border-primary/20 bg-accent p-3 text-xs text-primary font-medium"
            >
              Account created successfully! Please sign in with your email and password.
            </div>
          )}

          {expired && (
            <div
              role="alert"
              className="rounded-lg border border-border bg-muted p-3 text-xs text-muted-foreground font-medium"
            >
              Your session has expired. Please sign in again to continue.
            </div>
          )}

          {errorMessage && (
            <div
              role="alert"
              className="rounded-lg border border-destructive/20 bg-destructive/10 p-3 text-xs text-destructive font-medium"
            >
              {errorMessage}
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="login-email">Email address</Label>
            <Input
              id="login-email"
              type="email"
              autoComplete="email"
              placeholder="name@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={isLoading}
              required
              className="bg-background text-sm"
            />
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="login-password">Password</Label>
            </div>
            <Input
              id="login-password"
              type="password"
              autoComplete="current-password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={isLoading}
              required
              className="bg-background text-sm"
            />
          </div>

          <Button
            type="submit"
            disabled={isLoading}
            className="w-full font-semibold shadow-xs mt-2"
          >
            {isLoading ? "Signing in..." : "Sign in"}
          </Button>

          {isGoogleLoginEnabled && (
            <div className="pt-2">
              <div className="relative flex items-center justify-center my-3 text-xs uppercase text-muted-foreground">
                <span className="w-full border-t border-border" />
                <span className="bg-card px-2">or</span>
                <span className="w-full border-t border-border" />
              </div>
              <Button
                type="button"
                variant="outline"
                className="w-full font-medium"
                onClick={() => {
                  const target = new URL("/oauth2/authorization/google", getApiBaseUrl()).href;
                  window.location.assign(target);
                }}
              >
                Sign in with Google
              </Button>
            </div>
          )}
        </CardContent>

        <CardFooter className="flex flex-col gap-2 pt-2 border-t border-border/60 text-center text-xs text-muted-foreground">
          <p>
            Don&apos;t have an account?{" "}
            <Link
              href="/signup"
              className="font-semibold text-primary underline-offset-4 hover:underline"
            >
              Sign up
            </Link>
          </p>
        </CardFooter>
      </form>
    </Card>
  );
}

export default function LoginPage() {
  return (
    <main className="container mx-auto flex-1 flex items-center justify-center px-4 py-12 min-h-[calc(100vh-3.5rem)]">
      <Suspense fallback={<div className="h-96 w-full max-w-md animate-pulse rounded-xl bg-card border border-border" />}>
        <LoginForm />
      </Suspense>
    </main>
  );
}
