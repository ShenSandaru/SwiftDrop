"use client";

import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";
import { cn } from "cn";
import "./landing.css";

type RevealState = "idle" | "hidden" | "shown";

/**
 * Fades its content in when it scrolls into view. Content is visible by default (server render,
 * no script, reduced motion); it is hidden only when the script sees it is still below the fold.
 */
export function Reveal({ children, delay = 0, className, as: Tag = "div" }: {
  children: ReactNode;
  /** Seconds to wait after entering the view, for staggered groups. */
  delay?: number;
  className?: string;
  as?: "div" | "li" | "section";
}) {
  const ref = useRef<HTMLElement>(null);
  const [state, setState] = useState<RevealState>("idle");

  useEffect(() => {
    const element = ref.current;
    if (!element || element.getBoundingClientRect().top < window.innerHeight) {
      return;
    }
    // Deferred so the hidden state is set outside the effect body, as React recommends
    const frame = requestAnimationFrame(() => setState("hidden"));
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setState("shown");
          observer.disconnect();
        }
      },
      { rootMargin: "0px 0px -12% 0px" },
    );
    observer.observe(element);
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
    };
  }, []);

  return (
    <Tag
      ref={(node: HTMLElement | null) => {
        ref.current = node;
      }}
      data-state={state}
      className={cn("landing-reveal", className)}
      style={{ "--delay": `${delay}s` } as CSSProperties}
    >
      {children}
    </Tag>
  );
}
