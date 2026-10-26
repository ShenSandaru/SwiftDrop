import React from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Hammer, CheckCircle2, Server } from "lucide-react";

export interface PortalPlaceholderCardProps {
  title: string;
  description: string;
  workstream: string;
  contractRef?: string;
  plannedEndpoints?: string[];
  plannedFeatures?: string[];
}

/**
 * Standardized placeholder card identifying the target capabilities, contract references,
 * and planned REST endpoints for workstream teammates implementing future business features.
 */
export function PortalPlaceholderCard({
  title,
  description,
  workstream,
  contractRef,
  plannedEndpoints = [],
  plannedFeatures = [],
}: PortalPlaceholderCardProps) {
  return (
    <Card className="rounded-xl border-border bg-card shadow-xs">
      <CardHeader className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="inline-flex items-center gap-1.5 rounded-full border border-primary/20 bg-accent px-2.5 py-0.5 text-xs font-semibold text-primary">
            <Hammer className="size-3.5" aria-hidden="true" />
            <span>{workstream}</span>
          </div>

          {contractRef && (
            <span className="text-xs font-mono text-muted-foreground bg-muted px-2 py-0.5 rounded">
              Ref: {contractRef}
            </span>
          )}
        </div>

        <div>
          <CardTitle className="text-xl font-bold text-foreground">{title}</CardTitle>
          <CardDescription className="text-sm text-muted-foreground mt-1">
            {description}
          </CardDescription>
        </div>
      </CardHeader>

      <CardContent className="space-y-4 pt-0 text-sm">
        {plannedEndpoints.length > 0 && (
          <div className="space-y-2">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
              <Server className="size-3.5" aria-hidden="true" />
              Target Backend Endpoints
            </h2>
            <div className="flex flex-wrap gap-1.5">
              {plannedEndpoints.map((endpoint) => (
                <code
                  key={endpoint}
                  className="rounded bg-muted px-2 py-1 text-xs font-mono text-foreground border border-border"
                >
                  {endpoint}
                </code>
              ))}
            </div>
          </div>
        )}

        {plannedFeatures.length > 0 && (
          <div className="space-y-2">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
              <CheckCircle2 className="size-3.5" aria-hidden="true" />
              Planned Feature Scope
            </h2>
            <ul className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs text-muted-foreground">
              {plannedFeatures.map((feature) => (
                <li key={feature} className="flex items-start gap-2">
                  <span className="size-1.5 rounded-full bg-primary mt-1.5 shrink-0" aria-hidden="true" />
                  <span>{feature}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="rounded-lg bg-accent/40 border border-primary/10 p-3 text-xs text-muted-foreground">
          <p className="font-medium text-foreground">
            Scaffold Ready for Workstream Implementation
          </p>
          <p className="mt-0.5 text-[11px]">
            Portal shell, authentication context, and role guards are established. Teammates can implement page components
            and connect to the backend REST API without altering shell layouts.
          </p>
        </div>
      </CardContent>
    </Card>
  );
}
