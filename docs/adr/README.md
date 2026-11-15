# Architecture decision records

An ADR records one significant decision: its context, the decision itself and its consequences.
ADRs are reviewed in pull requests like code. [docs/architecture.md](../architecture.md) shows the
current state of each decision and lists the open questions that still need one.

## Index

| ADR | Title | Status |
|---|---|---|
| [0001](0001-modular-monolith-in-a-monorepo.md) | Modular monolith in a monorepo | Accepted |
| [0002](0002-technology-and-version-baseline.md) | Technology and version baseline | Accepted |
| [0003](0003-backend-modules-and-boundaries.md) | Backend business modules and boundaries | Accepted |
| [0004](0004-messaging-and-integration-reliability.md) | Messaging and integration reliability | Accepted |
| [0005](0005-google-oauth-token-handoff.md) | Google OAuth token handoff | Accepted |
| [0006](0006-delivery-lifecycle-and-transition-rules.md) | Delivery lifecycle and transition rules | Proposed *(implemented in `delivery`; awaiting formal ADR acceptance)* |
| [0007](0007-public-sse-tracking-stream.md) | Public SSE tracking stream | Proposed *(implemented in `tracking`; awaiting formal ADR acceptance)* |
| [0008 (Dispatch)](0008-dispatch-assignment-history-and-handoff.md) | Dispatch assignment history and handoff | Accepted *(shares number 0008 with Merchant API key ADR)* |
| [0008 (Merchant)](0008-merchant-api-key-authentication.md) | Merchant API key authentication | Accepted *(shares number 0008 with Dispatch ADR)* |
| [0009](0009-hub-network-delivery-attempts-and-returns.md) | Hub network, delivery attempts, returns and cancellation | Accepted |
| [0010](0010-ai-delivery-assistant-and-mcp-server.md) | AI delivery assistant and MCP server (BON-2) | Proposed *(implemented in `assistant`; awaiting formal ADR acceptance)* |

## Process

1. Copy the template below into `NNNN-short-title.md` using the next free number.
2. Set the status to `Proposed`, add the ADR to the index with that status, and open a pull request.
   A Proposed ADR is not binding.
3. Only after the decision has been explicitly reviewed and approved, set the status to `Accepted`
   in the ADR and in the index.
4. Do not rewrite accepted ADRs. To change a decision, write a new ADR and mark the old one
   `Superseded by NNNN`. Version patch updates within the same baseline may be edited in place.

## Template

```markdown
# NNNN. Title

- Status: Proposed | Accepted | Superseded by NNNN
- Date: YYYY-MM-DD

## Context

What problem or requirement forces a decision? What constraints apply?

## Decision

What we decided, stated clearly.

## Alternatives considered

Other options and why they were not chosen.

## Consequences

What becomes easier or harder, risks and follow-up work.
```
