import { cn } from "cn";

/**
 * "Colombo 05 ──────▶ Kandy" with the tracking number above it: the headline of every Island Route
 * view. The dashed road animates slowly to suggest movement (disabled for reduced motion).
 */
export function RouteHeading({
  from,
  to,
  trackingNumber,
  className,
}: {
  from: string;
  to: string;
  trackingNumber?: string;
  className?: string;
}) {
  return (
    <div className={cn("space-y-1", className)}>
      {trackingNumber && (
        <p className="font-mono text-xs font-semibold tracking-[0.2em] text-primary">{trackingNumber}</p>
      )}
      <div className="flex items-center gap-3">
        <span className="truncate capitalize text-lg font-extrabold tracking-tight text-foreground sm:text-2xl">{from}</span>
        <span className="relative h-0.5 min-w-10 flex-1 overflow-hidden" aria-hidden="true">
          <span className="absolute inset-0 bg-[repeating-linear-gradient(90deg,var(--color-primary)_0_8px,transparent_8px_14px)] motion-safe:animate-[route-dash_1.2s_linear_infinite]" />
        </span>
        <span className="sr-only">to</span>
        <span className="truncate capitalize text-lg font-extrabold tracking-tight text-foreground sm:text-2xl">{to}</span>
      </div>
      <style>{`@keyframes route-dash { from { background-position: 0 0 } to { background-position: 14px 0 } }`}</style>
    </div>
  );
}
