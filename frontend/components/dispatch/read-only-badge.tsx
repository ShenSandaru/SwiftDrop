import { Eye } from "lucide-react";

/** Marks a parcel the dispatcher may follow but not change: it is with another hub (ADR 0012 §3). */
export function ReadOnlyBadge({ readOnly }: { readOnly: boolean }) {
  if (!readOnly) {
    return null;
  }
  return (
    <span
      title="This parcel is with another hub. You can follow it, but not change it."
      className="inline-flex shrink-0 items-center gap-1 rounded-full border border-border bg-muted px-2 py-0.5 text-[11px] font-semibold text-muted-foreground"
    >
      <Eye className="size-3" aria-hidden="true" />
      Following
    </span>
  );
}
