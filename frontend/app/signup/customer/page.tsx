"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, ApiError, formatApiError, type AccessTokenResponse, type UserResponse } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  AuthLayout,
  AuthBrandingPanel,
  AuthFormContainer,
  AuthHeader,
  PasswordInput,
} from "@/components/auth";

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MAX_PASSWORD_BYTES = 72;

export default function SignupPage() {
  const router = useRouter();
  const { login } = useAuth();

  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isConflict, setIsConflict] = useState(false);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (isLoading) return;

    setErrorMessage(null);
    setIsConflict(false);

    // Client-side validations
    const trimmedName = fullName.trim();
    const trimmedEmail = email.trim().toLowerCase();

    if (!trimmedName) {
      setErrorMessage("Please enter your full name.");
      return;
    }
    if (trimmedName.length > 200) {
      setErrorMessage("Full name must be at most 200 characters.");
      return;
    }

    if (!trimmedEmail) {
      setErrorMessage("Please enter your email address.");
      return;
    }
    if (!EMAIL_REGEX.test(trimmedEmail) || trimmedEmail.length > 320) {
      setErrorMessage("Please enter a valid email address (max 320 characters).");
      return;
    }

    if (!password) {
      setErrorMessage("Please enter a password.");
      return;
    }
    if (password.length < 8) {
      setErrorMessage("Password must be at least 8 characters long.");
      return;
    }
    const passwordBytes = new TextEncoder().encode(password).length;
    if (passwordBytes > MAX_PASSWORD_BYTES) {
      setErrorMessage(`Password exceeds the maximum length of ${MAX_PASSWORD_BYTES} bytes.`);
      return;
    }

    if (password !== confirmPassword) {
      setErrorMessage("Passwords do not match. Please re-enter your password.");
      return;
    }

    setIsLoading(true);

    try {
      // 1. Register customer account (strictly no role field)
      await api.post<UserResponse>("/api/v1/users", {
        fullName: trimmedName,
        email: trimmedEmail,
        password,
      });

      // 2. Auto-authenticate on successful registration for seamless UX
      try {
        const tokenRes = await api.post<AccessTokenResponse>("/api/v1/auth/tokens", {
          email: trimmedEmail,
          password,
        });
        await login(tokenRes.accessToken);
        router.push("/customer");
      } catch {
        // Fallback to login page with registered banner
        router.push("/login?registered=true");
      }
    } catch (err: unknown) {
      if (err instanceof ApiError && err.status === 409) {
        setIsConflict(true);
        setErrorMessage(
          "An account with this email address already exists. Please sign in with your credentials."
        );
      } else {
        setErrorMessage(formatApiError(err));
      }
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <AuthLayout
      branding={
        <AuthBrandingPanel
          headline="From your door to anywhere on the island."
          description="Create an account to calculate instant courier fares, schedule doorstep pickups, and track packages in real time."
        />
      }
    >
      <div className="space-y-4">
        <Link
          href="/signup"
          className="text-xs text-muted-foreground hover:text-foreground inline-flex items-center gap-1.5 transition-colors font-medium"
        >
          &larr; Back to account selection
        </Link>

        <AuthFormContainer>
          <AuthHeader
            badge="Customer"
            title="Create an account"
            description="Get started with SwiftDrop parcel bookings and live delivery tracking."
          />

          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            {errorMessage && (
              <div
                role="alert"
                className="rounded-lg border border-destructive/20 bg-destructive/10 p-3 text-xs text-destructive font-medium space-y-1.5"
              >
                <p>{errorMessage}</p>
                {isConflict && (
                  <p>
                    <Link
                      href="/login"
                      className="font-semibold underline underline-offset-4 hover:text-foreground"
                    >
                      Click here to sign in &rarr;
                    </Link>
                  </p>
                )}
              </div>
            )}

            <div className="space-y-1.5">
              <Label htmlFor="signup-name">Full name</Label>
              <Input
                id="signup-name"
                type="text"
                autoComplete="name"
                placeholder="Jane Doe"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                disabled={isLoading}
                maxLength={200}
                required
                className="h-11 text-sm rounded-xl bg-background border-border/80 transition-all duration-200 hover:border-primary/50 focus:border-primary focus:ring-4 focus:ring-primary/15 focus-visible:border-primary focus-visible:ring-4 focus-visible:ring-primary/15"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="signup-email">Email address</Label>
              <Input
                id="signup-email"
                type="email"
                autoComplete="email"
                placeholder="name@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={isLoading}
                maxLength={320}
                required
                className="h-11 text-sm rounded-xl bg-background border-border/80 transition-all duration-200 hover:border-primary/50 focus:border-primary focus:ring-4 focus:ring-primary/15 focus-visible:border-primary focus-visible:ring-4 focus-visible:ring-primary/15"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="signup-password">Password</Label>
              <PasswordInput
                id="signup-password"
                autoComplete="new-password"
                placeholder="At least 8 characters"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={isLoading}
                required
              />
              <p className="text-[11px] text-muted-foreground">
                Minimum 8 characters, maximum 72 bytes.
              </p>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="signup-confirm-password">Confirm password</Label>
              <PasswordInput
                id="signup-confirm-password"
                autoComplete="new-password"
                placeholder="Re-enter your password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                disabled={isLoading}
                required
              />
            </div>

            <Button
              type="submit"
              disabled={isLoading}
              className="w-full h-11 rounded-xl font-semibold bg-primary hover:bg-[#0c5f59] hover:shadow-[0_8px_20px_-4px_rgba(15,118,110,0.4)] hover:-translate-y-0.5 active:translate-y-0 active:shadow-sm transition-all duration-200 ease-out disabled:pointer-events-none disabled:opacity-50 disabled:transform-none disabled:shadow-none motion-reduce:transform-none motion-reduce:transition-none cursor-pointer mt-2"
            >
              {isLoading ? "Creating account..." : "Create customer account"}
            </Button>

            <div className="pt-3 border-t border-border/60 text-center text-xs text-muted-foreground">
              Already have an account?{" "}
              <Link
                href="/login"
                className="font-semibold text-primary underline-offset-4 hover:underline"
              >
                Sign in
              </Link>
            </div>
          </form>
        </AuthFormContainer>
      </div>
    </AuthLayout>
  );
}
