"use client";

import { useEffect, useState } from "react";
import {
  getPublicTracking,
  isFinalStatus,
  trackingUrl,
  type ParcelStatus,
  type PublicTracking,
} from "@/lib/delivery";

export type LiveConnection = "loading" | "live" | "reconnecting" | "finished" | "not-found" | "error";

interface StatusUpdate {
  status: ParcelStatus;
  occurredAt: string;
  sequence: number;
}

interface LiveState {
  trackingNumber: string | null;
  view: PublicTracking | null;
  connection: LiveConnection;
}

/**
 * Public tracking view of a parcel, kept up to date over Server-Sent Events (ADR 0007). Loads the
 * current state, then applies live status events until the parcel is delivered or failed.
 *
 * The browser's EventSource reconnects by itself and gets a fresh snapshot each time; the stream is
 * closed here once the parcel is finished, otherwise the browser would keep reconnecting.
 */
export function useLiveTracking(trackingNumber: string | null): { view: PublicTracking | null; connection: LiveConnection } {
  const [state, setState] = useState<LiveState>({ trackingNumber: null, view: null, connection: "loading" });

  useEffect(() => {
    if (!trackingNumber) {
      return;
    }
    let source: EventSource | null = null;
    let cancelled = false;
    const update = (next: Partial<LiveState>) => {
      if (!cancelled) {
        setState((previous) => ({ ...previous, ...next, trackingNumber }));
      }
    };

    // The plain request tells "unknown number" (404) apart from network trouble; EventSource cannot
    getPublicTracking(trackingNumber)
      .then((initial) => {
        if (cancelled) {
          return;
        }
        if (isFinalStatus(initial.status)) {
          update({ view: initial, connection: "finished" });
          return;
        }
        update({ view: initial, connection: "live" });
        source = new EventSource(trackingUrl(trackingNumber, "/events"));

        source.addEventListener("snapshot", (event) => {
          const snapshot = JSON.parse((event as MessageEvent<string>).data) as PublicTracking;
          const finished = isFinalStatus(snapshot.status);
          update({ view: snapshot, connection: finished ? "finished" : "live" });
          if (finished) {
            source?.close();
          }
        });

        source.addEventListener("status", (event) => {
          const change = JSON.parse((event as MessageEvent<string>).data) as StatusUpdate;
          const finished = isFinalStatus(change.status);
          if (!cancelled) {
            setState((previous) => {
              const view = previous.view;
              // Ignore updates the snapshot already contains (history length = latest sequence)
              if (!view || change.sequence <= view.history.length) {
                return previous;
              }
              return {
                trackingNumber,
                connection: finished ? "finished" : "live",
                view: {
                  ...view,
                  status: change.status,
                  history: [...view.history, { status: change.status, occurredAt: change.occurredAt }],
                },
              };
            });
          }
          if (finished) {
            source?.close();
          }
        });

        source.onerror = () => {
          // CONNECTING: the browser retries by itself; CLOSED: it gave up
          update({ connection: source?.readyState === EventSource.CLOSED ? "error" : "reconnecting" });
        };
        source.onopen = () => update({ connection: "live" });
      })
      .catch((error: { status?: number }) => {
        update({ view: null, connection: error?.status === 404 ? "not-found" : "error" });
      });

    return () => {
      cancelled = true;
      source?.close();
    };
  }, [trackingNumber]);

  // State from a previous tracking number is never shown for a new one
  if (!trackingNumber || state.trackingNumber !== trackingNumber) {
    return { view: null, connection: "loading" };
  }
  return { view: state.view, connection: state.connection };
}
