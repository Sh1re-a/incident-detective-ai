# Documentation guide

The main [project README](../README.md) is the recruiter and evaluator entry
point. This index separates current system design from dated implementation
evidence so historical reports are not mistaken for permanent deployment state.

## Current design

- [Engineering decisions](DECISIONS.md) — architecture choices and trade-offs
- [AI system card](AI-SYSTEM-CARD.md) — intended use, limitations, and safety boundaries
- [Incident Lab backend](INCIDENT-LAB-BACKEND-V2.md) — current bounded incident flow
- [Nordly company knowledge](NORDLY-COMPANY-KNOWLEDGE.md) — synthetic company world and governed corpus
- [Frontend/API handoff](FRONTEND-API-HANDOFF.md) — UI/backend contract
- [Cloud logging](CLOUD-LOGGING.md) — structured runtime event design

## Interfaces

- [API walkthrough](API-WALKTHROUGH.md)
- [Day-one contracts](DAY-1-CONTRACTS.md)

## Verification and historical evidence

These documents describe the named revision and date only. They are useful
engineering evidence, not claims that cost-bearing cloud capacity or the same
deployment remains active today.

- [Backend hardening gate — 2026-09-14](BACKEND-HARDENING-GATE-2026-09-14.md)
- [Incident Lab live smoke — 2026-09-14](INCIDENT-LAB-V2-LIVE-SMOKE-2026-09-14.md)
- [Incident Lab golden replay capture — 2026-09-16](INCIDENT-LAB-GOLDEN-REPLAY-CAPTURE-2026-09-16.md)
- [Private VPC, Vertex AI, and Cloud SQL smoke — 2026-09-16](PRIVATE-VPC-VERTEX-CLOUD-SQL-SMOKE-2026-09-16.md)
- [Vertex and Cloud SQL connector smoke — 2026-09-16](VERTEX-CLOUD-SQL-CONNECTOR-SMOKE-2026-09-16.md)
- [Historical cache/cost smoke — 2026-08-26](CACHE-COST-SMOKE-2026-08-26.md)

## Learning record

- [Learning path](LEARNING-PATH.md)
- [Google Cloud path](GOOGLE-CLOUD-PATH.md)

The documents are kept because visible constraints, failures, and changed
decisions are part of the engineering record. When two documents differ, prefer
the newer dated report for that specific run and the current design documents
for intended behavior.
