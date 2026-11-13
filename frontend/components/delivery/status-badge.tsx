import { cn } from "cn";
import type { ParcelStatus } from "@/lib/delivery";
import { STATUS_LABELS } from "./format";

/** Status colours are independent of the theme so every status stays recognisable. */
const STATUS_STYLES: Record<ParcelStatus, string> = {
  CREATED: "bg-slate-100 text-slate-700 border-slate-200 dark:bg-slate-800/60 dark:text-slate-200 dark:border-slate-700",
  PICKED_UP: "bg-sky-50 text-sky-800 border-sky-200 dark:bg-sky-950/60 dark:text-sky-200 dark:border-sky-800",
  IN_TRANSIT: "bg-indigo-50 text-indigo-800 border-indigo-200 dark:bg-indigo-950/60 dark:text-indigo-200 dark:border-indigo-800",
  OUT_FOR_DELIVERY:
    "bg-amber-50 text-amber-900 border-amber-200 dark:bg-amber-950/60 dark:text-amber-200 dark:border-amber-800",
  DELIVERED:
    "bg-emerald-50 text-emerald-800 border-emerald-200 dark:bg-emerald-950/60 dark:text-emerald-200 dark:border-emerald-800",
  FAILED: "bg-red-50 text-red-800 border-red-200 dark:bg-red-950/60 dark:text-red-200 dark:border-red-800",
  AT_HUB: "bg-teal-50 text-teal-800 border-teal-200 dark:bg-teal-950/60 dark:text-teal-200 dark:border-teal-800",
  DELIVERY_ATTEMPT_FAILED:
    "bg-orange-50 text-orange-900 border-orange-200 dark:bg-orange-950/60 dark:text-orange-200 dark:border-orange-800",
  RETURNING: "bg-violet-50 text-violet-800 border-violet-200 dark:bg-violet-950/60 dark:text-violet-200 dark:border-violet-800",
  RETURNED: "bg-violet-100 text-violet-900 border-violet-300 dark:bg-violet-950/70 dark:text-violet-100 dark:border-violet-700",
  CANCELLED: "bg-zinc-100 text-zinc-600 border-zinc-200 dark:bg-zinc-800/60 dark:text-zinc-300 dark:border-zinc-700",
};

export function StatusBadge({ status, className }: { status: ParcelStatus; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap",
        STATUS_STYLES[status],
        className,
      )}
    >
      <span className="size-1.5 rounded-full bg-current opacity-70" aria-hidden="true" />
      {STATUS_LABELS[status]}
    </span>
  );
}
