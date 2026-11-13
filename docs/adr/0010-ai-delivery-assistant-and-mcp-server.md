# 0010. AI delivery assistant and MCP server (BON-2)

- Status: Proposed
- Date: 2026-11-13

## Context

The brief's bonus BON-2 asks for a Generative AI chatbot that answers customer questions such as
"Where is my parcel?" and "How much to send 2 kg to Kandy?" by calling the backend, and encourages
exposing those calls as MCP tools. The project rules require a separate decision for it
([architecture §13](../architecture.md), D-32 in the implementation contract), because it adds a
module, an external service and a secret.

Constraints:

- Modular monolith: a new module may call another module only through its `api` package, along a
  graph decided in [ADR 0003](0003-backend-modules-and-boundaries.md).
- The backend owns business rules and authorization; the model only chooses a tool and phrases the
  result. It must never be able to read or change more than the endpoints it calls allow.
- No new dependency without approval; `mvnw clean verify` must pass without Docker and without an
  API key.
- The team wants to avoid paid services. Gemini offers a free tier with low rate limits, and Google
  may use free-tier prompts to improve its products, so nothing personal may be sent to it.

## Decision

### 1. Module and dependencies

- A new module `assistant` with the usual packages (`api` only if another module needs it, `web`,
  `application`, `infrastructure`).
- `assistant → delivery, pricing`, through their `api` packages only. No module depends on
  `assistant`. `ModuleBoundaryTest` and ADR 0003's graph are updated in the same change.
- `pricing.api` has no zone lookup today (only `FeeCalculator`). The fee tool needs to turn a city
  name into a zone, so `pricing.api` gains one small read-only method that lists active zones. This
  touches another module's `api` package and needs review by the pricing owner before merging.

### 2. Tools

Each tool is a plain Java class with a name, a description, a JSON-schema for its arguments and a
method that returns a small record. They are public and read-only:

| Tool | Calls | Returns |
|---|---|---|
| `track_parcel(trackingNumber)` | `ParcelQueries.findPublicTrackingView` | Status, route cities, status history (the same data as the public `/track` page) |
| `estimate_fee(fromCity, toCity, weightKg, size)` | zone lookup + `FeeCalculator` | Total fee and breakdown; "route not priced" and unknown cities become readable messages |
| `list_service_areas()` | zone lookup | Active zone names |

No tool returns names, phones or street addresses. Tools that need a logged-in customer
(`list_my_parcels`) are out of scope; adding one needs a new decision about sending personal data
to the model provider.

### 3. Model provider

- **Gemini API, free tier**, using a Flash-class model with function calling. The model name is an
  environment variable (`SWIFTDROP_ASSISTANT_MODEL`), because Google renames and retires models.
  The default is `gemini-3.5-flash-lite`: `gemini-2.5-flash` returned 404 for a new key in
  November 2026 although the API listed it, because Google limits older models to accounts that
  already used them. Gemini 3 models need the `thoughtSignature` of a tool call sent back with its
  result, so the client keeps and returns it.
- Called with Spring's `RestClient` against the `generateContent` REST endpoint. No SDK and no new
  dependency.
- The provider sits behind an `LlmClient` interface with one `GeminiLlmClient`. Tests use a fake
  client, so no test needs a key or network access. Switching provider later is one new class.
- The API key is `SWIFTDROP_GEMINI_API_KEY`: read from the environment, sent in the
  `x-goog-api-key` header (never in the URL), never logged, and only a placeholder in
  `.env.example`.

### 4. Endpoint and limits

- `POST /api/v1/assistant/messages` with `{ "message": "...", "history": [...] }`; the browser
  keeps the conversation, the server keeps no state. It is public, like tracking, because the tools
  expose only public data.
- Validation: message at most 500 characters, history at most 10 turns, reply capped in tokens.
- At most 3 tool calls per message; a loop that wants more is cut off with a plain answer.
- A per-client rate limit (in memory, by IP) on top of Gemini's own quota.
- When Gemini is down, rate-limited or answers badly, the endpoint returns a fixed friendly
  message ("The assistant is unavailable, please use Track Parcel"), not a stack trace.
