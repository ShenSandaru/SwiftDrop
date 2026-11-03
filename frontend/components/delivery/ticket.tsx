import { cn } from "cn";

export interface TicketProps {
  children: React.ReactNode;
  /** The part below the perforation, like a ticket stub. */
  stub?: React.ReactNode;
  className?: string;
  id?: string;
}

/**
 * A torn-off ticket: a card with a dashed perforation and two half-circle notches cut out of its
 * sides. Used for the price estimate, the booking confirmation and the receipt. The notches take
 * the page background colour, so they look like real cut-outs.
 */
export function Ticket({ children, stub, className, id }: TicketProps) {
  return (
    <div id={id} className={cn("relative rounded-2xl border border-border bg-card shadow-sm", className)}>
      <div className="p-5 sm:p-6">{children}</div>
      {stub && (
        <>
          <div className="relative" aria-hidden="true">
            <span className="absolute -left-3 top-1/2 size-6 -translate-y-1/2 rounded-full border border-border bg-background [clip-path:inset(0_0_0_50%)]" />
            <span className="absolute -right-3 top-1/2 size-6 -translate-y-1/2 rounded-full border border-border bg-background [clip-path:inset(0_50%_0_0)]" />
            <div className="mx-5 border-t-2 border-dashed border-border" />
          </div>
          <div className="p-5 sm:p-6">{stub}</div>
        </>
      )}
    </div>
  );
}
