"use client";

import { useCallback, useEffect, useId, useRef, type KeyboardEvent } from "react";
import { cn } from "cn";
import { useReducedMotion } from "./use-reduced-motion";

/** Cities with three-letter codes, like airports, for the boarding pass. Illustration only. */
export const CITIES = [
  { code: "CMB", name: "Colombo" },
  { code: "KDY", name: "Kandy" },
  { code: "GAL", name: "Galle" },
  { code: "JAF", name: "Jaffna" },
  { code: "TRC", name: "Trincomalee" },
  { code: "BTC", name: "Batticaloa" },
  { code: "ANU", name: "Anuradhapura" },
  { code: "MTR", name: "Matara" },
] as const;

export type CityCode = (typeof CITIES)[number]["code"];

const ITEM_HEIGHT = 44;
/** Rows visible above and below the selected one. */
const SIDE_ROWS = 2;
const SETTLE_MS = 120;

/**
 * A scroll wheel for picking a city, like a drum picker on a phone: scroll, swipe or use the arrow
 * keys, and the city that stops in the middle band is chosen. Rows curve away with distance.
 */
export function CityWheel({ label, value, onChange }: {
  label: string;
  value: CityCode;
  onChange: (code: CityCode) => void;
}) {
  const id = useId();
  const reducedMotion = useReducedMotion();
  const listRef = useRef<HTMLDivElement>(null);
  const rowRefs = useRef<(HTMLDivElement | null)[]>([]);
  const valueRef = useRef(value);
  const onChangeRef = useRef(onChange);
  const settleTimer = useRef<number | undefined>(undefined);
  const frame = useRef(0);
  const mounted = useRef(false);
  const index = CITIES.findIndex((city) => city.code === value);

  useEffect(() => {
    valueRef.current = value;
    onChangeRef.current = onChange;
  });

  /** Curves and fades each row by its distance from the middle band. */
  const paint = useCallback(() => {
    frame.current = 0;
    const list = listRef.current;
    if (!list) {
      return;
    }
    const position = list.scrollTop / ITEM_HEIGHT;
    rowRefs.current.forEach((row, rowIndex) => {
      if (!row) {
        return;
      }
      const distance = rowIndex - position;
      const away = Math.min(Math.abs(distance), SIDE_ROWS + 1);
      row.style.opacity = String(1 - away * 0.3);
      row.style.transform = reducedMotion
        ? ""
        : `perspective(420px) rotateX(${-distance * 22}deg) scale(${1 - away * 0.06})`;
    });
  }, [reducedMotion]);

  const scrollToRow = useCallback(
    (rowIndex: number, instant = false) => {
      const target = Math.max(0, Math.min(CITIES.length - 1, rowIndex)) * ITEM_HEIGHT;
      listRef.current?.scrollTo({ top: target, behavior: instant || reducedMotion ? "auto" : "smooth" });
    },
    [reducedMotion],
  );

  // Follow the value when it changes from outside (first render, the swap button)
  useEffect(() => {
    const list = listRef.current;
    if (list && Math.abs(list.scrollTop - index * ITEM_HEIGHT) > 1) {
      scrollToRow(index, !mounted.current);
    }
    mounted.current = true;
    paint();
  }, [index, paint, scrollToRow]);

  useEffect(
    () => () => {
      window.clearTimeout(settleTimer.current);
      cancelAnimationFrame(frame.current);
    },
    [],
  );

  function handleScroll() {
    if (!frame.current) {
      frame.current = requestAnimationFrame(paint);
    }
    // Choose the city once the wheel has come to rest
    window.clearTimeout(settleTimer.current);
    settleTimer.current = window.setTimeout(() => {
      const list = listRef.current;
      if (!list) {
        return;
      }
      const rowIndex = Math.max(0, Math.min(CITIES.length - 1, Math.round(list.scrollTop / ITEM_HEIGHT)));
      const code = CITIES[rowIndex].code;
      if (code !== valueRef.current) {
        onChangeRef.current(code);
      }
    }, SETTLE_MS);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const moves: Record<string, number> = { ArrowDown: index + 1, ArrowUp: index - 1, Home: 0, End: CITIES.length - 1 };
    if (event.key in moves) {
      event.preventDefault();
      scrollToRow(moves[event.key]);
    }
  }

  return (
    <div className="min-w-0 space-y-2">
      <p id={`${id}-label`} className="text-[11px] font-semibold uppercase tracking-[0.2em] text-muted-foreground">
        {label}
      </p>
      <div className="relative overflow-hidden rounded-2xl border border-border bg-card">
        {/* The middle band: whatever stops here is chosen */}
        <div aria-hidden="true"
          className="pointer-events-none absolute inset-x-1.5 top-1/2 -translate-y-1/2 rounded-xl border border-primary/30 bg-accent"
          style={{ height: ITEM_HEIGHT }} />
        <div
          ref={listRef}
          role="listbox"
          tabIndex={0}
          aria-labelledby={`${id}-label`}
          aria-activedescendant={`${id}-${value}`}
          onScroll={handleScroll}
          onKeyDown={handleKeyDown}
          className="relative snap-y snap-mandatory overflow-y-auto outline-none [scrollbar-width:none] focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset [&::-webkit-scrollbar]:hidden [mask-image:linear-gradient(transparent,black_32%,black_68%,transparent)]"
          style={{ height: ITEM_HEIGHT * (SIDE_ROWS * 2 + 1), paddingBlock: ITEM_HEIGHT * SIDE_ROWS }}
        >
          {CITIES.map((city, rowIndex) => (
            <div
              key={city.code}
              id={`${id}-${city.code}`}
              ref={(node) => { rowRefs.current[rowIndex] = node; }}
              role="option"
              aria-selected={city.code === value}
              onClick={() => scrollToRow(rowIndex)}
              className={cn(
                "flex cursor-pointer snap-center items-center justify-between gap-2 px-3 will-change-transform sm:px-4",
                city.code === value ? "font-bold text-primary" : "font-medium text-foreground",
              )}
              style={{ height: ITEM_HEIGHT }}
            >
              <span className="truncate text-sm sm:text-base">{city.name}</span>
              {/* The code only fits next to long names on wider screens */}
              <span className="hidden font-mono text-[11px] tracking-[0.15em] text-muted-foreground sm:inline">{city.code}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
