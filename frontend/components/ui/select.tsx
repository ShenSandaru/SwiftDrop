"use client";

import React, { useEffect, useMemo, useRef, useState } from "react";
import { Check, ChevronDown, Search, X } from "lucide-react";
import { cn } from "cn";

export interface SelectOption {
  value: string;
  label: string;
  description?: string;
  badge?: string;
  badgeVariant?: "default" | "outline" | "success" | "warning";
  disabled?: boolean;
}

export interface ThemedSelectProps {
  value: string;
  onChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  disabled?: boolean;
  searchable?: boolean;
  searchPlaceholder?: string;
  className?: string;
  id?: string;
  size?: "sm" | "default";
}

export function ThemedSelect({
  value,
  onChange,
  options,
  placeholder = "Select an option...",
  disabled = false,
  searchable,
  searchPlaceholder = "Search options...",
  className,
  id,
  size = "default",
}: ThemedSelectProps) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const selectedOption = useMemo(
    () => options.find((opt) => opt.value === value),
    [options, value]
  );

  const isSearchable = searchable ?? options.length > 6;

  const filtered = useMemo(() => {
    if (!isSearchable || !search.trim()) return options;
    const q = search.toLowerCase();
    return options.filter(
      (opt) =>
        opt.label.toLowerCase().includes(q) ||
        (opt.description && opt.description.toLowerCase().includes(q)) ||
        (opt.badge && opt.badge.toLowerCase().includes(q))
    );
  }, [options, isSearchable, search]);

  // Click outside to close
  useEffect(() => {
    if (!open) return;
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
        setSearch("");
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  // Escape key to close
  useEffect(() => {
    if (!open) return;
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false);
        setSearch("");
      }
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [open]);

  // Focus search input when opened
  useEffect(() => {
    if (open && isSearchable) {
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [open, isSearchable]);

  return (
    <div ref={containerRef} className={cn("relative w-full", className)}>
      <button
        type="button"
        id={id}
        disabled={disabled}
        onClick={() => {
          if (!disabled) setOpen((prev) => !prev);
        }}
        className={cn(
          "flex w-full items-center justify-between rounded-lg border border-input bg-card text-foreground shadow-xs transition-colors text-left",
          "hover:bg-accent hover:text-accent-foreground focus:outline-none focus:ring-1 focus:ring-ring",
          size === "sm" ? "h-8 px-2.5 text-xs" : "h-9 px-3 text-sm",
          disabled && "cursor-not-allowed opacity-50 bg-muted/40",
          open && "ring-1 ring-ring border-ring"
        )}
      >
        <div className="flex items-center gap-2 truncate pr-2">
          {selectedOption ? (
            <>
              <span className="font-medium truncate">{selectedOption.label}</span>
              {selectedOption.badge && (
                <span className="shrink-0 text-[10px] font-mono font-bold px-1.5 py-0.2 rounded bg-muted border border-border/60 text-primary">
                  {selectedOption.badge}
                </span>
              )}
            </>
          ) : (
            <span className="text-muted-foreground truncate">{placeholder}</span>
          )}
        </div>
        <ChevronDown className={cn("size-4 shrink-0 opacity-50 transition-transform duration-200", open && "rotate-180")} />
      </button>

      {open && (
        <div className="absolute z-50 mt-1 max-h-64 w-full min-w-[220px] overflow-auto rounded-xl border border-border bg-popover p-1 text-popover-foreground shadow-lg animate-in fade-in-0 zoom-in-95 duration-100">
          {isSearchable && (
            <div className="sticky top-0 bg-popover p-1 border-b border-border/50">
              <div className="flex items-center gap-1.5 rounded-lg border border-input px-2 py-1 bg-background">
                <Search className="size-3.5 text-muted-foreground shrink-0" />
                <input
                  ref={inputRef}
                  type="text"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder={searchPlaceholder}
                  className="w-full bg-transparent text-xs outline-none placeholder:text-muted-foreground text-foreground"
                />
                {search && (
                  <button
                    type="button"
                    onClick={() => setSearch("")}
                    className="text-muted-foreground hover:text-foreground"
                  >
                    <X className="size-3" />
                  </button>
                )}
              </div>
            </div>
          )}

          <div className="py-1">
            {filtered.length === 0 ? (
              <p className="p-3 text-center text-xs text-muted-foreground">No matching options found.</p>
            ) : (
              filtered.map((opt) => {
                const isSelected = opt.value === value;
                return (
                  <button
                    key={opt.value}
                    type="button"
                    disabled={opt.disabled}
                    onClick={() => {
                      if (opt.disabled) return;
                      onChange(opt.value);
                      setOpen(false);
                      setSearch("");
                    }}
                    className={cn(
                      "flex w-full items-center justify-between rounded-lg px-2.5 py-1.5 text-xs text-left transition-colors",
                      opt.disabled
                        ? "opacity-40 cursor-not-allowed bg-muted/20 select-none"
                        : isSelected
                        ? "bg-accent font-medium text-accent-foreground"
                        : "hover:bg-accent hover:text-accent-foreground"
                    )}
                  >
                    <div className="flex flex-col min-w-0 pr-2">
                      <div className="flex items-center gap-1.5">
                        <span className={cn("truncate", opt.disabled ? "text-muted-foreground" : "text-foreground font-medium")}>
                          {opt.label}
                        </span>
                        {opt.badge && (
                          <span
                            className={cn(
                              "shrink-0 text-[10px] px-1.5 py-0.2 rounded font-mono font-medium",
                              opt.badgeVariant === "success"
                                ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
                                : opt.badgeVariant === "warning"
                                ? "bg-amber-500/10 text-amber-600 dark:text-amber-400"
                                : "bg-muted text-muted-foreground border border-border/50"
                            )}
                          >
                            {opt.badge}
                          </span>
                        )}
                      </div>
                      {opt.description && (
                        <span className="text-[10px] text-muted-foreground truncate">{opt.description}</span>
                      )}
                    </div>
                    {isSelected && <Check className="size-3.5 shrink-0 text-primary" />}
                  </button>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}

