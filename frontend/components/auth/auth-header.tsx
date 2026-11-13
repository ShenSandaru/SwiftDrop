import React from "react";
import { cn } from "cn";

export interface AuthHeaderProps {
  badge?: string;
  title: string;
  description?: string;
  className?: string;
}

/**
 * Reusable header for authentication forms.
 * Displays an optional brand badge, prominent heading, and descriptive subtitle.
 */
export function AuthHeader({ badge, title, description, className }: AuthHeaderProps) {
  return (
    <div className={cn("space-y-2 text-left", className)}>
      {badge && (
        <div className="inline-flex items-center gap-1.5 rounded-full border border-primary/20 bg-accent px-3 py-0.5 text-xs font-semibold uppercase tracking-wider text-primary">
          <span className="size-1.5 rounded-full bg-primary" aria-hidden="true" />
          {badge}
        </div>
      )}
      <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
        {title}
      </h1>
      {description && (
        <p className="text-sm text-muted-foreground leading-relaxed">
          {description}
        </p>
      )}
    </div>
  );
}

