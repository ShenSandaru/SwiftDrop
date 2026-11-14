"use client";

import React, { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { api, formatApiError, type AccessTokenResponse, type UserResponse } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { initiateGoogleAuth, isGoogleLoginEnabled as checkGoogleLoginEnabled } from "@/lib/google-auth";
import {
  getPortalContext,
  getPostAuthRedirect,
  sanitizeRedirectTarget,
  type PortalRole,
} from "@/lib/role-redirect";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  AuthLayout,
  AuthBrandingPanel,
  AuthFormContainer,
  AuthHeader,
  AuthDivider,
  SocialLoginButton,
  PasswordInput,
} from "@/components/auth";
import { Shield, Truck, Warehouse, Store, User } from "lucide-react";

interface PortalConfig {
  badge: string;
  title: string;
  description: string;
  icon: React.ReactNode;
  headline: string;
  subheadline: string;
  imageSrc: string;
  imageAlt: string;
}

const PORTAL_CONFIGS: Record<PortalRole, PortalConfig> = {
  CUSTOMER: {
    badge: "Customer Portal",
    title: "Welcome Back",
    description: "Sign in to book parcels and track your deliveries.",
    icon: <User className="size-4" aria-hidden="true" />,
    headline: "From your door to anywhere on the island.",
    subheadline: "Book in a minute. Track every stop. Delivered with proof.",
    imageSrc: "/images/auth-delivery.png",
    imageAlt: "Professional courier delivering parcel box at doorstep",
  },
  MERCHANT: {
    badge: "Merchant Portal",
    title: "Merchant Portal",
    description: "Sign in to manage your business and deliveries.",
    icon: <Store className="size-4" aria-hidden="true" />,
    headline: "Grow and streamline your commercial deliveries.",
    subheadline: "Bulk order intake, scheduled pickups, and real-time shipment monitoring.",
    imageSrc: "/images/business-account.png",
    imageAlt: "Merchant shipping account dashboard on digital tablet",
  },
  COURIER: {
    badge: "Courier Portal",
    title: "Courier Portal",
    description: "Sign in to view and manage your assigned deliveries.",
    icon: <Truck className="size-4" aria-hidden="true" />,
    headline: "Your daily runs, routes, and handoffs in one place.",
    subheadline: "View assigned parcels, record digital proof of delivery, and update transit statuses.",
    imageSrc: "/images/auth-courier.png",
    imageAlt: "SwiftDrop courier driver with delivery van and parcel packages",
  },
  DISPATCHER: {
    badge: "Dispatcher Portal",
    title: "Dispatcher Portal",
    description: "Sign in to coordinate and manage delivery operations.",
    icon: <Warehouse className="size-4" aria-hidden="true" />,
    headline: "Centralized delivery dispatch and fleet coordination.",
    subheadline: "Manage regional hub operations, balance courier workloads, and resolve exceptions.",
    imageSrc: "/images/auth-dispatcher.png",
    imageAlt: "Logistics dispatch coordinator monitoring live parcel routes on operations screens",
  },
  ADMIN: {
    badge: "Admin Portal",
    title: "Administration Portal",
    description: "Sign in to manage and oversee SwiftDrop operations.",
    icon: <Shield className="size-4" aria-hidden="true" />,
    headline: "Platform administration and system oversight.",
    subheadline: "Onboard staff, review merchant accounts, configure hubs, and manage pricing rules.",
    imageSrc: "/images/auth-admin.png",
    imageAlt: "System administrator managing enterprise logistics platform controls and user access",
  },
};

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
  const portalContext = getPortalContext(redirectTarget);
  const portalConfig = PORTAL_CONFIGS[portalContext];

  // Google button and public customer signup prompt are enabled only for customer authentication
  const isCustomerContext = portalContext === "CUSTOMER";
  const isGoogleLoginEnabled = checkGoogleLoginEnabled() && isCustomerContext;

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

      try {
        const currentUser = await api.get<UserResponse>("/api/v1/users/me", { token: response.accessToken });
        const destination = await getPostAuthRedirect(currentUser, response.accessToken, redirectTarget);
        router.push(destination);
      } catch {
        router.push(redirectTarget);
      }
    } catch (err: unknown) {
      setErrorMessage(formatApiError(err));
    } finally {
      setIsLoading(false);
    }
  }

  function handleGoogleSignIn() {
    initiateGoogleAuth(searchParams.get("redirect"));
  }

  return (
    <AuthLayout
      branding={
        <AuthBrandingPanel
          badgeText={portalConfig.badge}
          headline={portalConfig.headline}
          description={portalConfig.subheadline}
          imageSrc={portalConfig.imageSrc}
          imageAlt={portalConfig.imageAlt}
        />
      }
    >
      <AuthFormContainer>
        <AuthHeader
          badge={portalConfig.badge}
          icon={portalConfig.icon}
          title={portalConfig.title}
          description={portalConfig.description}
        />

        <form onSubmit={handleSubmit} noValidate className="space-y-4">
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
              className="h-11 text-sm rounded-xl bg-background border-border/80 transition-all duration-200 hover:border-primary/50 focus:border-primary focus:ring-4 focus:ring-primary/15 focus-visible:border-primary focus-visible:ring-4 focus-visible:ring-primary/15"
            />
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="login-password">Password</Label>
            </div>
            <PasswordInput
              id="login-password"
              autoComplete="current-password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={isLoading}
              required
            />
          </div>

          <Button
            type="submit"
            disabled={isLoading}
            className="w-full h-11 rounded-xl font-semibold bg-primary hover:bg-[#0c5f59] hover:shadow-[0_8px_20px_-4px_rgba(15,118,110,0.4)] hover:-translate-y-0.5 active:translate-y-0 active:shadow-sm transition-all duration-200 ease-out disabled:pointer-events-none disabled:opacity-50 disabled:transform-none disabled:shadow-none motion-reduce:transform-none motion-reduce:transition-none cursor-pointer mt-2"
          >
            {isLoading ? "Signing in..." : "Sign in"}
          </Button>

          {isGoogleLoginEnabled && (
            <div className="pt-1">
              <AuthDivider text="or" />
              <SocialLoginButton
                onClick={handleGoogleSignIn}
                disabled={isLoading}
                label="Sign in with Google"
              />
            </div>
          )}

          {isCustomerContext && (
            <div className="pt-3 border-t border-border/60 text-center text-xs text-muted-foreground">
              Don&apos;t have an account?{" "}
              <Link
                href="/signup/customer"
                className="font-semibold text-primary underline-offset-4 hover:underline"
              >
                Sign up
              </Link>
            </div>
          )}
        </form>
      </AuthFormContainer>
    </AuthLayout>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="flex-1 flex items-center justify-center min-h-screen">
          <div className="h-96 w-full max-w-md animate-pulse rounded-2xl bg-card border border-border" />
        </div>
      }
    >
      <LoginForm />
    </Suspense>
  );
}
