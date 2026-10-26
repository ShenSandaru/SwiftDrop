import Link from "next/link";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const PORTALS = [
  {
    title: "Customer Portal",
    role: "CUSTOMER",
    description: "Book parcel pickups, manage delivery addresses, and view order history.",
    href: "/customer",
    action: "Access Customer Portal",
  },
  {
    title: "Merchant Portal",
    role: "MERCHANT",
    description: "Bulk shipment booking, cash on delivery (COD) settlements, and store integration.",
    href: "/merchant",
    action: "Access Merchant Portal",
  },
  {
    title: "Courier Portal",
    role: "COURIER",
    description: "Assigned delivery runs, optimized routes, and digital proof of delivery.",
    href: "/courier",
    action: "Access Courier Portal",
  },
  {
    title: "Dispatcher Portal",
    role: "DISPATCHER",
    description: "Fleet tracking, parcel assignment, route monitoring, and exception handling.",
    href: "/dispatch",
    action: "Access Dispatch Portal",
  },
  {
    title: "Admin Portal",
    role: "DISPATCHER_ADMIN",
    description: "User management, staff onboarding, delivery zone setup, and system configuration.",
    href: "/admin",
    action: "Access Admin Portal",
  },
];

export default function Home() {
  return (
    <main className="container mx-auto flex-1 px-4 py-12 md:py-16 space-y-12 max-w-6xl">
      {/* Hero section */}
      <section className="text-center space-y-4 max-w-3xl mx-auto">
        <div className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-accent px-3 py-1 text-xs font-semibold text-primary">
          <span className="size-1.5 rounded-full bg-primary" />
          Logistics Operations Platform
        </div>
        <h1 className="text-4xl font-extrabold tracking-tight sm:text-5xl text-foreground">
          Fast, Reliable Parcel Delivery & Logistics
        </h1>
        <p className="text-lg text-muted-foreground">
          SwiftDrop connects senders, merchants, couriers, and dispatchers on a unified real-time delivery platform.
        </p>

        {/* Quick Track Input */}
        <div className="pt-4 max-w-md mx-auto">
          <form
            action="/track"
            method="GET"
            className="flex flex-col sm:flex-row gap-2 p-1.5 rounded-xl bg-card border border-border shadow-xs"
          >
            <div className="flex-1">
              <Label htmlFor="hero-track-code" className="sr-only">
                Tracking Number
              </Label>
              <Input
                id="hero-track-code"
                name="code"
                placeholder="Enter tracking number (e.g. SWD-12345678)"
                aria-label="Tracking Number"
                className="border-0 bg-transparent shadow-none focus-visible:ring-0 text-sm"
              />
            </div>
            <Button type="submit" className="font-semibold shadow-xs">
              Track
            </Button>
          </form>
        </div>
      </section>

      {/* Role Portals Grid */}
      <section className="space-y-6">
        <div className="text-center space-y-1">
          <h2 className="text-2xl font-bold tracking-tight text-foreground">Portals & Workspaces</h2>
          <p className="text-sm text-muted-foreground">
            Role-specific workspaces for participants across the logistics lifecycle.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {PORTALS.map((portal) => (
            <Card
              key={portal.href}
              className="flex flex-col justify-between rounded-xl bg-card border-border shadow-xs hover:border-primary/40 hover:shadow-sm transition-all duration-200"
            >
              <CardHeader>
                <div className="flex items-center justify-between gap-2">
                  <CardTitle className="text-base font-bold text-foreground">{portal.title}</CardTitle>
                  <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-accent text-primary border border-primary/20 uppercase tracking-wider">
                    {portal.role}
                  </span>
                </div>
                <CardDescription className="text-sm text-muted-foreground">
                  {portal.description}
                </CardDescription>
              </CardHeader>
              <CardContent className="pt-0">
                <Link
                  href={portal.href}
                  className={
                    buttonVariants({ variant: "outline", size: "sm" }) +
                    " w-full font-medium hover:border-primary hover:text-primary transition-colors"
                  }
                >
                  {portal.action}
                </Link>
              </CardContent>
            </Card>
          ))}
          {/* Public Tracking Card */}
          <Card className="flex flex-col justify-between rounded-xl bg-card border-primary/20 shadow-xs hover:border-primary/40 hover:shadow-sm transition-all duration-200">
            <CardHeader>
              <div className="flex items-center justify-between gap-2">
                <CardTitle className="text-base font-bold text-foreground">Public Tracking</CardTitle>
                <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-secondary text-secondary-foreground uppercase tracking-wider">
                  PUBLIC
                </span>
              </div>
              <CardDescription className="text-sm text-muted-foreground">
                Track shipments in real time without creating an account or signing in.
              </CardDescription>
            </CardHeader>
            <CardContent className="pt-0">
              <Link
                href="/track"
                className={
                  buttonVariants({ variant: "default", size: "sm" }) +
                  " w-full font-semibold shadow-xs"
                }
              >
                Go to Tracking Page
              </Link>
            </CardContent>
          </Card>
        </div>
      </section>
    </main>
  );
}
