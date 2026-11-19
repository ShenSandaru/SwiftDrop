import { deleteToken, getToken } from "firebase/messaging";
import { FIREBASE_VAPID_KEY, getFirebaseMessaging, isFirebaseConfigured } from "./firebase";
import { registerPushToken, unregisterPushToken } from "./notifications";

export type PushNotificationStatus =
  | "unsupported"
  | "denied"
  | "idle"
  | "requesting"
  | "enabled"
  | "error";

const STORAGE_TOKEN_KEY = "swiftdrop_fcm_token";

function buildServiceWorkerUrl(): string {
  const params = new URLSearchParams({
    apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY ?? "",
    authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN ?? "",
    projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID ?? "",
    storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET ?? "",
    messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID ?? "",
    appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID ?? "",
  });
  return `/firebase-messaging-sw.js?${params.toString()}`;
}

export async function getInitialPushStatus(): Promise<PushNotificationStatus> {
  if (typeof window === "undefined" || !("Notification" in window) || !("serviceWorker" in navigator)) {
    return "unsupported";
  }
  const messaging = await getFirebaseMessaging();
  if (!messaging || !FIREBASE_VAPID_KEY) {
    return "unsupported";
  }
  if (Notification.permission === "denied") {
    return "denied";
  }
  if (Notification.permission === "granted" && localStorage.getItem(STORAGE_TOKEN_KEY)) {
    return "enabled";
  }
  return "idle";
}

async function waitForActiveServiceWorker(
  registration: ServiceWorkerRegistration,
): Promise<ServiceWorkerRegistration> {
  if (registration.active) {
    return registration;
  }

  const worker = registration.installing ?? registration.waiting;
  if (worker) {
    await new Promise<void>((resolve, reject) => {
      if (worker.state === "activated") {
        resolve();
        return;
      }
      if (worker.state === "redundant") {
        reject(new Error("Service worker became redundant before activation."));
        return;
      }
      const onStateChange = () => {
        if (worker.state === "activated") {
          worker.removeEventListener("statechange", onStateChange);
          resolve();
        } else if (worker.state === "redundant") {
          worker.removeEventListener("statechange", onStateChange);
          reject(new Error("Service worker became redundant before activation."));
        }
      };
      worker.addEventListener("statechange", onStateChange);
    });
  }

  return navigator.serviceWorker.ready;
}

export async function enablePushNotifications(): Promise<{
  status: PushNotificationStatus;
  error?: string;
}> {
  if (typeof window === "undefined" || !("Notification" in window) || !("serviceWorker" in navigator)) {
    return { status: "unsupported", error: "Push notifications are not supported in this browser." };
  }
  if (!isFirebaseConfigured() || !FIREBASE_VAPID_KEY) {
    return { status: "unsupported", error: "Push notifications are not configured." };
  }

  const messaging = await getFirebaseMessaging();
  if (!messaging) {
    return { status: "unsupported", error: "Push notifications are not supported in this browser." };
  }

  const permission = await Notification.requestPermission();
  if (permission === "denied") {
    return { status: "denied", error: "Notification permission was denied." };
  }
  if (permission !== "granted") {
    return { status: "idle" };
  }

  try {
    const registration = await navigator.serviceWorker.register(buildServiceWorkerUrl(), {
      scope: "/",
    });
    const activeRegistration = await waitForActiveServiceWorker(registration);
    const token = await getToken(messaging, {
      vapidKey: FIREBASE_VAPID_KEY,
      serviceWorkerRegistration: activeRegistration,
    });
    if (!token) {
      return { status: "error", error: "Failed to obtain push registration token." };
    }

    await registerPushToken(token, "WEB");
    localStorage.setItem(STORAGE_TOKEN_KEY, token);
    return { status: "enabled" };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to enable push notifications.";
    return { status: "error", error: message };
  }
}

export async function disablePushNotifications(): Promise<{
  status: PushNotificationStatus;
  error?: string;
}> {
  const storedToken = typeof window !== "undefined" ? localStorage.getItem(STORAGE_TOKEN_KEY) : null;

  try {
    if (storedToken) {
      await unregisterPushToken(storedToken);
      localStorage.removeItem(STORAGE_TOKEN_KEY);
    }

    const messaging = await getFirebaseMessaging();
    if (messaging) {
      await deleteToken(messaging).catch(() => false);
    }
    return { status: "idle" };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to disable push notifications.";
    return { status: "error", error: message };
  }
}

export async function cleanupPushOnLogout(): Promise<void> {
  if (typeof window === "undefined") {
    return;
  }
  const storedToken = localStorage.getItem(STORAGE_TOKEN_KEY);
  if (!storedToken) {
    return;
  }
  try {
    await unregisterPushToken(storedToken);
    localStorage.removeItem(STORAGE_TOKEN_KEY);
    const messaging = await getFirebaseMessaging();
    if (messaging) {
      await deleteToken(messaging).catch(() => false);
    }
  } catch {
    // Keep storedToken in localStorage if backend unregistration failed so it can be retried/reassigned
  }
}
