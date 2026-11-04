import { Plus } from "lucide-react";
import { cn } from "cn";
import { Reveal } from "./reveal";
import "./landing.css";

const QUESTIONS = [
  {
    question: "How do I track my parcel?",
    answer:
      "Use the tracking number on your confirmation and receipt. It starts with SD and has 12 characters. The tracking page needs no account and updates by itself.",
  },
  {
    question: "How much does it cost?",
    answer:
      "The fare depends on where the parcel starts and ends and on its size. You see it before you confirm, and it is fixed on your ticket.",
  },
  {
    question: "What can I send?",
    answer:
      "Small, medium and large parcels up to 30 kg: documents, clothes, gifts, small appliances. Describe the contents when you book.",
  },
  {
    question: "What if nobody is home?",
    answer:
      "The courier records why the delivery could not be completed, for example recipient unavailable or address not found, and you see it straight away.",
  },
  {
    question: "How do I know it arrived?",
    answer:
      "Before a parcel counts as delivered, the courier takes a photo and notes who received it. The name appears on your receipt.",
  },
  {
    question: "Can my business use SwiftDrop?",
    answer:
      "Yes. Merchant accounts send orders in bulk and follow every parcel from one place.",
  },
];

/** "Lost & found": questions as luggage tags that swing open on their strings. Native <details>. */
export function Faq() {
  return (
    <section className="border-b border-border bg-muted/40 py-20 md:py-28">
      <div className="container mx-auto max-w-6xl px-4 sm:px-6">
        <Reveal className="mx-auto max-w-2xl space-y-3 text-center">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">Lost &amp; found</p>
          <h2 className="text-3xl font-extrabold tracking-tight text-foreground sm:text-4xl">Questions with a tag on.</h2>
          <p className="text-muted-foreground">Pull a tag to read the answer.</p>
        </Reveal>

        {/* The line the tags hang from */}
        <div aria-hidden="true" className="relative mx-auto mt-14 hidden h-3 md:block">
          <svg viewBox="0 0 1000 12" preserveAspectRatio="none" className="absolute inset-0 size-full">
            <path d="M0 2Q500 14 1000 2" className="fill-none stroke-foreground/30" strokeWidth="2" />
          </svg>
        </div>

        <ul className="mt-6 grid items-start gap-x-6 gap-y-10 sm:grid-cols-2 md:mt-0 lg:grid-cols-3">
          {QUESTIONS.map((item, index) => (
            <Reveal as="li" key={item.question} delay={(index % 3) * 0.08} className="landing-tag-host flex flex-col items-center">
              {/* String from the line down into the tag's hole (12px inset + 7px radius) */}
              <span aria-hidden="true" className="relative z-10 -mb-[19px] h-[51px] w-px bg-foreground/30" />
              <details className={cn("landing-tag", "group w-full")}>
                <summary className="relative flex cursor-pointer list-none flex-col items-center gap-3 px-6 pb-6 pt-9 text-center outline-none [&::-webkit-details-marker]:hidden">
                  <span aria-hidden="true" className="absolute left-1/2 top-3 size-3.5 -translate-x-1/2 rounded-full border-2 border-foreground/30 bg-background" />
                  <span className="font-mono text-[10px] font-semibold tracking-[0.25em] text-primary">TAG {String(index + 1).padStart(2, "0")}</span>
                  <span className="text-base font-bold text-foreground">{item.question}</span>
                  <Plus className="size-4 text-primary transition-transform duration-300 group-open:rotate-45" aria-hidden="true" />
                </summary>
                <p className="border-t border-dashed border-border px-6 pb-6 pt-4 text-center text-sm leading-relaxed text-muted-foreground">
                  {item.answer}
                </p>
              </details>
            </Reveal>
          ))}
        </ul>
      </div>
    </section>
  );
}
