import { getApiBaseUrl } from "@/lib/api";

/** Limits the backend enforces (ADR 0010 §4); the widget stops the user before the server has to. */
export const MAX_MESSAGE_LENGTH = 500;
export const MAX_HISTORY_TURNS = 10;

export type AssistantRole = "USER" | "ASSISTANT";

export interface AssistantTurn {
  role: AssistantRole;
  text: string;
}

export interface AssistantAnswer {
  reply: string;
  /** True when the model could not answer and `reply` is the fixed fallback text. */
  unavailable: boolean;
}

/** An error whose message is safe to show to the visitor. */
export class AssistantError extends Error {}

/**
 * Sends one message with the conversation so far. The endpoint is public, so this never sends the
 * login token: an expired token would be rejected (401) and sign the visitor out.
 */
export async function sendAssistantMessage(message: string, history: AssistantTurn[]): Promise<AssistantAnswer> {
  const base = getApiBaseUrl().replace(/\/+$/, "");
  let response: Response;
  try {
    response = await fetch(`${base}/api/v1/assistant/messages`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message, history: history.slice(-MAX_HISTORY_TURNS) }),
    });
  } catch {
    throw new AssistantError("I can't reach SwiftDrop right now. Check your connection and try again.");
  }
  if (response.status === 429) {
    throw new AssistantError("You're sending messages too fast. Please wait a minute and try again.");
  }
  if (response.status === 400) {
    throw new AssistantError("That message could not be sent. Try a shorter one.");
  }
  if (!response.ok) {
    throw new AssistantError("The assistant is unavailable right now. Please try again in a moment.");
  }
  return (await response.json()) as AssistantAnswer;
}
