import React from "react";
import Link from "next/link";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "cn";

export default function SignupChooserPage() {
  return (
    <main className="container mx-auto flex-1 flex flex-col items-center justify-center px-4 py-12 min-h-[calc(100vh-3.5rem)]">
      <div className="w-full max-w-3xl space-y-8">
        <div className="text-center space-y-2">
          <div className="inline-flex items-center justify-center gap-1.5 px-3 py-1 rounded-full bg-primary/10 text-xs font-semibold text-primary uppercase tracking-wider">
            <span className="size-1.5 rounded-full bg-primary" />
            Get Started with SwiftDrop
          </div>
          <h1 className="text-3xl font-extrabold tracking-tight text-foreground sm:text-4xl">
            Choose your account type
          </h1>
          <p className="text-sm sm:text-base text-muted-foreground max-w-lg mx-auto">
            Whether you are sending a single parcel or managing island-wide e-commerce deliveries, we have the right account for you.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Customer Card */}
          <Card className="rounded-xl border border-border bg-card shadow-xs hover:border-primary/50 transition-all flex flex-col justify-between">
            <CardHeader className="space-y-2">
              <div className="size-10 rounded-lg bg-primary/10 text-primary flex items-center justify-center font-bold text-lg">
                📦
              </div>
              <CardTitle className="text-xl font-bold tracking-tight text-foreground">
                Customer Account
              </CardTitle>
              <CardDescription className="text-sm text-muted-foreground leading-relaxed">
                Send personal parcels across the island. Calculate upfront fees, schedule pickups, and track packages in real time.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3 text-xs text-muted-foreground">
              <div className="flex items-center gap-2">
                <span className="text-emerald-500 font-bold">✓</span> Instant account activation
              </div>
              <div className="flex items-center gap-2">
                <span className="text-emerald-500 font-bold">✓</span> Real-time SSE parcel tracking
              </div>
              <div className="flex items-center gap-2">
                <span className="text-emerald-500 font-bold">✓</span> Downloadable digital receipts
              </div>
            </CardContent>
            <CardFooter className="pt-4 border-t border-border/50">
              <Link
                href="/signup/customer"
                className={cn(buttonVariants({ size: "default" }), "w-full font-semibold")}
              >
                Register as Customer &rarr;
              </Link>
            </CardFooter>
          </Card>

          {/* Merchant Card */}
          <Card className="rounded-xl border border-border bg-card shadow-xs hover:border-primary/50 transition-all flex flex-col justify-between relative overflow-hidden">
            <div className="absolute top-3 right-3">
              <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20">
                Requires Approval
              </span>
            </div>
            <CardHeader className="space-y-2">
              <div className="size-10 rounded-lg bg-primary/10 text-primary flex items-center justify-center font-bold text-lg">
                🏪
              </div>
              <CardTitle className="text-xl font-bold tracking-tight text-foreground">
                Merchant Business
              </CardTitle>
              <CardDescription className="text-sm text-muted-foreground leading-relaxed">
                For online stores, supermarkets, and businesses. Automate order fulfillment via APIs and receive delivery webhooks.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3 text-xs text-muted-foreground">
              <div className="flex items-center gap-2">
                <span className="text-emerald-500 font-bold">✓</span> Merchant dashboard & bulk logistics
              </div>
              <div className="flex items-center gap-2">
                <span className="text-emerald-500 font-bold">✓</span> HMAC-signed delivery webhooks
              </div>
              <div className="flex items-center gap-2">
                <span className="text-amber-600 dark:text-amber-400 font-bold">ℹ</span> Verified through admin approval review
              </div>
            </CardContent>
            <CardFooter className="pt-4 border-t border-border/50">
              <Link
                href="/signup/merchant"
                className={cn(
                  buttonVariants({ variant: "outline", size: "default" }),
                  "w-full font-semibold border-primary/40 hover:bg-primary/5"
                )}
              >
                Apply as Merchant &rarr;
              </Link>
            </CardFooter>
          </Card>
        </div>

        <div className="text-center text-sm text-muted-foreground pt-4">
          Already have an account?{" "}
          <Link href="/login" className="font-semibold text-primary underline-offset-4 hover:underline">
            Sign in
          </Link>
        </div>
      </div>
    </main>
  );
}