- Logs contain the tool name, duration and outcome only, never the message text, tracking numbers
  or the key.
- The system prompt tells the model to answer only about SwiftDrop parcels and prices, to use the
  tools for facts instead of guessing, and to say so when it cannot find something. It also holds
  about a dozen plain-text facts on how to book, track and cancel a parcel and on delivery
  attempts, so "how do I send a parcel?" has an answer. This is prompt text, not retrieval (RAG);
  it must be updated when those flows change. Delivery times are not stated because no rule exists.

### 5. MCP server

The same three tools are exposed as MCP tools at `POST /mcp`, so any MCP client (Claude Code,
Claude Desktop, the MCP Inspector, a merchant's own agent) can use them. The client's own model
does the reasoning; SwiftDrop only runs the tools, so MCP calls cost no Gemini quota.

- **Hand-written, no dependency.** Only the tool part of the protocol is needed. The Spring AI MCP
  starter would add a large dependency whose Spring Boot 4 general-availability status was unclear,
  and the plain MCP Java SDK would need a Jackson compatibility review. The endpoint uses the JSON
  support that is already present.
- **Both protocol eras on one endpoint.** Requests whose `params._meta` carries
  `io.modelcontextprotocol/protocolVersion` are served statelessly under **2026-07-28**:
  `server/discover`, `tools/list`, `tools/call` and `ping`, with the required `MCP-Protocol-Version`,
  `Mcp-Method` and (for `tools/call`) `Mcp-Name` headers checked against the body
  (`400`, `-32020` on a mismatch; `-32022` for an unsupported version). An `initialize` request
  selects the legacy flow (**2025-11-25**, also 2025-06-18 and 2025-03-26) without sessions.
- **Plain JSON responses only**, no SSE stream: every request gets one `application/json` reply,
  notifications get `202`, and `GET` or `DELETE` get `405`. One JSON-RPC message per request;
  batches are rejected.
- **Same limits as the chat:** read-only tools, public data only, the per-client rate limit applies
  to `tools/call`. Unknown tools answer `-32602`; a failing or refusing tool answers with
  `isError: true` and a safe message. Logs hold the method and tool name, never arguments.
- **Public like the chat.** A request with an `Origin` header that is not an allowed frontend origin
  is refused with `403` (protection against DNS rebinding). `/mcp` is a protocol endpoint, so it is
  not under `/api/v1/` like the REST resources.

### 6. Frontend

A chat widget on the landing page, on `/track` and on the customer pages; not on the courier,
dispatcher, admin or merchant pages. It only sends text to the endpoint and shows
the reply; it contains no business rules.

## Alternatives considered

- **Claude (Anthropic API).** Better tool-use quality, but it is paid and needs separate API
  credit. Behind the `LlmClient` interface it remains an easy later switch.
- **Spring AI.** Handles tool calling and providers for us, but it is a new dependency, its
  Spring Boot 4 support is unverified, and the abstraction is more than three tools need.
- **Local model (Ollama).** Free and private, but weak tool calling and the demo only works on one
  machine.
- **A rule-based chatbot.** Simple, but it does not meet the "Generative AI" requirement.
- **Authenticated tools from day one.** Useful ("my parcels"), but they send personal data to a
  free tier that may train on it.

## Consequences

- Easier: a demonstrable BON-2 with no new dependency, no cost and no personal data leaving the
  system; the provider can be replaced by one class.
- MCP clients can use the same tools with their own model, at no Gemini cost; the hand-written
  endpoint covers only the tool part of the protocol and must follow future spec revisions.
- Harder: free-tier limits can make the live demo fail, so the fallback message and our own rate
  limit are part of the scope. Answer quality depends on the tool descriptions and needs manual
  testing of the example questions.
- Follow-up work: pricing owner reviews the zone lookup in `pricing.api`; the identity/config
  owner reviews the public endpoint in `SecurityConfiguration` and the `.env.example` entries;
  architecture §13, §14 and D-32 are updated once this ADR is accepted.
- Risks: a prompt-injection attempt can only reach the three read-only public tools; tracking
  numbers are the only guess-able input, and public tracking already exposes the same data.
