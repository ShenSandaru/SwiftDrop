import React from "react";
import { cn } from "cn";

export interface AuthFormContainerProps {
  children: React.ReactNode;
  className?: string;
}

/**
 * Reusable presentation container for authentication forms.
 * Provides consistent elevation, rounded corners, padding, and maximum width.
 * Contains no business logic or state.
 */
export function AuthFormContainer({ children, className }: AuthFormContainerProps) {
  return (
    <div
      className={cn(
        "w-full rounded-2xl border border-border/80 bg-card p-5 sm:p-8 shadow-xs sm:shadow-sm space-y-6 transition-all",
        className
      )}
    >
      {children}
    </div>
  );
}

