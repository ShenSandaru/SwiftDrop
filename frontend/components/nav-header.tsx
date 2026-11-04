"use client";

import { useEffect, useRef, useState, type FocusEvent } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { LogOut, Menu, X } from "lucide-react";
import { cn } from "cn";
import { useAuth } from "@/lib/auth-context";

const HOVER_OPEN_MS = 150;
const HOVER_CLOSE_MS = 250;

interface NavLink {
  href: string;
  label: string;
}

function PinMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} aria-hidden="true">
      <path d="M12 22c-1-2-7-6.5-7-12a7 7 0 0 1 14 0c0 5.5-6 10-7 12Z" className="fill-sidebar-primary" />
      <rect x="8.5" y="6.5" width="7" height="7" rx="1.5" className="fill-foreground" />
    </svg>
  );
}

/**
 * The "dynamic island" header: a small dark pill centred at the top of every page. It opens on
 * hover, keyboard focus or a tap on the menu button: sideways when the links fit the screen, and
 * otherwise as a card below the pill (phones, or users with many roles). The links shown depend on the signed-in user's roles; the backend
 * still enforces access, this only decides what is offered.
 */
export function NavHeader() {
  const { user, isAuthenticated, isLoading, logout, hasRole } = useAuth();
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  // Opened with the menu button (or a tap): stays open until closed, not just while hovered
  const [pinned, setPinned] = useState(false);
  const navRef = useRef<HTMLElement>(null);
  const hoverTimer = useRef<number | undefined>(undefined);
  const logoRef = useRef<HTMLAnchorElement>(null);
  const linksRef = useRef<HTMLDivElement>(null);
  // Whether the links fit when the island stretches sideways; if not, it opens as a card below
  const [sideways, setSideways] = useState(false);

  const links: NavLink[] = [{ href: "/track", label: "Track Parcel" }];
  if (hasRole("CUSTOMER")) links.push({ href: "/customer", label: "My Deliveries" });
  if (hasRole("MERCHANT")) links.push({ href: "/merchant", label: "Merchant Portal" });
  if (hasRole("COURIER")) links.push({ href: "/courier", label: "Courier Tasks" });
  if (hasRole("DISPATCHER_ADMIN")) {
    links.push({ href: "/dispatch", label: "Dispatch" }, { href: "/admin", label: "Admin" });
  }
  const isActive = (href: string) => pathname === href || pathname.startsWith(`${href}/`);
  const current = links.find((link) => isActive(link.href));
  const initial = (user?.fullName || user?.email || "?").trim().charAt(0).toUpperCase();

  const close = () => {
    window.clearTimeout(hoverTimer.current);
    setOpen(false);
    setPinned(false);
  };

  useEffect(() => () => window.clearTimeout(hoverTimer.current), []);

  // Measure the full width of the links (scrollWidth counts them even while collapsed)
  const linkCount = links.length;
  useEffect(() => {
    let frame = 0;
    const measure = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const logo = logoRef.current;
        const inner = linksRef.current;
        if (!logo || !inner) {
          return;
        }
        // Logo + expanded links + 40px menu button + 28px pill padding and gaps
        const needed = logo.offsetWidth + inner.scrollWidth + 68;
        setSideways(needed + 24 <= window.innerWidth);
      });
    };
    measure();
    window.addEventListener("resize", measure);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener("resize", measure);
    };
  }, [linkCount, user, isLoading]);

  // While pinned open: close on Escape or on a tap outside the island
  useEffect(() => {
    if (!open) {
      return;
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
        setPinned(false);
      }
    };
    const onPointer = (event: PointerEvent) => {
      if (navRef.current && !navRef.current.contains(event.target as Node)) {
        setOpen(false);
        setPinned(false);
      }
    };
    document.addEventListener("keydown", onKey);
    document.addEventListener("pointerdown", onPointer);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("pointerdown", onPointer);
    };
  }, [open]);

  const canHover = () => window.matchMedia("(hover: hover)").matches;
  // A short pause before opening and closing, so passing the mouse over it (on the way to the
  // browser tabs, say) does not make it jump
  const onMouseEnter = () => {
    window.clearTimeout(hoverTimer.current);
    if (canHover()) {
      hoverTimer.current = window.setTimeout(() => setOpen(true), HOVER_OPEN_MS);
    }
  };
  const onMouseLeave = () => {
    window.clearTimeout(hoverTimer.current);
    if (!pinned) {
      hoverTimer.current = window.setTimeout(() => setOpen(false), HOVER_CLOSE_MS);
    }
  };
  const onBlur = (event: FocusEvent) => {
    if (!pinned && !navRef.current?.contains(event.relatedTarget as Node)) {
      setOpen(false);
    }
  };

  const linkClass = (href: string) =>
    cn(
      "shrink-0 whitespace-nowrap rounded-full px-4 py-2 text-base transition-colors",
      isActive(href) ? "bg-background/15 font-semibold text-background" : "text-background/70 hover:bg-background/10 hover:text-background",
    );

  const account = isLoading ? (
    <span className="mx-2 h-6 w-16 shrink-0 animate-pulse rounded-full bg-background/15" />
  ) : isAuthenticated && user ? (
    <>
      <span className="max-w-40 shrink-0 truncate px-2 text-sm text-background/60">{user.fullName || user.email}</span>
      <button type="button" onClick={() => { close(); logout(); }}
        className="inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full bg-background/10 px-4 py-2 text-base text-background hover:bg-background/20">
        <LogOut className="size-3.5" aria-hidden="true" /> Sign Out
      </button>
    </>
  ) : (
    <>
      <Link href="/login" onClick={close} className={linkClass("/login")}>Sign In</Link>
      <Link href="/signup" onClick={close}
        className="shrink-0 whitespace-nowrap rounded-full bg-primary px-5 py-2 text-base font-semibold text-primary-foreground hover:bg-primary/90">
        Register
      </Link>
    </>
  );

  return (
    // Same 3.5rem as the old bar, so pages sized with calc(100vh - 3.5rem) still fit. The 3.5rem pill
    // sits 0.5rem from the top and overhangs the slot slightly; pages start with more padding than that
    <header className="pointer-events-none sticky top-0 z-40 flex h-14 justify-center px-3 pt-2 before:pointer-events-none before:fixed before:inset-x-0 before:top-0 before:h-6 before:bg-gradient-to-b before:from-background before:to-transparent print:hidden">
      <nav
        ref={navRef}
        aria-label="Main"
        onMouseEnter={onMouseEnter}
        onMouseLeave={onMouseLeave}
        onFocus={(event) => {
          if ((event.target as HTMLElement).matches?.(":focus-visible")) {
            setOpen(true);
          }
        }}
        onBlur={onBlur}
        className="pointer-events-auto relative"
      >
        <div className="flex h-14 max-w-[calc(100vw-1.5rem)] items-center gap-1.5 rounded-full bg-foreground pl-2 pr-2 text-background shadow-[0_10px_30px_-10px_rgb(15_31_29/0.6)] ring-1 ring-background/10">
          <Link ref={logoRef} href="/" onClick={close} className="flex shrink-0 items-center gap-1.5 rounded-full py-1 pl-1 pr-2.5 text-xl font-bold tracking-tight">
            <PinMark className="size-8" />
            <span>Swift<span className="text-sidebar-primary">Drop</span></span>
          </Link>

          {/* Where you are and who is signed in; kept while the card is open, so the pill does
              not shrink out from under the pointer */}
          {!(open && sideways) && current && (
            <span className="min-w-0 max-w-28 truncate whitespace-nowrap rounded-full bg-primary/25 px-3 py-1 text-xs font-semibold text-background sm:max-w-none sm:text-sm">
              {current.label}
            </span>
          )}

          {/* Open, when the links fit: the island stretches sideways */}
          <div
            ref={linksRef}
            className={cn(
              "flex items-center gap-1 overflow-hidden transition-[max-width,opacity] duration-500 ease-[cubic-bezier(0.3,1.25,0.5,1)]",
              open && sideways ? "max-w-[64rem] opacity-100" : "max-w-0 opacity-0",
            )}
            aria-hidden={!(open && sideways)}
            inert={!(open && sideways)}
          >
            <span className="mx-1 h-5 w-px shrink-0 bg-background/15" aria-hidden="true" />
            {links.map((link) => (
              <Link key={link.href} href={link.href} onClick={close} className={linkClass(link.href)}
                aria-current={isActive(link.href) ? "page" : undefined}>
                {link.label}
              </Link>
            ))}
            <span className="mx-1 h-5 w-px shrink-0 bg-background/15" aria-hidden="true" />
            {account}
          </div>

          {!(open && sideways) && isAuthenticated && user && (
            <span aria-hidden="true" className="inline-flex size-9 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-bold text-primary-foreground">
              {initial}
            </span>
          )}
          <button
            type="button"
            aria-label={open ? "Close menu" : "Open menu"}
            aria-expanded={open}
            onClick={() => {
              if (open && pinned) {
                close();
              } else {
                setOpen(true);
                setPinned(true);
              }
            }}
            className="inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-background/10 transition-colors hover:bg-background/20"
          >
            {open ? <X className="size-5" aria-hidden="true" /> : <Menu className="size-5" aria-hidden="true" />}
          </button>
        </div>

        {/* Open, when they do not fit (phones, or many roles): the island grows into a card below */}
        {open && !sideways && (
          <div className="absolute left-1/2 top-full mt-2 w-[min(92vw,22rem)] -translate-x-1/2 rounded-3xl bg-foreground p-3 text-background shadow-2xl ring-1 ring-background/10 motion-safe:animate-in motion-safe:fade-in motion-safe:zoom-in-95">
            <ul className="space-y-1">
              {links.map((link) => (
                <li key={link.href}>
                  <Link href={link.href} onClick={close} aria-current={isActive(link.href) ? "page" : undefined}
                    className={cn("block rounded-2xl px-4 py-3 text-base", isActive(link.href) ? "bg-background/15 font-semibold" : "text-background/80")}>
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
            <div className="mt-3 flex flex-wrap items-center justify-end gap-2 border-t border-background/10 pt-3">{account}</div>
          </div>
        )}
      </nav>
    </header>
  );
}
