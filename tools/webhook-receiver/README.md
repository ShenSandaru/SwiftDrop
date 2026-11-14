# Demo webhook receiver

A minimal SwiftDrop webhook receiver for local demos and for testing an integration (SD-413). It does what
a merchant's endpoint must do: verify the signature over the raw body, reject old timestamps, ignore
duplicate event IDs and apply only the highest `sequence` per parcel. See
[the merchant integration guide](../../docs/merchant-integration-guide.md) for the full contract.

It is a single Java file with no dependencies and runs on the JDK 21 the backend already needs. It listens
on `127.0.0.1` only.

## Run it

```powershell
java tools/webhook-receiver/WebhookReceiver.java --port 9000 --secret whsec_...
```

| Option | Default | Meaning |
|---|---|---|
| `--secret` | required | The endpoint's signing secret, shown once when the endpoint is created |
| `--port` | `9000` | Port on `127.0.0.1` |
| `--path` | `/hook` | Path that receives webhooks |
| `--fail-first N` | `0` | Answer `500` to the first N valid requests to show retries. Applied before an event is recorded, so the retry of that event is processed normally |

Each request prints one line: `RECEIVED`, `DUPLICATE`, `STALE` (an older sequence than one already
applied), `FAILING` (simulated) or `REJECTED` (bad signature or timestamp, answered `401`).

## Connect it to a local backend

The backend only sends webhooks to public `https` addresses. For this demo, run the backend and the
receiver on the same machine (decision D-21, option a) and allow loopback in your `.env`:

```
SWIFTDROP_WEBHOOK_ALLOW_LOCAL=true
```

Restart the backend, then, as an approved merchant, create a webhook endpoint with the URL
`http://localhost:9000/hook` and the statuses you want, and start the receiver with the secret from the
response. Place an order through the merchant order API: the receiver prints `RECEIVED ... status=CREATED`.

## Demo retries and manual redelivery quickly

With the normal settings a failing webhook is retried 8 times over about an hour. For a local demo only,
add to your `.env` and restart the backend:

```
SWIFTDROP_MERCHANT_WEBHOOK_BACKOFF_BASE=1s
SWIFTDROP_MERCHANT_WEBHOOK_MAX_ATTEMPTS=3
```

Start the receiver with `--fail-first 3`: attempts 1 to 3 get `500` and the delivery becomes `FAILED`
within seconds. Then redeliver it:

```powershell
curl.exe -X POST http://localhost:8080/api/v1/merchants/me/webhook-deliveries/<delivery id>/redeliveries `
  -H "Authorization: Bearer <merchant token>"
```

Attempt 4 (series 1) succeeds and the receiver prints the event. `GET
/api/v1/merchants/me/webhook-deliveries/<delivery id>` shows series 0 (three `500`s) and series 1 (`200`)
with who requested it. Remove the two settings afterwards; never use them on a shared or production system.
