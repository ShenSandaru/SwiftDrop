# SwiftDrop merchant integration guide

How to connect your shop or order system to SwiftDrop: create delivery orders with an API key, receive
status updates by webhook, verify that they really come from SwiftDrop, and manage deliveries that failed.

All endpoints are under `https://<swiftdrop-host>/api/v1`. Requests and responses are JSON (UTF-8); errors
use [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) (`status`, `title`, `detail`). Times
are ISO-8601 in UTC, for example `2026-11-16T10:15:30Z`.

## 1. Before you start

1. Register your business in the SwiftDrop merchant portal and wait until an administrator **approves**
   it. Until then the merchant API, API keys and webhooks are unavailable.
2. Sign in to the portal. The portal and the management endpoints below use your user login (a bearer
   token from `POST /api/v1/auth/tokens`); only order creation uses an API key.
3. Look up the delivery zones you ship from and to (`GET /api/v1/zones` while signed in). Orders need a
   `zoneId` for pickup and drop-off; a zone that no SwiftDrop hub serves is refused with `409`.

## 2. API keys

Create a key in the portal or with `POST /api/v1/merchants/me/api-keys` and a body `{"label": "Production"}`.
The response contains the full key **once**:

```json
{ "id": "…", "label": "Production", "keyPrefix": "k7m2q9xw4ht1", "apiKey": "sdk_k7m2q9xw4ht1_…", "createdAt": "…" }
```

- Store `apiKey` as a secret in your server's configuration. SwiftDrop keeps only a hash and cannot show it again.
- Send it in the `X-Api-Key` header, server to server only. Never put it in web pages or mobile apps.
- You may have up to 5 active keys. To rotate: create a new key, deploy it, then revoke the old one with
  `DELETE /api/v1/merchants/me/api-keys/{id}`. `GET /api/v1/merchants/me/api-keys` lists keys by prefix.
- Every authentication failure (missing, wrong, revoked key, or your merchant account suspended) answers
  the same `401`.

## 3. Create and read orders

```bash
curl -X POST https://<swiftdrop-host>/api/v1/merchant-orders \
  -H "X-Api-Key: sdk_k7m2q9xw4ht1_…" -H "Content-Type: application/json" \
  -d '{
        "merchantOrderReference": "ORD-10045",
        "pickup":  {"contactName": "Apex Store", "phone": "+94771234567", "line1": "12 Main St",
                    "city": "Colombo", "zoneId": "<zone id>"},
        "dropoff": {"contactName": "Nimal Perera", "phone": "+94779876543", "line1": "5 Hill Rd",
                    "line2": "Apt 3", "city": "Kandy", "zoneId": "<zone id>", "postalCode": "20000"},
        "size": "MEDIUM",
        "weightGrams": 1200,
        "description": "Shoes"
      }'
```

| Field | Rules |
|---|---|
| `merchantOrderReference` | Your order ID, unique per merchant: letters, digits and `. _ : -`, starts with a letter or digit, at most 100 characters |
| `pickup`, `dropoff` | `contactName` (≤ 200), `phone` in E.164 (`+94771234567`), `line1` (≤ 200), optional `line2` (≤ 200), `city` (≤ 100), `zoneId`, optional `postalCode` (≤ 10) |
| `size` | `SMALL`, `MEDIUM` or `LARGE` |
| `weightGrams` | 1 to 30,000 |
| `description` | optional, ≤ 200 |

- `201 Created` with `id`, `trackingNumber`, `status` (`CREATED`), `fee` (`{"amount": "650.00", "currency":
  "LKR"}`) and a `Location` header.
- **Safe to retry:** sending the same `merchantOrderReference` again returns `200 OK` with the existing
  order instead of creating a second parcel. Retry on network errors with the same reference.
- `400` for invalid fields (`invalidFields` lists them), `409` when a zone is not served or no price exists.
- `GET /api/v1/merchant-orders/{merchantOrderReference}` returns the order's current `status`; another
  merchant's reference answers `404`.

## 4. Parcel statuses

`CREATED`, `PICKED_UP`, `AT_HUB`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERY_ATTEMPT_FAILED`, `DELIVERED`,
`RETURNING`, `RETURNED`, `CANCELLED`, `FAILED`. A parcel can pass several hubs (`AT_HUB` and `IN_TRANSIT`
repeat) and have several delivery attempts. `DELIVERED`, `RETURNED`, `CANCELLED` and `FAILED` are final.
Do not assume a fixed list or order: new statuses may be added.

## 5. Webhooks

### 5.1 Register an endpoint

`POST /api/v1/merchants/me/webhook-endpoints` with:

```json
{ "url": "https://shop.example.com/swiftdrop/webhooks", "description": "Order updates",
  "events": ["CREATED", "PICKED_UP", "AT_HUB", "OUT_FOR_DELIVERY", "DELIVERED", "DELIVERY_ATTEMPT_FAILED", "RETURNED"] }
