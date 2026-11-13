import Link from "next/link";
import type { Metadata } from "next";
import { Truck, Warehouse, ShieldCheck, ArrowRight, Lock } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "cn";

export const metadata: Metadata = {
  title: "Staff Operations Portal · SwiftDrop",
  description: "Secure workspace sign-in for SwiftDrop couriers, dispatchers, and system administrators.",
  robots: { index: false },
};

interface StaffPortalCard {
  id: string;
  role: string;
  title: string;
  description: string;
  ctaText: string;
  href: string;
  icon: typeof Truck;
  tagColor: string;
}

const STAFF_PORTALS: StaffPortalCard[] = [
  {
    id: "courier",
    role: "COURIER WORKSPACE",
    title: "Courier Portal",
    description: "Access your assigned deliveries, manage delivery progress, and update parcel statuses.",
    ctaText: "Courier Login",
    href: "/login?redirect=/courier",
    icon: Truck,
    tagColor: "border-primary/20 bg-accent text-primary",
  },
  {
    id: "dispatcher",
    role: "DISPATCH WORKSPACE",
    title: "Dispatcher Portal",
    description: "Coordinate delivery operations, manage parcel assignments, and monitor courier activities.",
    ctaText: "Dispatcher Login",
    href: "/login?redirect=/dispatch",
    icon: Warehouse,
    tagColor: "border-primary/20 bg-accent text-primary",
  },
  {
    id: "admin",
    role: "SYSTEM ADMIN",
    title: "Admin Portal",
    description: "Manage users, merchants, delivery operations, and SwiftDrop platform settings.",
    ctaText: "Admin Login",
    href: "/login?redirect=/admin",
    icon: ShieldCheck,
    tagColor: "border-primary/20 bg-accent text-primary",
  },
];

/**
 * Dedicated SwiftDrop Staff Portal selection page.
 * Allows authorized Couriers, Dispatchers, and Administrators to select their operational
 * workspace and navigate to the corresponding role-specific login view.
 */
export default function PortalsPage() {
  return (
    <main className="container mx-auto flex-1 px-4 py-12 md:py-20 max-w-6xl space-y-12 sm:space-y-16">
      {/* Header section */}
      <section className="text-center space-y-4 max-w-2xl mx-auto">
        <div className="inline-flex items-center gap-1.5 rounded-full border border-primary/20 bg-accent px-3 py-1 text-xs font-semibold uppercase tracking-wider text-primary">
          <ShieldCheck className="size-3.5" aria-hidden="true" />
          Authorized Operations
        </div>
        <h1 className="text-3xl sm:text-4xl md:text-5xl font-extrabold tracking-tight text-foreground">
          SwiftDrop Staff Portal
        </h1>
        <p className="text-base sm:text-lg text-muted-foreground leading-relaxed">
          Welcome to the SwiftDrop Operations Portal. Select your workspace to securely access your account.
        </p>
      </section>

      {/* Staff Workspace Cards Grid */}
      <section className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 lg:gap-8" aria-label="Staff Workspaces">
        {STAFF_PORTALS.map((portal) => {
          const Icon = portal.icon;
          return (
            <Card
              key={portal.id}
              className="group flex flex-col justify-between rounded-2xl border border-border bg-card shadow-sm hover:border-primary/40 hover:shadow-lg hover:-translate-y-1 transition-all duration-300"
            >
              <CardHeader className="space-y-4 p-6 sm:p-7">
                <div className="flex items-center justify-between gap-3">
                  <div className="flex size-12 items-center justify-center rounded-xl bg-primary/10 text-primary group-hover:bg-primary group-hover:text-primary-foreground transition-colors duration-300">
                    <Icon className="size-6" aria-hidden="true" />
                  </div>
                  <span className={cn("text-[10px] font-bold px-2.5 py-1 rounded-full border uppercase tracking-wider", portal.tagColor)}>
                    {portal.role}
                  </span>
                </div>
                <div className="space-y-2">
                  <CardTitle className="text-xl font-bold tracking-tight text-foreground group-hover:text-primary transition-colors">
                    {portal.title}
                  </CardTitle>
                  <CardDescription className="text-sm text-muted-foreground leading-relaxed">
                    {portal.description}
                  </CardDescription>
                </div>
              </CardHeader>

              <CardFooter className="p-6 sm:p-7 pt-0">
                <Link
                  href={portal.href}
                  className={cn(
                    buttonVariants({ variant: "outline", size: "default" }),
                    "w-full h-11 justify-between font-semibold border-border bg-background hover:bg-primary hover:text-primary-foreground hover:border-primary transition-all duration-200"
                  )}
                >
                  <span>{portal.ctaText}</span>
                  <ArrowRight className="size-4 transition-transform group-hover:translate-x-1" aria-hidden="true" />
                </Link>
              </CardFooter>
            </Card>
          );
        })}
      </section>

      {/* Security notice footer */}
      <section className="mx-auto max-w-xl text-center">
        <div className="inline-flex items-center gap-2 rounded-xl border border-border/80 bg-muted/50 px-4 py-3 text-xs text-muted-foreground">
          <Lock className="size-4 text-primary shrink-0" aria-hidden="true" />
          <span>
            This portal is restricted to authorized SwiftDrop logistics personnel. All login attempts and operations are audited.
          </span>
        </div>
      </section>
    </main>
  );
}
