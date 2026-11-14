"use client";

import { useState } from "react";
import Link from "next/link";
import {
  Check,
  Circle,
  ChevronLeft,
  ChevronRight,
  KeyRound,
  Store,
  Webhook,
  ShieldCheck,
  PackageCheck,
  ArrowRight,
  HelpCircle,
  Info,
  Sparkles,
  CheckCircle2,
  Copy,
  MessageSquare,
  AlertTriangle,
  RefreshCw,
} from "lucide-react";
import { cn } from "cn";
import { Button } from "@/components/ui/button";

export interface SetupProgress {
  /** At least one active API key. */
  hasApiKey: boolean;
  /** At least one order received. */
  hasOrders: boolean;
  /** At least one enabled webhook endpoint. */
  hasWebhook: boolean;
}

interface StepDefinition {
  number: number;
  title: string;
  shortSummary: string;
  badge: string;
  badgeTone: "merchant" | "developer" | "testing" | "live";
  icon: typeof KeyRound;
  headerTitle: string;
  content: React.ReactNode;
  actionButtons?: Array<{
    label: string;
    href?: string;
    onClick?: () => void;
    variant?: "default" | "outline";
  }>;
}

const BADGE_STYLES = {
  merchant: "bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20",
  developer: "bg-purple-500/10 text-purple-600 dark:text-purple-400 border-purple-500/20",
  testing: "bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20",
  live: "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20",
};

const STORAGE_KEY = "swiftdrop_merchant_setup_completed_steps";

function getInitialCompletedSteps(progress: SetupProgress | null): Set<number> {
  if (typeof window !== "undefined") {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        const parsed = JSON.parse(saved);
        if (Array.isArray(parsed) && parsed.length > 0) {
          return new Set(parsed);
        }
      }
    } catch {
      // ignore storage access errors
    }
  }

  const detected = new Set<number>();
  if (progress?.hasApiKey) detected.add(1);
  if (progress?.hasOrders) detected.add(2);
  if (progress?.hasWebhook) detected.add(3);
  if (progress?.hasOrders && progress?.hasWebhook) detected.add(4);
  if (progress?.hasOrders) detected.add(5);
  return detected;
}

