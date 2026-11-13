"use client";

import { useEffect, useId, useRef, useState } from "react";
import { Loader2, MessageCircleQuestion, SendHorizontal, Sparkles, X } from "lucide-react";
import { cn } from "cn";
import { Button } from "@/components/ui/button";
import {
  AssistantError,
  MAX_HISTORY_TURNS,
  MAX_MESSAGE_LENGTH,
  sendAssistantMessage,
  type AssistantRole,
} from "@/lib/assistant";

interface ChatMessage {
  id: number;
  role: AssistantRole;
  text: string;
  /** Errors and the fallback reply are shown, but never sent back as conversation history. */
  failed?: boolean;
}

const SUGGESTIONS = ["Where is my parcel?", "How much to send 2 kg to Kandy?", "Which cities do you deliver to?"];

const GREETING =
  "Hi! I can find your parcel from its tracking number, or estimate what a delivery costs. What would you like to know?";

/**
 * The AI delivery assistant (BON-2, ADR 0010): a chat panel that asks the backend, which answers
 * only from public tracking and pricing data. The conversation lives in this component only; it is
 * not stored anywhere.
 */
export function AssistantWidget() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const nextId = useRef(1);
  const input = useRef<HTMLInputElement>(null);
  const end = useRef<HTMLDivElement>(null);
  const panelId = useId();

  useEffect(() => {
    if (open) {
      input.current?.focus();
    }
  }, [open]);

  useEffect(() => {
    end.current?.scrollIntoView({ block: "end" });
  }, [messages, busy, open]);

  function add(role: AssistantRole, text: string, failed = false) {
    setMessages((current) => [...current, { id: nextId.current++, role, text, failed }]);
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
    add("USER", text);
    setBusy(true);
    try {
      const answer = await sendAssistantMessage(text, history);
      add("ASSISTANT", answer.reply, answer.unavailable);
    } catch (failure) {
      add("ASSISTANT", failure instanceof AssistantError ? failure.message : "Something went wrong. Please try again.", true);
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
          className="fixed inset-x-3 bottom-24 z-50 flex h-[32rem] max-h-[calc(100dvh-7rem)] flex-col overflow-hidden rounded-3xl border border-border bg-card shadow-2xl sm:inset-x-auto sm:right-6 sm:w-[24rem] motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-bottom-4"
        >
          <header className="flex items-center gap-3 bg-primary px-4 py-3 text-primary-foreground">
            <span className="grid size-9 place-items-center rounded-full bg-primary-foreground/15">
              <Sparkles className="size-4" aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-bold leading-tight">SwiftDrop Assistant</p>
              <p className="text-xs text-primary-foreground/80">Tracking and delivery prices</p>
            </div>
            <button type="button" onClick={() => setOpen(false)} aria-label="Close assistant"
              className="rounded-full p-1.5 hover:bg-primary-foreground/15 focus-visible:outline-2 focus-visible:outline-primary-foreground">
              <X className="size-4" aria-hidden="true" />
            </button>
          </header>

          <div className="flex-1 space-y-3 overflow-y-auto px-4 py-4" role="log" aria-live="polite" aria-label="Conversation">
            <Bubble role="ASSISTANT" text={GREETING} />
            {messages.length === 0 && (
              <div className="flex flex-wrap gap-2 pt-1">
                {SUGGESTIONS.map((suggestion) => (
                  <button key={suggestion} type="button" onClick={() => send(suggestion)}
                    className="rounded-full border border-primary/30 bg-accent px-3 py-1.5 text-xs font-semibold text-primary hover:bg-primary/10">
                    {suggestion}
                  </button>
                ))}
              </div>
            )}
            {messages.map((message) => (
              <Bubble key={message.id} role={message.role} text={message.text} failed={message.failed} />
            ))}
            {busy && (
              <div className="flex items-center gap-2 text-xs text-muted-foreground" role="status">
                <Loader2 className="size-3.5 animate-spin" aria-hidden="true" /> Checking…
              </div>
            )}
            <div ref={end} />
          </div>

          <form
            className="border-t border-border p-3"
            onSubmit={(event) => {
              event.preventDefault();
              void send(draft);
            }}
          >
            <div className="flex items-center gap-2 rounded-2xl border border-border bg-background p-1.5 focus-within:ring-2 focus-within:ring-ring/50">
              <label htmlFor={`${panelId}-input`} className="sr-only">Your message</label>
              <input
                id={`${panelId}-input`}
                ref={input}
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                maxLength={MAX_MESSAGE_LENGTH}
                placeholder="Ask about a parcel or a price…"
                autoComplete="off"
                className="h-9 min-w-0 flex-1 bg-transparent px-2 text-sm outline-none placeholder:text-muted-foreground"
              />
              <Button type="submit" size="icon-lg" disabled={busy || !draft.trim()} aria-label="Send message">
                <SendHorizontal aria-hidden="true" />
              </Button>
            </div>
            {draft.length > MAX_MESSAGE_LENGTH - 100 && (
              <p className="mt-1 text-right text-[11px] text-muted-foreground">{draft.length}/{MAX_MESSAGE_LENGTH}</p>
            )}
          </form>
        </section>
      )}

      <button
        type="button"
        onClick={() => setOpen((current) => !current)}
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        className="fixed bottom-6 right-6 z-50 flex h-14 items-center gap-2 rounded-full bg-primary px-5 text-sm font-bold text-primary-foreground shadow-lg transition-transform hover:scale-105 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary motion-reduce:transition-none"
      >
        {open ? <X className="size-5" aria-hidden="true" /> : <MessageCircleQuestion className="size-5" aria-hidden="true" />}
        <span className="hidden sm:inline">{open ? "Close" : "Ask SwiftDrop"}</span>
        <span className="sr-only sm:hidden">{open ? "Close assistant" : "Open assistant"}</span>
      </button>
    </>
  );
}

function Bubble({ role, text, failed }: { role: AssistantRole; text: string; failed?: boolean }) {
  const mine = role === "USER";
  return (
    <div className={cn("flex", mine && "justify-end")}>
      <p className={cn(
        "max-w-[85%] whitespace-pre-wrap break-words rounded-2xl px-3.5 py-2 text-sm leading-relaxed",
        mine ? "rounded-br-md bg-primary text-primary-foreground"
          : failed ? "rounded-bl-md border border-destructive/30 bg-destructive/5 text-destructive"
            : "rounded-bl-md bg-muted text-foreground",
      )}>
        {text}
      </p>
    </div>
  );
}