```

- The URL must be **public `https` on port 443**. Private, loopback and other internal addresses are refused,
  also if your host name resolves to one of them later; redirects are not followed.
- `events` are the statuses you want; you only receive webhooks for these.
- The response contains the **signing secret** (`whsec_…`) **once**. Store it with your server's secrets.
  `POST /api/v1/merchants/me/webhook-endpoints/{id}/secret-rotations` issues a new one.
- `PATCH …/{id}` changes the URL, description, events or `enabled`; `DELETE …/{id}` removes it.

### 5.2 What you receive

One `POST` per event and endpoint, with a JSON body:

```json
{
  "eventId": "0f4b2e9a-…",
  "eventType": "parcel.status-changed",
  "schemaVersion": 1,
  "occurredAt": "2026-11-16T10:15:30Z",
  "producer": "delivery",
  "data": {
    "parcelId": "5a0e…", "trackingNumber": "SD7K3M9Q2XWB",
    "previousStatus": "PICKED_UP", "newStatus": "AT_HUB", "sequence": 3,
    "source": "MERCHANT", "ownerCustomerId": null, "ownerMerchantId": "77aa…",
    "merchantOrderReference": "ORD-10045",
    "failureReasonCode": null, "hubCode": "CMB", "attempt": null
  }
}
```

- `sequence` increases by one per status change of a parcel. `failureReasonCode` is set for `FAILED` and
  `DELIVERY_ATTEMPT_FAILED`; `hubCode` for `AT_HUB` and `IN_TRANSIT`; `attempt` for `OUT_FOR_DELIVERY` and
  `DELIVERY_ATTEMPT_FAILED`. **Ignore fields you do not know**: optional fields are added without a new
  `schemaVersion`.
- Headers: `X-SwiftDrop-Event-Id`, `X-SwiftDrop-Event-Type`, `X-SwiftDrop-Timestamp` (Unix seconds) and
  `X-SwiftDrop-Signature: v1=<hex>`.
- Answer with any `2xx` within 10 seconds. Do the real work asynchronously if it takes longer.

### 5.3 Verify every request

1. Read the **raw body bytes** before parsing JSON.
2. Compute HMAC-SHA256 with your signing secret (its UTF-8 bytes, including `whsec_`) over
   `"<X-SwiftDrop-Timestamp>." + raw body`, as lowercase hex, and compare it with the part after `v1=`
   using a **constant-time** comparison.
3. Reject the request (`401`) if the timestamp is more than **5 minutes** from your clock.
4. **Deduplicate by `X-SwiftDrop-Event-Id`**: the same event can arrive more than once (retries, redelivery).
   Answer `2xx` to a duplicate without applying it again.
5. **Apply the highest `sequence` per parcel**: retries can arrive out of order; do not let an older event
   overwrite a newer status.

Java:

```java
boolean verify(String signatureHeader, String timestampHeader, byte[] rawBody, String secret) throws Exception {
    long timestamp = Long.parseLong(timestampHeader);
    if (Math.abs(Instant.now().getEpochSecond() - timestamp) > 300 || !signatureHeader.startsWith("v1=")) {
        return false;
    }
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
    byte[] expected = mac.doFinal(rawBody);
    return MessageDigest.isEqual(expected, HexFormat.of().parseHex(signatureHeader.substring(3)));
}
```

Node.js:

```js
import crypto from "node:crypto";

function verify(signatureHeader, timestampHeader, rawBody /* Buffer */, secret) {
  const timestamp = Number(timestampHeader);
  if (!signatureHeader?.startsWith("v1=") || Math.abs(Date.now() / 1000 - timestamp) > 300) return false;
  const expected = crypto.createHmac("sha256", secret)
    .update(`${timestamp}.`).update(rawBody).digest();
  const presented = Buffer.from(signatureHeader.slice(3), "hex");
  return presented.length === expected.length && crypto.timingSafeEqual(presented, expected);
}
```

Python:

```python
import hashlib, hmac, time

def verify(signature_header: str, timestamp_header: str, raw_body: bytes, secret: str) -> bool:
    timestamp = int(timestamp_header)
    if not signature_header.startswith("v1=") or abs(time.time() - timestamp) > 300:
        return False
    expected = hmac.new(secret.encode(), f"{timestamp}.".encode() + raw_body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature_header[3:])
```

### 5.4 Retries

| Your answer | What SwiftDrop does |
|---|---|
| `2xx` | Delivered |
| timeout (10 s), connection error, `5xx`, `408`, `429` | Retries: after 30 s, then doubling (±20 %), at most 1 hour apart, 8 attempts in total. `Retry-After` on `429`/`503` is honored up to 1 hour |
| `3xx` (redirects are not followed), other `4xx` | Stops: the delivery is `FAILED` |

If your endpoint is disabled or deleted, or your merchant account is suspended, pending deliveries end as
`FAILED` without a request.

## 6. Delivery history and redelivery

All with your portal login:

- `GET /api/v1/merchants/me/webhook-deliveries?endpointId=&status=&page=0&size=20` – your deliveries, newest
  first: event type, status (`PENDING`, `IN_PROGRESS`, `SUCCEEDED`, `FAILED`, `SECRET_UNAVAILABLE`), attempts,
  last HTTP status, next retry.
- `GET /api/v1/merchants/me/webhook-deliveries/{id}` – one delivery with its attempt **series**: series 0 is
  the automatic one; each manual redelivery is a new series with who requested it and when. Attempts show
  time, outcome category, HTTP status and duration (no bodies).
- `POST /api/v1/merchants/me/webhook-deliveries/{id}/redeliveries` – sends a `FAILED` (or
  `SECRET_UNAVAILABLE`) delivery again: same body and event ID, a fresh timestamp and signature with your
  endpoint's current secret, and a new series of up to 8 attempts. Answers `202` with the series number.
  - Allowed **3 times** per delivery; the 4th answers `409`.
  - `409` while a series is still running, for a delivery already `SUCCEEDED`, or when the endpoint is
    disabled or deleted. Another merchant's delivery answers `404`.

Because a redelivery carries the original event ID, your deduplication recognizes it if you had already
processed the event.

## 7. Test locally

Run SwiftDrop and the demo receiver in [`tools/webhook-receiver`](../tools/webhook-receiver/README.md) on the
same machine; it verifies signatures exactly as described above and prints every event, and can simulate
failures to show retries and redelivery.