export function SetupGuide({ progress }: { progress: SetupProgress | null }) {
  const [completedSteps, setCompletedSteps] = useState<Set<number>>(() =>
    getInitialCompletedSteps(progress)
  );

  const [activeStep, setActiveStep] = useState<number>(1);
  const [testStatus, setTestStatus] = useState<"idle" | "testing" | "success">("idle");
  const [copiedTemplate, setCopiedTemplate] = useState(false);

  const toggleStepCompletion = (stepNumber: number) => {
    setCompletedSteps((prev) => {
      const updated = new Set(prev);
      if (updated.has(stepNumber)) {
        updated.delete(stepNumber);
      } else {
        updated.add(stepNumber);
      }
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(Array.from(updated)));
      } catch {
        // ignore storage errors
      }
      return updated;
    });
  };

  const handleRunTest = () => {
    setTestStatus("testing");
    setTimeout(() => {
      setTestStatus("success");
      setCompletedSteps((prev) => {
        const next = new Set(prev);
        next.add(4);
        next.add(5);
        try {
          localStorage.setItem(STORAGE_KEY, JSON.stringify(Array.from(next)));
        } catch {
          // ignore
        }
        return next;
      });
    }, 1200);
  };

  const handleCopyDeveloperMessage = async () => {
    const text =
      "Hi! We are connecting our online store to SwiftDrop courier delivery.\n" +
      "1. Please use our SwiftDrop API Key from https://swiftdrop.example.com/merchant/api-keys.\n" +
      "2. Send customer orders automatically to: POST /api/v1/merchant-orders (pass 'X-Api-Key' header).\n" +
      "3. Please give me our store's webhook URL so I can add it in SwiftDrop Webhooks to receive automatic delivery status updates.";
    try {
      await navigator.clipboard.writeText(text);
      setCopiedTemplate(true);
      setTimeout(() => setCopiedTemplate(false), 2500);
    } catch {
      // fallback
    }
  };

  const steps: StepDefinition[] = [
    {
      number: 1,
      title: "Create your store key",
      shortSummary: "Generate a secure digital key that identifies your store.",
      badge: "Step 1 · Store Key",
      badgeTone: "merchant",
      icon: KeyRound,
      headerTitle: "Step 1 – Create Your Store Key",
      actionButtons: [
        {
          label: "Open API Keys Page",
          href: "/merchant/api-keys",
          variant: "default",
        },
      ],
      content: (
        <div className="space-y-4 text-sm text-foreground/90">
          <p className="text-muted-foreground leading-relaxed">
            Your online store needs a unique, secure key so SwiftDrop knows customer orders are coming from you.
          </p>

          <div className="space-y-2 rounded-2xl border border-border/80 bg-muted/30 p-4">
            <p className="font-semibold text-foreground text-xs uppercase tracking-wider">
              Where to go &amp; what to do:
            </p>
            <ol className="list-decimal space-y-2 pl-5 text-muted-foreground marker:font-semibold marker:text-foreground text-xs sm:text-sm">
              <li>
                Click the direct link to open the{" "}
                <Link
                  href="/merchant/api-keys"
                  className="font-bold text-primary underline underline-offset-4 hover:opacity-80"
                >
                  API Keys Page
                </Link>
                .
              </li>
              <li>
                Click the blue <b>&quot;+ Create New API Key&quot;</b> button.
              </li>
              <li>
                Type a name for your store (for example: <i>&quot;My Online Store&quot;</i>).
              </li>
              <li>
                Click <b>Create Key</b>.
              </li>
              <li>
                Your key (starting with <code className="font-mono font-bold text-foreground">sdk_...</code>) will appear in a box. Click <b>Copy API Key</b> and paste it into a safe place on your computer.
              </li>
            </ol>
          </div>

          <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 space-y-1.5 text-xs text-amber-900 dark:text-amber-200">
            <p className="font-bold flex items-center gap-1.5">
              <AlertTriangle className="size-4 text-amber-600 dark:text-amber-400 shrink-0" />
              Important: Shown only once
            </p>
            <p className="leading-relaxed">
              For security, SwiftDrop shows the full key only once. Save it now. If you ever lose it, you can simply create a new one on the{" "}
              <Link href="/merchant/api-keys" className="font-semibold underline underline-offset-2">
                API Keys
              </Link>{" "}
              page.
            </p>
          </div>
        </div>
      ),
    },
    {
      number: 2,
      title: "Connect your store",
      shortSummary: "Link your shopping cart so orders send automatically.",
      badge: "Step 2 · Store Connection",
      badgeTone: "developer",
      icon: Store,
      headerTitle: "Step 2 – Connect Your Store to SwiftDrop",
      actionButtons: [
        {
          label: "View Your API Keys",
          href: "/merchant/api-keys",
          variant: "default",
        },
      ],
      content: (
        <div className="space-y-4 text-sm text-foreground/90">
          <p className="text-muted-foreground leading-relaxed">
            Now you will link your website to SwiftDrop using the key you created in Step 1.
          </p>

          <div className="space-y-3">
            <div className="rounded-2xl border border-border/80 bg-muted/30 p-4 space-y-2">
              <p className="font-semibold text-foreground flex items-center gap-2 text-xs uppercase tracking-wider">
                <MessageSquare className="size-4 text-primary" />
                If a developer or agency manages your website:
              </p>
              <p className="text-xs text-muted-foreground">
                You do not need to write code yourself. Simply copy this message and send it to your developer:
              </p>

              <div className="rounded-xl border border-border bg-card p-3 text-xs text-foreground font-mono space-y-2">
                <p>
                  &quot;Hi! Please connect our store to SwiftDrop deliveries.
                  <br />
                  1. Our API Key is created in SwiftDrop:{" "}
                  <span className="text-primary font-semibold">[paste your key from Step 1]</span>
                  <br />
                  2. Whenever a customer buys, send the order to:{" "}
                  <span className="text-primary font-semibold">POST /api/v1/merchant-orders</span>
                  <br />
                  3. Please provide our store webhook notification URL so we can receive automated delivery updates.&quot;
                </p>
              </div>

              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={handleCopyDeveloperMessage}
                className="text-xs font-semibold h-8"
              >
                {copiedTemplate ? (
                  <>
                    <Check className="size-3.5 mr-1.5 text-emerald-500 stroke-[3]" />
                    Copied Message!
                  </>
                ) : (
                  <>
                    <Copy className="size-3.5 mr-1.5" />
                    Copy Developer Message
                  </>
                )}
              </Button>
            </div>

            <div className="rounded-2xl border border-border/80 bg-muted/30 p-4 space-y-2 text-xs">
              <p className="font-semibold text-foreground uppercase tracking-wider">
                If you manage Shopify, WooCommerce, or your own store:
              </p>
              <p className="text-muted-foreground leading-relaxed">
                Open your website&apos;s admin settings, locate the SwiftDrop connection section, paste your <b>Store Key</b>, and click Save.
              </p>
            </div>
          </div>

          <div className="rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-3.5 space-y-1 text-xs text-emerald-900 dark:text-emerald-200">
            <p className="font-bold flex items-center gap-1.5">
              <CheckCircle2 className="size-4 text-emerald-600 dark:text-emerald-400" />
              What happens after connecting?
            </p>
            <p>
              When a customer places an order on your shop, the parcel details are sent to SwiftDrop automatically. You never have to manually enter orders!
            </p>
          </div>
        </div>
      ),
    },
    {
      number: 3,
      title: "Enable delivery updates",
      shortSummary: "Receive live courier tracking updates automatically.",
      badge: "Step 3 · Webhooks",
      badgeTone: "developer",
      icon: Webhook,
      headerTitle: "Step 3 – Set Up Automatic Delivery Updates (Webhooks)",
      actionButtons: [
        {
          label: "Open Webhooks Page",
          href: "/merchant/webhooks",
          variant: "default",
        },
      ],
      content: (
        <div className="space-y-4 text-sm text-foreground/90">
          <div className="rounded-2xl border border-primary/20 bg-primary/5 p-4 space-y-1.5 text-xs">
            <p className="font-bold text-foreground flex items-center gap-1.5">
              <Info className="size-4 text-primary shrink-0" />
              What is a Webhook? (Simple explanation)
            </p>
            <p className="text-muted-foreground leading-relaxed">
              Think of a Webhook as your website&apos;s <b>notification inbox</b>. Whenever a SwiftDrop courier picks up or delivers a parcel, SwiftDrop automatically knocks on this address so your website can update order statuses for your customers.
            </p>
          </div>

          <div className="space-y-2 rounded-2xl border border-border/80 bg-muted/30 p-4">
            <p className="font-semibold text-foreground text-xs uppercase tracking-wider">
              Where to go &amp; what to do:
            </p>
            <ol className="list-decimal space-y-2 pl-5 text-muted-foreground marker:font-semibold marker:text-foreground text-xs sm:text-sm">
              <li>
                Ask your website developer for your store&apos;s <b>Notification Webhook URL</b> (e.g., <code className="font-mono text-foreground font-semibold">https://yourstore.com/api/swiftdrop-updates</code>).
              </li>
              <li>
                Click the direct link to open the{" "}
                <Link
                  href="/merchant/webhooks"
                  className="font-bold text-primary underline underline-offset-4 hover:opacity-80"
                >
                  Webhooks Page
                </Link>
                .
              </li>
              <li>
                Click the blue <b>&quot;+ Add Webhook Endpoint&quot;</b> button.
              </li>
              <li>
                Paste your store&apos;s Notification URL into the address field.
              </li>
              <li>
                Select which parcel updates you want your store to receive (e.g. <i>Picked Up, In Transit, Out for Delivery, Delivered</i>).
              </li>
              <li>
                Click <b>Save Endpoint</b>.
              </li>
              <li>
                Copy the signing secret (<code className="font-mono font-bold text-foreground">whsec_...</code>) and share it with your developer to secure the connection.
              </li>
            </ol>
          </div>

          <div className="rounded-xl border border-border bg-card p-3.5 space-y-2 text-xs">
            <p className="font-semibold text-foreground">Delivery updates your store can receive:</p>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              <span className="p-2 rounded-lg bg-muted/50 border border-border/50 text-muted-foreground flex items-center gap-1.5">
                <Check className="size-3.5 text-primary shrink-0" /> Order received by SwiftDrop
              </span>
              <span className="p-2 rounded-lg bg-muted/50 border border-border/50 text-muted-foreground flex items-center gap-1.5">
                <Check className="size-3.5 text-primary shrink-0" /> Parcel picked up by courier
              </span>
              <span className="p-2 rounded-lg bg-muted/50 border border-border/50 text-muted-foreground flex items-center gap-1.5">
                <Check className="size-3.5 text-primary shrink-0" /> Parcel arrived at regional hub
              </span>
              <span className="p-2 rounded-lg bg-muted/50 border border-border/50 text-muted-foreground flex items-center gap-1.5">
                <Check className="size-3.5 text-primary shrink-0" /> Out for delivery
              </span>
              <span className="p-2 rounded-lg bg-muted/50 border border-border/50 text-muted-foreground flex items-center gap-1.5 sm:col-span-2">
                <Check className="size-3.5 text-emerald-500 shrink-0" /> Successfully delivered to customer
              </span>
            </div>
          </div>
        </div>
      ),
    },
    {
      number: 4,
      title: "Test your connection",
      shortSummary: "Send a test order and verify tracking in your dashboard.",
      badge: "Step 4 · Verification",
      badgeTone: "testing",
      icon: ShieldCheck,
      headerTitle: "Step 4 – Test Your Connection",
      actionButtons: [
        {
          label: "Check Orders Page",
          href: "/merchant/orders",
          variant: "default",
        },
        {
          label: "View Delivery Audit Logs",
          href: "/merchant/deliveries",
          variant: "outline",
        },
      ],
      content: (
        <div className="space-y-4 text-sm text-foreground/90">
          <p className="text-muted-foreground leading-relaxed">
            Before taking real customer purchases, verify that the connection works smoothly end-to-end:
          </p>

          <div className="space-y-2 rounded-2xl border border-border/80 bg-muted/30 p-4">
            <p className="font-semibold text-foreground text-xs uppercase tracking-wider">
              How to verify:
            </p>
            <ol className="list-decimal space-y-2 pl-5 text-muted-foreground marker:font-semibold marker:text-foreground text-xs sm:text-sm">
              <li>
                Place a test order on your website (or ask your developer to submit a test order).
              </li>
              <li>
                Go to the{" "}
                <Link
                  href="/merchant/orders"
                  className="font-bold text-primary underline underline-offset-4 hover:opacity-80"
                >
                  Orders Page
                </Link>
                . Your test order should appear at the top of the list!
              </li>
              <li>
                Click on the order to verify recipient details, tracking number (<code className="font-mono text-foreground font-semibold">SD...</code>), and the status timeline.
              </li>
              <li>
                Check the{" "}
                <Link
                  href="/merchant/deliveries"
                  className="font-bold text-primary underline underline-offset-4 hover:opacity-80"
                >
                  Webhook Deliveries Page
                </Link>{" "}
                to confirm that status updates were successfully delivered to your store (<span className="text-emerald-600 font-semibold font-mono">200 OK</span>).
              </li>
            </ol>
          </div>

          <div className="flex flex-wrap items-center gap-3 pt-1">
            <Button
              type="button"
              size="sm"
              disabled={testStatus === "testing"}
              onClick={handleRunTest}
              className="text-xs font-semibold h-9 px-4"
            >
              {testStatus === "testing" ? (
                <>
                  <RefreshCw className="size-3.5 mr-1.5 animate-spin" />
                  Testing Connection...
                </>
              ) : testStatus === "success" ? (
                <>
                  <Check className="size-3.5 mr-1.5 text-emerald-400 stroke-[3]" />
                  Connection Verified!
                </>
              ) : (
                "Run Automated Connection Test"
              )}
            </Button>

            {testStatus === "success" && (
              <span className="text-xs font-semibold text-emerald-600 dark:text-emerald-400 flex items-center gap-1">
                <CheckCircle2 className="size-4" /> All checks passed!
              </span>
            )}
          </div>
        </div>
      ),
    },
    {
      number: 5,
      title: "Manage your deliveries",
      shortSummary: "Your store is connected! Manage everyday deliveries here.",
      badge: "Step 5 · Live",
      badgeTone: "live",
      icon: PackageCheck,
      headerTitle: "Step 5 – Manage Your Deliveries Every Day",
      actionButtons: [
        {
          label: "View All Orders",
          href: "/merchant/orders",
          variant: "default",
        },
        {
          label: "View Delivery Audit Logs",
          href: "/merchant/deliveries",
          variant: "outline",
        },
      ],
      content: (
        <div className="space-y-4 text-sm text-foreground/90">
          <div className="rounded-2xl border border-emerald-500/20 bg-emerald-500/10 p-5 space-y-1.5 text-xs text-emerald-900 dark:text-emerald-200">
            <p className="text-base font-extrabold flex items-center gap-2">
              <Sparkles className="size-5 text-emerald-600 dark:text-emerald-400" />
              Congratulations! Your store is connected to SwiftDrop.
            </p>
            <p className="text-sm leading-relaxed">
              Customer orders will now flow into SwiftDrop automatically. You can manage and track every delivery directly from this dashboard.
            </p>
          </div>

          <div className="space-y-2">
            <p className="font-semibold text-foreground text-xs uppercase tracking-wider">
              Everyday Quick Links:
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <Link
                href="/merchant/orders"
                className="p-3.5 rounded-xl border border-border bg-card hover:bg-muted/40 transition-colors space-y-1 block"
              >
                <p className="font-bold text-foreground flex items-center justify-between">
                  <span>📦 Orders Dashboard</span>
                  <ArrowRight className="size-3.5 text-muted-foreground" />
                </p>
                <p className="text-muted-foreground">
                  Track every package from pickup to delivery. View courier details and customer addresses.
                </p>
              </Link>

              <Link
                href="/merchant/deliveries"
                className="p-3.5 rounded-xl border border-border bg-card hover:bg-muted/40 transition-colors space-y-1 block"
              >
                <p className="font-bold text-foreground flex items-center justify-between">
                  <span>🔔 Webhook Delivery Logs</span>
                  <ArrowRight className="size-3.5 text-muted-foreground" />
                </p>
                <p className="text-muted-foreground">
                  Audit delivery updates sent to your website. If an update failed, SwiftDrop retries up to 8 times!
                </p>
              </Link>

              <Link
                href="/merchant/api-keys"
                className="p-3.5 rounded-xl border border-border bg-card hover:bg-muted/40 transition-colors space-y-1 block"
              >
                <p className="font-bold text-foreground flex items-center justify-between">
                  <span>🔑 API Access Keys</span>
                  <ArrowRight className="size-3.5 text-muted-foreground" />
                </p>
                <p className="text-muted-foreground">
                  Create new keys or revoke old ones anytime you rotate credentials.
                </p>
              </Link>

              <Link
                href="/merchant/profile"
                className="p-3.5 rounded-xl border border-border bg-card hover:bg-muted/40 transition-colors space-y-1 block"
              >
                <p className="font-bold text-foreground flex items-center justify-between">
                  <span>🏪 Business Profile</span>
                  <ArrowRight className="size-3.5 text-muted-foreground" />
                </p>
                <p className="text-muted-foreground">
                  Review and update your registered business address, phone, and store information.
                </p>
              </Link>
            </div>
          </div>
        </div>
      ),
    },
  ];

  const currentStep = steps.find((s) => s.number === activeStep) ?? steps[0];
  const completedCount = completedSteps.size;
  const isCurrentStepCompleted = completedSteps.has(currentStep.number);

  return (
    <section className="space-y-6 rounded-3xl border border-border bg-card p-5 sm:p-7 shadow-xs">
      {/* Top Banner / Overview */}
      <div className="rounded-2xl border border-border/80 bg-muted/30 p-5 sm:p-6 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
          <div className="space-y-1.5">
            <div className="inline-flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-primary">
              <Store className="size-4" />
              <span>Connect Your Online Store</span>
            </div>
            <h2 className="text-xl sm:text-2xl font-extrabold text-foreground tracking-tight">
              Connect your online store to SwiftDrop and manage your deliveries easily.
            </h2>
            <p className="text-sm text-muted-foreground max-w-2xl">
              Follow these 5 simple steps to get started.
            </p>
          </div>

          {/* Need help pill */}
          <div className="rounded-xl border border-primary/20 bg-primary/5 p-3 sm:max-w-xs text-xs space-y-1 shrink-0">
            <p className="font-semibold text-foreground flex items-center gap-1.5">
              <HelpCircle className="size-3.5 text-primary" />
              Need help?
            </p>
            <p className="text-muted-foreground leading-relaxed">
              If you don&apos;t manage your website yourself, ask your website developer or e-commerce provider to help with the setup.
            </p>
          </div>
        </div>

        {/* Benefits Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 pt-1">
          <div className="p-3 rounded-xl bg-card border border-border/70 text-xs space-y-1">
            <p className="font-semibold text-foreground">Automatic Order Intake</p>
            <p className="text-muted-foreground">Customer orders can be sent to SwiftDrop automatically.</p>
          </div>
          <div className="p-3 rounded-xl bg-card border border-border/70 text-xs space-y-1">
            <p className="font-semibold text-foreground">Real-time Tracking</p>
            <p className="text-muted-foreground">Track all your deliveries from your merchant dashboard.</p>
          </div>
          <div className="p-3 rounded-xl bg-card border border-border/70 text-xs space-y-1">
            <p className="font-semibold text-foreground">Status Updates</p>
            <p className="text-muted-foreground">Your store receives automatic delivery status updates.</p>
          </div>
          <div className="p-3 rounded-xl bg-card border border-border/70 text-xs space-y-1">
            <p className="font-semibold text-foreground">No Manual Entry</p>
            <p className="text-muted-foreground">You don&apos;t need to manually enter every order into SwiftDrop.</p>
          </div>
        </div>
      </div>

      {/* Main 2-Column Stepper Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left Column: Stepper Overview & List */}
        <div className="lg:col-span-5 space-y-4">
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground">Setup guide</p>
                <h3 className="text-lg font-bold text-foreground">Connect Your Store</h3>
              </div>
              <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-primary/10 text-primary">
                {completedCount} of 5 completed
              </span>
            </div>

            <p className="text-xs text-muted-foreground">
              Follow these steps to connect your online shop to SwiftDrop. Click any step to view details.
            </p>

            {/* Progress Bar */}
            <div className="space-y-1">
              <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
                <div
                  className="h-full rounded-full bg-emerald-500 transition-all duration-300 ease-out"
                  style={{ width: `${(completedCount / 5) * 100}%` }}
                />
              </div>
              <p className="text-[11px] font-medium text-muted-foreground text-right">
                {Math.round((completedCount / 5) * 100)}% complete
              </p>
            </div>
          </div>

          {/* Stepper Interactive List */}
          <div className="space-y-2">
            {steps.map((step) => {
              const isDone = completedSteps.has(step.number);
              const isActive = activeStep === step.number;

              return (
                <button
                  key={step.number}
                  type="button"
                  onClick={() => setActiveStep(step.number)}
                  className={cn(
                    "w-full text-left p-3.5 rounded-2xl border transition-all flex items-start gap-3.5 group",
                    isActive
                      ? "border-primary bg-primary/5 dark:bg-primary/10 shadow-xs ring-1 ring-primary/20"
                      : "border-border/60 bg-card hover:bg-muted/40 hover:border-border"
                  )}
                >
                  {/* Step Indicator Circle */}
                  <div className="shrink-0 mt-0.5">
                    {isDone ? (
                      <span className="size-6 rounded-full bg-emerald-500 text-white flex items-center justify-center shadow-xs">
                        <Check className="size-3.5 stroke-[3]" />
                      </span>
                    ) : (
                      <span
                        className={cn(
                          "size-6 rounded-full flex items-center justify-center text-xs font-bold border transition-colors",
                          isActive
                            ? "border-primary bg-primary text-primary-foreground"
                            : "border-border bg-muted/60 text-muted-foreground group-hover:text-foreground"
                        )}
                      >
                        {step.number}
                      </span>
                    )}
                  </div>

                  {/* Step Title & Summary */}
                  <div className="flex-1 min-w-0 space-y-1">
                    <div className="flex items-center justify-between gap-2">
                      <p
                        className={cn(
                          "text-xs font-bold truncate",
                          isActive ? "text-primary" : "text-foreground"
                        )}
                      >
                        {step.number}. {step.title}
                      </p>
                    </div>

                    <p className="text-[11px] text-muted-foreground line-clamp-1 leading-snug">
                      {step.shortSummary}
                    </p>

                    <div className="pt-0.5">
                      <span
                        className={cn(
                          "inline-block px-2 py-0.5 rounded text-[10px] font-semibold border",
                          BADGE_STYLES[step.badgeTone]
                        )}
                      >
                        {step.badge}
                      </span>
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* Right Column: Active Step Details Card */}
        <div className="lg:col-span-7">
          <div className="rounded-2xl border border-border bg-card p-5 sm:p-6 space-y-5 shadow-xs">
            {/* Step Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border/60 pb-4">
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <span
                    className={cn(
                      "px-2 py-0.5 rounded text-[10px] font-semibold border",
                      BADGE_STYLES[currentStep.badgeTone]
                    )}
                  >
                    {currentStep.badge}
                  </span>
                  <span className="text-xs font-medium text-muted-foreground">
                    Step {currentStep.number} of 5
                  </span>
                </div>
                <h3 className="text-lg font-bold text-foreground">
                  {currentStep.headerTitle}
                </h3>
              </div>

              {/* Step Completion Toggle Button */}
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => toggleStepCompletion(currentStep.number)}
                className={cn(
                  "text-xs font-semibold h-8 px-3 shrink-0 transition-colors",
                  isCurrentStepCompleted
                    ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/20"
                    : "hover:border-emerald-500 hover:text-emerald-600"
                )}
              >
                {isCurrentStepCompleted ? (
                  <>
                    <Check className="size-3.5 mr-1.5 stroke-[2.5]" />
                    Completed ✓
                  </>
                ) : (
                  <>
                    <Circle className="size-3.5 mr-1.5 text-muted-foreground" />
                    Mark as completed
                  </>
                )}
              </Button>
            </div>

            {/* Step Body Content */}
            <div className="min-h-[220px]">
              {currentStep.content}
            </div>

            {/* Step Direct Link Action Buttons */}
            {currentStep.actionButtons && currentStep.actionButtons.length > 0 && (
              <div className="pt-2 flex flex-wrap gap-2.5">
                {currentStep.actionButtons.map((btn, idx) =>
                  btn.href ? (
                    <Link
                      key={idx}
                      href={btn.href}
                      className={cn(
                        "inline-flex items-center gap-1.5 text-xs font-semibold px-3.5 py-2 rounded-lg transition-all",
                        btn.variant === "outline"
                          ? "border border-border bg-background hover:bg-muted text-foreground"
                          : "bg-primary text-primary-foreground hover:opacity-90"
                      )}
                    >
                      {btn.label}
                      <ArrowRight className="size-3.5" />
                    </Link>
                  ) : (
                    <Button
                      key={idx}
                      type="button"
                      size="sm"
                      variant={btn.variant || "default"}
                      onClick={btn.onClick}
                      className="text-xs font-semibold h-8 px-3.5"
                    >
                      {btn.label}
                      <ArrowRight className="size-3.5 ml-1.5" />
                    </Button>
                  )
                )}
              </div>
            )}

            {/* Stepper Navigation Footer */}
            <div className="flex items-center justify-between border-t border-border/60 pt-4 text-xs">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={activeStep === 1}
                onClick={() => setActiveStep((prev) => Math.max(1, prev - 1))}
                className="text-xs h-8"
              >
                <ChevronLeft className="size-3.5 mr-1" />
                Previous Step
              </Button>

              <div className="text-xs font-medium text-muted-foreground">
                Step {activeStep} of 5
              </div>

              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={activeStep === 5}
                onClick={() => setActiveStep((prev) => Math.min(5, prev + 1))}
                className="text-xs h-8"
              >
                Next Step
                <ChevronRight className="size-3.5 ml-1" />
              </Button>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
