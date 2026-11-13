"use client";

import { useEffect, useId, useRef, useState } from "react";
import { Map as MapIcon, MapPinned, MessageCircleQuestion, Package, PackagePlus, ReceiptText, RotateCcw, SendHorizontal, X } from "lucide-react";
import { cn } from "cn";
import { Button } from "@/components/ui/button";
import {
  AssistantError,
  MAX_HISTORY_TURNS,
  MAX_MESSAGE_LENGTH,
  sendAssistantMessage,
  type AssistantCard,
  type AssistantRole,
} from "@/lib/assistant";
import { AssistantCardView } from "./assistant-cards";
import { RichText } from "./rich-text";

interface ChatMessage {
  id: number;
  role: AssistantRole;
  text: string;
  cards?: AssistantCard[];
  /** Errors and the fallback reply are shown, but never sent back as conversation history. */
  failed?: boolean;
}

/** Kept for this browser tab only, so the chat survives moving between pages; never sent anywhere. */
const STORAGE_KEY = "swiftdrop.assistant.chat";
const MAX_STORED_MESSAGES = 30;

const STARTERS = [
  { icon: MapPinned, title: "Track a parcel", prompt: "Where is my parcel?" },
  { icon: ReceiptText, title: "Price a delivery", prompt: "How much to send 2 kg from Colombo to Kandy?" },
  { icon: MapIcon, title: "Where we deliver", prompt: "Which cities do you deliver to?" },
  { icon: PackagePlus, title: "How to send", prompt: "How do I send a parcel with SwiftDrop?" },
];

function loadMessages(): ChatMessage[] {
  if (typeof window === "undefined") {
    return [];
  }
  try {
    const stored = JSON.parse(window.sessionStorage.getItem(STORAGE_KEY) ?? "[]");
    return Array.isArray(stored) ? (stored as ChatMessage[]) : [];
  } catch {
    return [];
  }
}

/**
 * The AI delivery assistant (BON-2, ADR 0010) in the Island Route style: a teal "route" header,
 * answers with tracking passes and fare tickets, and a launcher with a first-visit hint. It only
 * talks to the backend, which answers from public tracking and pricing data.
 */
