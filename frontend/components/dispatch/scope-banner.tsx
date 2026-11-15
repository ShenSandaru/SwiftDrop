"use client";

import { useEffect, useState } from "react";
import { AlertTriangle, Globe, Warehouse } from "lucide-react";
import { getDispatchScope, type DispatchScope } from "@/lib/dispatch";

/**
 * Says where the signed-in dispatcher works (ADR 0012): every hub, or named hubs. A dispatcher with
 * no hub sees nothing, so the banner says why and who to ask, instead of leaving an empty portal
 * unexplained. The backend decides the scope; this only shows it.
 */
export function ScopeBanner() {
  const [scope, setScope] = useState<DispatchScope | null>(null);

  useEffect(() => {
    let cancelled = false;
    getDispatchScope()
      .then((loaded) => {
        if (!cancelled) setScope(loaded);
      })
      // The banner is a courtesy: without it the portal still works
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  if (!scope) {
    return null;
  }
  if (scope.everywhere) {
    return (
      <p className="flex items-center gap-2 rounded-xl border border-border bg-card px-4 py-2.5 text-xs text-muted-foreground shadow-xs">
        <Globe className="size-4 text-primary" aria-hidden="true" />
        You work across every hub, so you see all parcels.
      </p>
    );
  }
  if (scope.hubs.length === 0) {
    return (
      <p role="status" className="flex items-start gap-2 rounded-xl border border-amber-500/40 bg-amber-500/10 px-4 py-3 text-sm text-amber-900 dark:text-amber-100">
        <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
        <span>
          <span className="font-semibold">You are not assigned to a hub yet.</span>{" "}
          Parcels appear here once an administrator gives you a hub.
        </span>
      </p>
    );
  }
  return (
    <p className="flex flex-wrap items-center gap-2 rounded-xl border border-border bg-card px-4 py-2.5 text-xs text-muted-foreground shadow-xs">
      <Warehouse className="size-4 text-primary" aria-hidden="true" />
      <span>You work for</span>
      {scope.hubs.map((hub) => (
        <span key={hub.id} className="rounded-full bg-primary/10 px-2.5 py-0.5 font-semibold text-primary">
          {hub.name}
        </span>
      ))}
      <span>and see the parcels at, or heading to, those hubs.</span>
    </p>
  );
}
