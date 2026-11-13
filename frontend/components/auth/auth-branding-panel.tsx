import React from "react";
import Link from "next/link";
import Image from "next/image";

export interface AuthBrandingPanelProps {
  badgeText?: string;
  headline?: string;
  description?: string;
  imageSrc?: string;
  imageAlt?: string;
}

function PinMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} aria-hidden="true">
      <path d="M12 22c-1-2-7-6.5-7-12a7 7 0 0 1 14 0c0 5.5-6 10-7 12Z" className="fill-sidebar-primary" />
      <rect x="8.5" y="6.5" width="7" height="7" rx="1.5" className="fill-foreground" />
    </svg>
  );
}

export function AuthBrandingPanel({
  badgeText,
  headline = "From your door to anywhere on the island.",
  description = "Book in a minute. Track every stop. Delivered with proof.",
  imageSrc = "/images/auth-delivery.png",
  imageAlt = "Professional courier delivering parcel box at doorstep",
}: AuthBrandingPanelProps) {
  return (
    <div className="relative flex flex-col justify-between h-full p-5 sm:p-6 lg:p-10 xl:p-12 z-10">
      {/* Background patterns: subtle dotted grid & teal radial glow */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle,rgba(255,255,255,0.08)_1px,transparent_1px)] [background-size:22px_22px] [mask-image:linear-gradient(to_bottom,black,transparent_90%)]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_70%_70%_at_20%_25%,rgba(45,212,191,0.14),transparent)]"
      />

      {/* 1. Top: SwiftDrop logo and brand name */}
      <div className="relative z-10">
        <Link
          href="/"
          className="inline-flex items-center gap-2 rounded-full py-1 pr-3 text-xl font-bold tracking-tight text-background transition-opacity hover:opacity-90"
        >
          <PinMark className="size-8" />
          <span>
            Swift<span className="text-sidebar-primary">Drop</span>
          </span>
        </Link>
      </div>

      {/* Middle Content: Badge, Headline, Description, and Large Delivery Image */}
      <div className="relative z-10 flex-1 flex flex-col justify-center py-4 lg:py-6 space-y-4 lg:space-y-6">
        <div className="space-y-2 lg:space-y-3.5 max-w-lg">
          {badgeText && (
            <div className="inline-flex items-center gap-2 rounded-full border border-sidebar-primary/30 bg-sidebar-primary/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-sidebar-primary">
              <span className="size-1.5 rounded-full bg-sidebar-primary animate-pulse" aria-hidden="true" />
              {badgeText}
            </div>
          )}

          {/* Main headline */}
          <h2 className="text-2xl sm:text-3xl xl:text-4xl font-extrabold tracking-tight text-background leading-tight">
            {headline}
          </h2>

          {/* Short supporting description */}
          <p className="text-xs sm:text-sm lg:text-base text-background/75 leading-relaxed">
            {description}
          </p>
        </div>

        {/* Large delivery-related image (Desktop only to keep mobile/tablet forms accessible) */}
        <div className="hidden lg:block relative w-full overflow-hidden rounded-2xl border border-background/15 bg-background/5 shadow-2xl transition-all">
          <Image
            src={imageSrc}
            alt={imageAlt}
            width={600}
            height={800}
            priority
            className="w-full h-auto max-h-[38vh] xl:max-h-[44vh] object-cover object-center transform transition-transform duration-500 hover:scale-[1.02]"
          />
          {/* Subtle gradient overlay to smoothly anchor the image into the dark teal brand background */}
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-0 bg-gradient-to-t from-foreground/50 via-transparent to-transparent"
          />
        </div>
      </div>

      {/* Footer / Trust note (Desktop only) */}
      <div className="relative z-10 hidden lg:flex items-center justify-between border-t border-background/10 pt-4 text-xs text-background/50">
        <span>Island-wide parcel delivery</span>
        <span className="font-medium text-background/70">9 Provinces · 25 Districts</span>
      </div>
    </div>
  );
}
