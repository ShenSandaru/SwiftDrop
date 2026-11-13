"use client";

import React, { useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import { Input } from "@/components/ui/input";
import { cn } from "cn";

export interface PasswordInputProps extends React.ComponentProps<"input"> {
  className?: string;
}

/**
 * Reusable password input with an accessible visibility toggle.
 * Preserves all underlying HTML input semantics, validations, and values.
 */
export function PasswordInput({ className, disabled, ...props }: PasswordInputProps) {
  const [showPassword, setShowPassword] = useState(false);

  return (
    <div className="relative">
      <Input
        type={showPassword ? "text" : "password"}
        disabled={disabled}
        className={cn(
          "h-11 pr-11 text-sm rounded-xl bg-background border-border/80 transition-all duration-200 hover:border-primary/50 focus:border-primary focus:ring-4 focus:ring-primary/15 focus-visible:border-primary focus-visible:ring-4 focus-visible:ring-primary/15",
          className
        )}
        {...props}
      />
      <button
        type="button"
        tabIndex={-1}
        disabled={disabled}
        aria-label={showPassword ? "Hide password" : "Show password"}
        aria-pressed={showPassword}
        onClick={() => setShowPassword((prev) => !prev)}
        className="absolute right-3 top-1/2 -translate-y-1/2 p-1.5 rounded-lg text-muted-foreground hover:text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 transition-colors disabled:pointer-events-none disabled:opacity-40 cursor-pointer"
      >
        {showPassword ? (
          <EyeOff className="size-4" aria-hidden="true" />
        ) : (
          <Eye className="size-4" aria-hidden="true" />
        )}
      </button>
    </div>
  );
}

