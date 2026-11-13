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
        "w-full rounded-2xl border border-border/80 bg-card p-6 sm:p-8 shadow-[0_20px_50px_-12px_rgba(15,31,29,0.12),0_8px_24px_-8px_rgba(15,118,110,0.08)] space-y-6 transition-all",
        className
      )}
    >
      {children}
    </div>
  );
}

