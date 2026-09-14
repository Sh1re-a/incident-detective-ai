# Incident Lab v2 live smoke — 2026-09-14

Status: completed with the model answer withheld

This receipt records one explicit, cost-bearing local smoke. It is kept because
the safe failure is part of the result; it must not be rewritten as a successful
diagnosis.

## Scope and runtime

- Local Spring Boot backend with the `rag` profile.
- Local PostgreSQL 17 with pgvector; the runbook index reported 12/12 chunks
  ready before the run.
- Gemini Developer API, not Vertex AI.
- Real Gemini planning followed by a real Google ADK `SequentialAgent` run.
- Synthetic Nordly data only; no customer or production system was accessed.

## Planning receipt

The Swedish request asked for a new synthetic Nordly case where order events
begin to lag, constrained to local, read-only investigation with human approval.

- Gemini proposed `order_event_backlog` across three services at medium severity.
- Deterministic Java narrowed the runnable scope to
  `ORDER_EVENT_CONSUMER` and canonicalized severity to high.
- Planning latency: 4,124 ms.
- Planning tokens reported by the provider: 612 input and 111 output.

## Generated case and alarm

- Seed origin: `server_generated`.
- Seed: `664177358557935762`.
- Evidence mode: `insufficient_evidence`.
- Scenario: `generated-order-event-backlog-2c2c3f8d804180d6`.
- Variant: `order-event-backlog-3ce7e8e4136f33ba`.
- Replay fingerprint:
  `3ce7e8e4136f33baeed5fa8569386f4b61d8abcd70c1ce5a90c33bb1ab57d4e0`.
- Java alarm rule: `ORDER_CONSUMER_LAG_V1`.
- Observed signal: 800 seconds of consumer lag against a threshold of 600
  seconds.
- The alarm cited the exact triggering metric evidence ID.

## Agent and tool receipt

- Workflow: Google ADK `SequentialAgent`.
- Expected and observed order:
  `nordly_evidence_agent` → `nordly_diagnosis_agent`.
- The evidence handoff came from the ADK function response.
- Model calls: 2.
- ADK function calls: 1.
- Read operations: 4.
- Embedding calls: 1.
- Diagnostic probe: `service_health`.
- Probe outcome: `observed`.
- Probe duration: 2 ms.
- Probe was read-only and executed no action.
- Total investigation latency: 11,286 ms.
- Estimated generation cost: USD 0.002546. This is a local list-price
  estimate, not a provider invoice; it excludes the planner and does not prove
  the amount actually charged.

## Verification result

The ADK sequence, evidence handoff, tool boundary, final author, JSON schema and
citation IDs passed. Deterministic Java rejected direct evidence support for
four of five claim-to-evidence links. The candidate also failed the required
factual abstention match for this deliberately incomplete case.

The public Incident Lab response therefore returned:

- `outcome=alarm_detected_investigation_withheld`;
- `answer_state=withheld`;
- no root-cause code;
- no affected service as a diagnosed fact;
- no ground-truth comparison;
- no proposed or executed action;
- a Swedish business explanation of the alarm and remaining uncertainty;
- a developer explanation naming the failed deterministic checks;
- `human_approval_required=true`.

## What this proves — and does not prove

This run proves that the current request crossed the real planner, Google ADK,
Gemini generation, a real embedding call and pgvector retrieval, and that Java
withheld an unsupported answer without fabricating a fallback. It also proves
that the generated incident was uniquely seeded and can be replayed.

It does not prove diagnosis accuracy, provider stability, production safety,
Vertex AI deployment or a successful insufficient-evidence answer. The next
bounded improvement is a general abstention instruction that reduces
unsupported extra claims across all incomplete incident families. The failed
run remains part of the evidence after that change.
