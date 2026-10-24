# Architecture decision records

An ADR records one significant decision: its context, the decision itself and its consequences.
ADRs are reviewed in pull requests like code.

## Index

| ADR | Title | Status |
|---|---|---|
| [0001](0001-modular-monolith-in-a-monorepo.md) | Modular monolith in a monorepo | Accepted |
| [0002](0002-technology-and-version-baseline.md) | Technology and version baseline | Accepted |

## Process

1. Copy the template below into `NNNN-short-title.md` using the next free number.
2. Set the status to `Proposed` and open a pull request.
3. Once approved, set the status to `Accepted` and add the ADR to the index.
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
