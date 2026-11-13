import React from "react";
import { cn } from "cn";

export interface AuthDividerProps {
  text?: string;
  className?: string;
}

/**
 * Reusable visual separator between social authentication and standard credential inputs.
 */
export function AuthDivider({ text = "or", className }: AuthDividerProps) {
  return (
    <div className={cn("relative flex items-center justify-center my-4 text-xs uppercase text-muted-foreground", className)}>
      <span className="w-full border-t border-border" aria-hidden="true" />
      <span className="bg-card px-3 text-[11px] font-medium tracking-wider text-muted-foreground uppercase shrink-0">
        {text}
      </span>
      <span className="w-full border-t border-border" aria-hidden="true" />
    </div>
  );
}

