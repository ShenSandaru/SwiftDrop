"use client";

import React, { useEffect, useSyncExternalStore } from "react";
import { createPortal } from "react-dom";
import { AlertTriangle, Loader2, Trash2, X } from "lucide-react";
import { cn } from "cn";
import { Button } from "./button";

const emptySubscribe = () => () => {};

export interface ConfirmationItemDetail {
  label: string;
  value: React.ReactNode;
  badge?: boolean;
}

export interface ConfirmationItemSummary {
  title: string;
  code?: string;
  badge?: string;
  badgeVariant?: "default" | "success" | "warning" | "destructive" | "outline";
  icon?: React.ComponentType<{ className?: string }>;
  details?: ConfirmationItemDetail[];
}

export interface ConfirmationDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: "destructive" | "warning" | "default";
  loading?: boolean;
  onConfirm: () => void | Promise<void>;
  itemSummary?: ConfirmationItemSummary;
}

export function ConfirmationDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = "Delete",
  cancelLabel = "Cancel",
  variant = "destructive",
  loading = false,
  onConfirm,
  itemSummary,
}: ConfirmationDialogProps) {
  const mounted = useSyncExternalStore(emptySubscribe, () => true, () => false);

  // Close on Escape key press
  useEffect(() => {
    if (!open) return;
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !loading) {
        onOpenChange(false);
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [open, loading, onOpenChange]);

  // Lock body scroll when dialog is open
  useEffect(() => {
    if (open) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "";
    }
    return () => {
      document.body.style.overflow = "";
    };
  }, [open]);

  if (!mounted || !open) return null;

  const IconComponent =
    variant === "destructive"
      ? Trash2
      : AlertTriangle;

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirmation-dialog-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
    >
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/60 backdrop-blur-xs transition-opacity animate-in fade-in duration-200"
        aria-hidden="true"
        onClick={() => {
          if (!loading) onOpenChange(false);
        }}
      />

      {/* Modal Card */}
      <div
        className={cn(
          "relative w-full max-w-md overflow-hidden rounded-2xl border border-border bg-card p-6 shadow-2xl transition-all",
          "animate-in fade-in zoom-in-95 duration-200"
        )}
      >
        {/* Close Button */}
        <button
          type="button"
          disabled={loading}
          onClick={() => onOpenChange(false)}
          className="absolute right-4 top-4 rounded-lg p-1 text-muted-foreground hover:bg-muted hover:text-foreground disabled:opacity-50 transition-colors"
          aria-label="Close dialog"
        >
          <X className="size-4" />
        </button>

        {/* Header with Icon */}
        <div className="flex items-start gap-3.5">
          <div
            className={cn(
              "flex size-10 shrink-0 items-center justify-center rounded-xl",
              variant === "destructive" && "bg-destructive/10 text-destructive",
              variant === "warning" && "bg-amber-500/10 text-amber-600 dark:text-amber-400",
              variant === "default" && "bg-primary/10 text-primary"
            )}
          >
            <IconComponent className="size-5" aria-hidden="true" />
          </div>

          <div className="space-y-1 pr-6">
            <h3
              id="confirmation-dialog-title"
              className="text-base font-bold text-foreground leading-snug"
            >
              {title}
            </h3>
            <p className="text-xs text-muted-foreground leading-relaxed">
              {description}
            </p>
          </div>
        </div>

        {/* Embedded Item Preview Statcard */}
        {itemSummary && (
          <div className="mt-4 rounded-xl border border-border/80 bg-muted/40 p-3.5 space-y-2.5">
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-2 min-w-0">
                {itemSummary.icon && (
                  <itemSummary.icon className="size-4 shrink-0 text-primary" />
                )}
                <span className="font-semibold text-sm text-foreground truncate">
                  {itemSummary.title}
                </span>
                {itemSummary.code && (
                  <span className="font-mono text-xs font-bold px-1.5 py-0.5 rounded bg-muted border border-border/60 text-primary shrink-0">
                    {itemSummary.code}
                  </span>
                )}
              </div>

              {itemSummary.badge && (
                <span
                  className={cn(
                    "text-[10px] font-medium px-2 py-0.5 rounded-full shrink-0",
                    itemSummary.badgeVariant === "success" &&
                      "bg-emerald-500/15 text-emerald-700 dark:text-emerald-400",
                    itemSummary.badgeVariant === "destructive" &&
                      "bg-destructive/15 text-destructive",
                    itemSummary.badgeVariant === "warning" &&
                      "bg-amber-500/15 text-amber-700 dark:text-amber-400",
                    itemSummary.badgeVariant === "default" &&
                      "bg-primary text-primary-foreground",
                    (!itemSummary.badgeVariant || itemSummary.badgeVariant === "outline") &&
                      "border border-border/70 text-muted-foreground"
                  )}
                >
                  {itemSummary.badge}
                </span>
              )}
            </div>

            {/* Sub-details grid */}
            {itemSummary.details && itemSummary.details.length > 0 && (
              <div className="grid grid-cols-2 gap-2 pt-2 border-t border-border/50 text-xs">
                {itemSummary.details.map((detail, idx) => (
                  <div key={idx} className="space-y-0.5 min-w-0">
                    <span className="text-[10px] font-medium text-muted-foreground uppercase tracking-wider block truncate">
                      {detail.label}
                    </span>
                    <span className="font-medium text-foreground text-xs block truncate">
                      {detail.badge ? (
                        <span className="font-mono font-semibold px-1 py-0.2 rounded bg-muted text-foreground">
                          {detail.value}
                        </span>
                      ) : (
                        detail.value
                      )}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Action Buttons */}
        <div className="mt-5 flex items-center justify-end gap-2.5">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={loading}
            onClick={() => onOpenChange(false)}
            className="h-8 px-3 text-xs"
          >
            {cancelLabel}
          </Button>

          <Button
            type="button"
            variant={variant === "destructive" ? "destructive" : "default"}
            size="sm"
            disabled={loading}
            onClick={onConfirm}
            className="h-8 gap-1.5 px-3 text-xs"
          >
            {loading && <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />}
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>,
    document.body
  );
}