export function AssistantWidget() {
  const [open, setOpen] = useState(false);
  const [hint, setHint] = useState(true);
  const [messages, setMessages] = useState<ChatMessage[]>(loadMessages);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const nextId = useRef(messages.reduce((highest, message) => Math.max(highest, message.id), 0) + 1);
  const input = useRef<HTMLInputElement>(null);
  const end = useRef<HTMLDivElement>(null);
  const panelId = useId();

  useEffect(() => {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(messages.slice(-MAX_STORED_MESSAGES)));
  }, [messages]);

  useEffect(() => {
    if (open) {
      input.current?.focus();
    }
  }, [open]);

  useEffect(() => {
    end.current?.scrollIntoView({ block: "end", behavior: "smooth" });
  }, [messages, busy, open]);

  function add(message: Omit<ChatMessage, "id">) {
    setMessages((current) => [...current, { ...message, id: nextId.current++ }]);
  }

  function toggle() {
    setOpen((current) => !current);
    setHint(false);
  }

  async function send(raw: string) {
    const text = raw.trim();
    if (!text || busy) {
      return;
    }
    const history = messages
      .filter((message) => !message.failed)
      .slice(-MAX_HISTORY_TURNS)
      .map(({ role, text: turn }) => ({ role, text: turn }));
    setDraft("");
    add({ role: "USER", text });
    setBusy(true);
    try {
      const answer = await sendAssistantMessage(text, history);
      add({ role: "ASSISTANT", text: answer.reply, cards: answer.cards, failed: answer.unavailable });
    } catch (failure) {
      add({
        role: "ASSISTANT",
        text: failure instanceof AssistantError ? failure.message : "Something went wrong. Please try again.",
        failed: true,
      });
    } finally {
      setBusy(false);
      input.current?.focus();
    }
  }

  return (
    <>
      {open && (
        <section
          id={panelId}
          role="dialog"
          aria-label="SwiftDrop assistant"
          onKeyDown={(event) => event.key === "Escape" && setOpen(false)}
          className="fixed inset-0 z-50 flex flex-col overflow-hidden bg-card shadow-2xl sm:inset-auto sm:bottom-24 sm:right-6 sm:h-[36rem] sm:max-h-[calc(100dvh-8rem)] sm:w-[25rem] sm:rounded-3xl sm:border sm:border-border motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-bottom-4 motion-safe:duration-300"
        >
          <Header onReset={() => setMessages([])} canReset={messages.length > 0 && !busy} onClose={() => setOpen(false)} />

          <div
            className="flex-1 space-y-4 overflow-y-auto bg-muted bg-[radial-gradient(circle,color-mix(in_oklch,var(--primary),transparent_88%)_1px,transparent_1px)] [background-size:18px_18px] px-4 py-4"
            role="log" aria-live="polite" aria-label="Conversation"
          >
            {messages.length === 0 ? (
              <Welcome onPick={(prompt) => void send(prompt)} />
            ) : (
              messages.map((message) => <MessageRow key={message.id} message={message} />)
            )}
            {busy && <Typing />}
            <div ref={end} />
          </div>

          <form
            className="border-t border-border bg-card p-3"
            onSubmit={(event) => {
              event.preventDefault();
              void send(draft);
            }}
          >
            <div className="flex items-center gap-2 rounded-2xl border border-border bg-background p-1.5 pl-3 transition-shadow focus-within:border-primary/50 focus-within:ring-3 focus-within:ring-primary/15">
              <label htmlFor={`${panelId}-input`} className="sr-only">Your message</label>
              <input
                id={`${panelId}-input`}
                ref={input}
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                maxLength={MAX_MESSAGE_LENGTH}
                placeholder="Ask about a parcel or a price…"
                autoComplete="off"
                className="h-9 min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
              />
              <Button type="submit" size="icon-lg" className="rounded-xl" disabled={busy || !draft.trim()} aria-label="Send message">
                <SendHorizontal aria-hidden="true" />
              </Button>
            </div>
            <p className="mt-1.5 flex justify-between gap-2 px-1 text-[10.5px] text-muted-foreground">
              <span>Uses public tracking and price data only.</span>
              {draft.length > MAX_MESSAGE_LENGTH - 100 && <span>{draft.length}/{MAX_MESSAGE_LENGTH}</span>}
            </p>
          </form>
        </section>
      )}

      <div className={cn("fixed bottom-6 right-6 z-50 flex flex-col items-end gap-3", open && "max-sm:hidden")}>
        {hint && !open && (
          <div
            className="relative max-w-[15rem] rounded-2xl rounded-br-md border border-border bg-card p-3 pr-8 text-sm shadow-lg motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-bottom-2 motion-safe:duration-500"
            style={{ animationDelay: "1.2s", animationFillMode: "both" }}
          >
            <p className="font-bold text-foreground">Where&apos;s my parcel?</p>
            <p className="text-xs text-muted-foreground">Ask me. I can also price a delivery in seconds.</p>
            <button type="button" onClick={() => setHint(false)} aria-label="Dismiss hint"
              className="absolute right-2 top-2 rounded-full p-1 text-muted-foreground hover:bg-muted hover:text-foreground">
              <X className="size-3.5" aria-hidden="true" />
            </button>
          </div>
        )}
        <button
          type="button"
          onClick={toggle}
          aria-expanded={open}
          aria-controls={open ? panelId : undefined}
          className="group relative flex h-14 items-center gap-2.5 rounded-full bg-primary pl-2 pr-5 text-sm font-bold text-primary-foreground shadow-lg shadow-primary/30 transition-transform hover:-translate-y-0.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary motion-reduce:transition-none"
        >
          <span className="relative grid size-10 place-items-center rounded-full bg-primary-foreground/15">
            {open ? <X className="size-5" aria-hidden="true" /> : <MessageCircleQuestion className="size-5" aria-hidden="true" />}
            {!open && (
              <span className="absolute right-0.5 top-0.5 flex size-2.5">
                <span className="absolute inline-flex size-full rounded-full bg-emerald-300 opacity-75 motion-safe:animate-ping" />
                <span className="relative inline-flex size-2.5 rounded-full bg-emerald-300 ring-2 ring-primary" />
              </span>
            )}
          </span>
          <span>{open ? "Close" : "Ask SwiftDrop"}</span>
        </button>
      </div>
    </>
  );
}

