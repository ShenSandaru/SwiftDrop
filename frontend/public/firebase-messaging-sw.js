importScripts("https://www.gstatic.com/firebasejs/13.3.0/firebase-app-compat.js");
importScripts("https://www.gstatic.com/firebasejs/13.3.0/firebase-messaging-compat.js");

const urlParams = new URLSearchParams(self.location.search);
const firebaseConfig = {
  apiKey: urlParams.get("apiKey") || "",
  authDomain: urlParams.get("authDomain") || "",
  projectId: urlParams.get("projectId") || "",
  storageBucket: urlParams.get("storageBucket") || "",
  messagingSenderId: urlParams.get("messagingSenderId") || "",
  appId: urlParams.get("appId") || "",
};

if (firebaseConfig.apiKey && firebaseConfig.projectId && firebaseConfig.appId) {
  firebase.initializeApp(firebaseConfig);
  const messaging = firebase.messaging();

  messaging.onBackgroundMessage((payload) => {
    // Firebase automatically displays notifications that include a `notification` payload.
    // Only manually show one for data-only messages to avoid duplicates.
    if (payload.notification) {
      return;
    }
    const title = payload.data?.title || "SwiftDrop Notification";
    const options = {
      body: payload.data?.body || "",
      data: payload.data || {},
    };
    self.registration.showNotification(title, options);
  });
}

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const data = event.notification.data || {};
  const params = new URLSearchParams();
  if (data.parcelId) {
    params.set("parcelId", data.parcelId);
  }
  if (data.trackingNumber) {
    params.set("trackingNumber", data.trackingNumber);
  }
  const query = params.toString();
  const targetUrl = query ? `/notifications?${query}` : "/notifications";

  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ("navigate" in client && "focus" in client) {
          return client.navigate(targetUrl).then((c) => (c ? c.focus() : undefined));
        }
        if ("focus" in client) {
          return client.focus();
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(targetUrl);
      }
      return undefined;
    }),
  );
});
