import { Camera, Radio, Ticket, type LucideIcon } from "lucide-react";
import { cn } from "cn";
import { Reveal } from "./reveal";
import "./landing.css";

interface StampedPromise {
  icon: LucideIcon;
  value: string;
  title: string;
  text: string;
  mark: string;
}

const PROMISES: StampedPromise[] = [
  {
    icon: Ticket,
    value: "No. 01",
    title: "Fare up front",
    text: "You see the price before you confirm. It is printed on your ticket and does not change on the way.",
    mark: "PRICED",
  },
  {
    icon: Radio,
    value: "No. 02",
    title: "Live, not later",
    text: "Every step reaches your screen the moment the courier taps it. No refreshing, no calling around.",
    mark: "LIVE",
  },
  {
    icon: Camera,
    value: "No. 03",
    title: "Proof at the door",
    text: "A photo and the recipient's name are recorded before anything counts as delivered.",
    mark: "PROVEN",
  },
];

/** A round rubber-stamp mark with its word set along the ring. */
function RubberMark({ word, id }: { word: string; id: string }) {
  return (
    <svg viewBox="0 0 120 120" className="size-full" aria-hidden="true">
      <defs>
        <path id={id} d="M60 60m-42 0a42 42 0 1 1 84 0a42 42 0 1 1 -84 0" />
      </defs>
      <circle cx="60" cy="60" r="56" className="fill-none stroke-current" strokeWidth="3" />
      <circle cx="60" cy="60" r="31" className="fill-none stroke-current" strokeWidth="1.5" />
      <text className="fill-current text-[10px] font-bold tracking-[0.3em]">
        <textPath href={`#${id}`}>SWIFTDROP · SRI LANKA ·</textPath>
      </text>
      <text x="60" y="65" textAnchor="middle" className="fill-current text-[13px] font-extrabold tracking-wider">
        {word}
      </text>
    </svg>
  );
}

/** "Promises, stamped": three postage stamps that get a rubber stamp as they scroll into view. */
export function Stamps() {
  return (
    <section className="border-b border-border py-20 md:py-28">
      <div className="container mx-auto max-w-6xl px-4 sm:px-6">
        <Reveal className="mx-auto max-w-2xl space-y-3 text-center">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">Our promises</p>
          <h2 className="text-3xl font-extrabold tracking-tight text-foreground sm:text-4xl">Signed, sealed, stamped.</h2>
          <p className="text-muted-foreground">Three things every parcel gets, whatever its size and wherever it goes.</p>
        </Reveal>

        <ul className="mt-14 grid gap-8 md:grid-cols-3">
          {PROMISES.map((promise, index) => {
            const Icon = promise.icon;
            return (
              <Reveal as="li" key={promise.title} delay={index * 0.15} className="landing-stamp-host h-full">
                {/* Perforated edge: page-coloured dots show only in the padding around the face */}
                <div className={cn("landing-stamp", "h-full p-3", index === 1 ? "md:rotate-1" : "md:-rotate-1")}>
                  <article className="relative h-full overflow-hidden border border-primary/15 bg-card p-6">
                    <span className="flex size-12 items-center justify-center rounded-xl bg-accent text-primary">
                      <Icon className="size-6" aria-hidden="true" />
                    </span>
                    <h3 className="mt-6 text-xl font-bold text-foreground">{promise.title}</h3>
                    <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{promise.text}</p>
                    <p className="mt-6 flex justify-between text-[10px] font-semibold uppercase tracking-[0.3em] text-muted-foreground/70">
                      <span>SwiftDrop post · LK</span>
                      <span className="font-mono">{promise.value}</span>
                    </p>
                    {/* Top corner, clear of the text */}
                    <div
                      className={cn("landing-stamp-mark", "absolute -right-5 -top-5 size-28 text-primary")}
                      style={{ animationDelay: `${0.35 + index * 0.15}s` }}
                    >
                      <RubberMark word={promise.mark} id={`stamp-ring-${index}`} />
                    </div>
                  </article>
                </div>
              </Reveal>
            );
          })}
        </ul>
      </div>
    </section>
  );
}