function Header({ onReset, canReset, onClose }: { onReset: () => void; canReset: boolean; onClose: () => void }) {
  return (
    <header className="relative overflow-hidden bg-primary px-4 pb-8 pt-[max(1rem,env(safe-area-inset-top))] text-primary-foreground">
      {/* The Island Route motif: a dashed route with stops across the header */}
      <svg className="pointer-events-none absolute inset-x-0 bottom-0 h-7 w-full opacity-30" viewBox="0 0 400 40" preserveAspectRatio="none" aria-hidden="true">
        <path d="M0 30 C 80 6, 140 38, 210 20 S 330 4, 400 18" fill="none" stroke="currentColor" strokeWidth="2" strokeDasharray="6 6" />
        <circle cx="70" cy="17" r="3.5" fill="currentColor" />
        <circle cx="210" cy="20" r="3.5" fill="currentColor" />
        <circle cx="340" cy="10" r="3.5" fill="currentColor" />
      </svg>
      <div className="relative flex items-center gap-3">
        <span className="grid size-11 shrink-0 place-items-center rounded-2xl bg-primary-foreground/15 ring-1 ring-primary-foreground/25">
          <Package className="size-5" aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-base font-extrabold leading-tight">SwiftDrop Assistant</p>
          <p className="flex items-center gap-1.5 text-xs text-primary-foreground/85">
            <span className="size-1.5 rounded-full bg-emerald-300" aria-hidden="true" /> Online
          </p>
        </div>
        <button type="button" onClick={onReset} disabled={!canReset} aria-label="Start a new chat" title="New chat"
          className="rounded-full p-2 hover:bg-primary-foreground/15 disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-primary-foreground">
          <RotateCcw className="size-4" aria-hidden="true" />
        </button>
        <button type="button" onClick={onClose} aria-label="Close assistant"
          className="rounded-full p-2 hover:bg-primary-foreground/15 focus-visible:outline-2 focus-visible:outline-primary-foreground">
          <X className="size-4" aria-hidden="true" />
        </button>
      </div>
    </header>
  );
}

function Welcome({ onPick }: { onPick: (prompt: string) => void }) {
  return (
    <div className="space-y-4 motion-safe:animate-in motion-safe:fade-in">
      <div className="flex gap-2.5">
        <Avatar />
        <div className="rounded-2xl rounded-tl-md bg-card px-3.5 py-2.5 text-sm leading-relaxed text-foreground shadow-sm">
          <p className="font-bold">Hi! I&apos;m your SwiftDrop assistant.</p>
          <p className="text-muted-foreground">I can find your parcel, price a delivery, or explain how SwiftDrop works.</p>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-2 pl-10">
        {STARTERS.map(({ icon: Icon, title, prompt }) => (
          <button key={title} type="button" onClick={() => onPick(prompt)}
            className="group flex flex-col items-start gap-1.5 rounded-2xl border border-border bg-card p-3 text-left shadow-sm transition-colors hover:border-primary/50 hover:bg-accent focus-visible:outline-2 focus-visible:outline-primary">
            <span className="grid size-8 place-items-center rounded-xl bg-primary/10 text-primary transition-colors group-hover:bg-primary group-hover:text-primary-foreground">
              <Icon className="size-4" aria-hidden="true" />
            </span>
            <span className="text-xs font-bold text-foreground">{title}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

function MessageRow({ message }: { message: ChatMessage }) {
  if (message.role === "USER") {
    return (
      <div className="flex justify-end motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-bottom-1">
        <p className="max-w-[80%] whitespace-pre-wrap break-words rounded-2xl rounded-br-md bg-primary px-3.5 py-2 text-sm leading-relaxed text-primary-foreground shadow-sm">
          {message.text}
        </p>
      </div>
    );
  }
  return (
    <div className="flex gap-2.5 motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-bottom-1">
      <Avatar />
      <div className="min-w-0 max-w-[85%] space-y-2">
        <div className={cn(
          "break-words rounded-2xl rounded-tl-md px-3.5 py-2.5 text-sm leading-relaxed shadow-sm",
          message.failed ? "border border-destructive/30 bg-destructive/5 text-destructive" : "bg-card text-foreground",
        )}>
          <RichText text={message.text} />
        </div>
        {message.cards?.map((card, index) => <AssistantCardView key={index} card={card} />)}
      </div>
    </div>
  );
}

function Avatar() {
  return (
    <span className="grid size-7.5 shrink-0 place-items-center rounded-full bg-primary text-primary-foreground shadow-sm" aria-hidden="true">
      <Package className="size-3.5" />
    </span>
  );
}

function Typing() {
  return (
    <div className="flex gap-2.5" role="status" aria-label="The assistant is typing">
      <Avatar />
      <div className="flex items-center gap-1 rounded-2xl rounded-tl-md bg-card px-4 py-3 shadow-sm">
        {[0, 150, 300].map((delay) => (
          <span key={delay} className="size-1.5 rounded-full bg-primary/70 motion-safe:animate-bounce" style={{ animationDelay: `${delay}ms` }} />
        ))}
      </div>
    </div>
  );
}
