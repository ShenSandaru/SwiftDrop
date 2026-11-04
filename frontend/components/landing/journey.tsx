"use client";

import { useEffect, useRef, useState } from "react";
import { Camera, CheckCircle2, ClipboardList, Route, UserCheck, type LucideIcon } from "lucide-react";
import { cn } from "cn";
import { StatusBadge } from "@/components/delivery/status-badge";
import type { ParcelStatus } from "@/lib/delivery";
import { Reveal } from "./reveal";

interface Step {
  icon: LucideIcon;
  stop: string;
  title: string;
  text: string;
  status?: ParcelStatus;
  chip?: string;
}

const STEPS: Step[] = [
  {
    icon: ClipboardList,
    stop: "Stop 1",
    title: "Book in a minute",
    text: "Pickup and drop-off addresses, size and weight. The fare comes from the zone table and is printed on your ticket before you confirm.",
    status: "CREATED",
  },
  {
    icon: UserCheck,
    stop: "Stop 2",
    title: "A courier is matched",
    text: "Dispatch assigns a courier who covers your route. From now on only that courier, you and the dispatchers can see the parcel.",
    chip: "Courier assigned",
  },
  {
    icon: Route,
    stop: "Stop 3",
    title: "One tap per step",
    text: "Picked up, in transit, out for delivery: the courier moves the parcel forward from their phone, with directions and tap-to-call.",
    status: "IN_TRANSIT",
  },
  {
    icon: Camera,
    stop: "Stop 4",
    title: "Proof at the door",
    text: "Before a parcel counts as delivered, the courier takes a photo and notes who received it. If nobody is home, the reason is recorded.",
    status: "OUT_FOR_DELIVERY",
  },
  {
    icon: CheckCircle2,
    stop: "Arrived",
    title: "Everyone sees it, live",
    text: "Your parcel page, the public tracking page and your receipt update the moment it happens. No refreshing, no calling around.",
    status: "DELIVERED",
  },
];

/** The point of the viewport (from the top) the route line fills up to. */
const TRIGGER = 0.62;

/**
 * "How a parcel moves": a scroll story. A route line fills as the visitor scrolls, and each stop
 * lights up when the line reaches it.
 */
export function Journey() {
  const trackRef = useRef<HTMLDivElement>(null);
  const fillRef = useRef<HTMLDivElement>(null);
  const dotRefs = useRef<(HTMLSpanElement | null)[]>([]);
  const [reached, setReached] = useState(-1);

  useEffect(() => {
    let frame = 0;
    const update = () => {
      frame = 0;
      const track = trackRef.current;
      const fill = fillRef.current;
      if (!track || !fill) {
        return;
      }
      const triggerY = window.innerHeight * TRIGGER;
      const rect = track.getBoundingClientRect();
      const filled = Math.min(Math.max(triggerY - rect.top, 0), rect.height);
      fill.style.height = `${filled}px`;
      const count = dotRefs.current.filter((dot) => dot && dot.getBoundingClientRect().top + 8 <= triggerY).length;
      setReached(count - 1);
    };
    const schedule = () => {
      if (!frame) {
        frame = requestAnimationFrame(update);
      }
    };
    schedule();
    window.addEventListener("scroll", schedule, { passive: true });
    window.addEventListener("resize", schedule);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener("scroll", schedule);
      window.removeEventListener("resize", schedule);
    };
  }, []);

  return (
    <section id="how-it-moves" className="scroll-mt-16 border-b border-border py-20 md:py-28">
      <div className="container mx-auto max-w-5xl px-4 sm:px-6">
        <Reveal className="mx-auto max-w-2xl space-y-3 text-center">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">How a parcel moves</p>
          <h2 className="text-3xl font-extrabold tracking-tight text-foreground sm:text-4xl">
            One route, five stops, nothing hidden.
          </h2>
          <p className="text-muted-foreground">Scroll down and follow a parcel from booking to the recipient&apos;s door.</p>
        </Reveal>

        <div ref={trackRef} className="relative mt-16">
          {/* Road ahead and the travelled part; left on phones, centred on wider screens */}
          <div aria-hidden="true" className="absolute bottom-0 left-5 top-0 border-l-2 border-dashed border-border md:left-1/2" />
          <div ref={fillRef} aria-hidden="true"
            className="absolute left-5 top-0 h-0 w-1 -translate-x-[1px] rounded-full bg-gradient-to-b from-primary/40 to-primary md:left-1/2" />

          <ol className="space-y-14 md:space-y-20">
            {STEPS.map((step, index) => {
              const isReached = index <= reached;
              const right = index % 2 === 1;
              const Icon = step.icon;
              return (
                <li key={step.title} className="relative grid md:grid-cols-2 md:gap-16">
                  <span
                    ref={(node) => { dotRefs.current[index] = node; }}
                    aria-hidden="true"
                    className={cn(
                      "absolute left-5 top-6 z-10 inline-flex size-10 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full border-2 transition-all duration-500 md:left-1/2",
                      isReached
                        ? "scale-110 border-primary bg-primary text-primary-foreground shadow-[0_0_0_6px_color-mix(in_oklab,var(--primary)_18%,transparent)]"
                        : "border-border bg-card text-muted-foreground",
                    )}
                  >
                    <Icon className="size-[18px]" />
                  </span>

                  <Reveal delay={0.05} className={cn("pl-14 md:pl-0", right ? "md:col-start-2" : "md:text-right")}>
                    <article
                      className={cn(
                        "rounded-2xl border bg-card p-5 shadow-sm transition-colors duration-500 sm:p-6",
                        isReached ? "border-primary/40" : "border-border",
                      )}
                    >
                      <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-primary">{step.stop}</p>
                      <h3 className="mt-1 text-xl font-bold text-foreground">{step.title}</h3>
                      <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{step.text}</p>
                      <div className={cn("mt-4 flex", !right && "md:justify-end")}>
                        {step.status ? (
                          <StatusBadge status={step.status} />
                        ) : (
                          <span className="inline-flex items-center gap-1.5 rounded-full border border-primary/20 bg-accent px-2.5 py-0.5 text-xs font-semibold text-primary">
                            <UserCheck className="size-3" aria-hidden="true" /> {step.chip}
                          </span>
                        )}
                      </div>
                    </article>
                  </Reveal>
                </li>
              );
            })}
          </ol>
        </div>
      </div>
    </section>
  );
}
