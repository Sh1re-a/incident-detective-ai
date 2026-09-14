# Incident Lab v2 live smoke — 2026-09-14

Status: historical live evidence with honest success, rejection and provider
failure outcomes; no run is presented as more than it proved

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

## Post-v7 observation — incomplete live receipt

After the general abstention contract was updated to prompt v7 and diagnosis
schema v5, one separate catalog request was sent with a direct canonical plan.
The planner was not part of this request.

- The request used a new server-generated seed and explicitly selected
  `insufficient_evidence`.
- The endpoint returned HTTP 502 after approximately two seconds.
- The command used fail-on-HTTP-error behavior, so the sanitized response body
  and backend error code were not retained.
- No automatic retry was made.
- The application health endpoint still reported `UP` after the response.

Because the response body was not captured, this observation cannot establish
whether the failure was a provider error, a rejected model response, invalid
tool arguments or another documented 502 path. It also cannot prove whether
the ADK agents or read tools had started. This is therefore evidence of an
honest failed request, not a completed post-v7 validation and not evidence that
prompt v7 caused the failure.

The next live validation must capture both HTTP status and the sanitized JSON
body without printing secrets. It remains a separate, explicit cost-bearing
run; it must not be triggered as an automatic retry.

## Backend-hardening addendum — captured planner failure

A later, independent planner request captured both status and sanitized body.
It returned HTTP 502 with:

- `code=MODEL_PROVIDER_ERROR`;
- title `Incident planner failed`;
- detail `Gemini could not complete the bounded planning request.`

No automatic retry and no fabricated plan followed. Because this endpoint did
not produce a plan receipt, the result does not prove which provider-internal
stage failed. It proves that the API exposed a bounded, non-secret failure
instead of pretending that planning succeeded.

## Backend-hardening addendum — separate canonical run

To isolate the investigation path from the failed planner, one separate direct
canonical run was explicitly submitted. It was not a planner retry and must not
be presented as though the failed planner produced its plan.

### Generated case and alarm

- Contract: `incident-lab-run-v3`.
- HTTP status: 200.
- Outcome: `alarm_detected_investigation_withheld`.
- Answer state: `withheld`.
- Family: `catalog_cache_invalidation`.
- Evidence mode: `diagnostic`.
- Seed origin: `server_generated`.
- Seed: `6907494064764568081`.
- Scenario: `generated-catalog-cache-invalidation-6e8ed80aa67f5ab1`.
- Variant: `catalog-cache-invalidation-ad6eff707311d3b0`.
- Evidence fingerprint:
  `ad6eff707311d3b0f9b10cab312ebc460f7374f7e7f5c9f60b7e9180527d3f1b`.
- Alarm rule: `CATALOG_VERSION_DIVERGENCE_V1`.
- Observed signal: 2 divergent catalog versions against an at-least-1
  threshold.

### Observed ADK, tool and RAG receipt

- Nested contract: `nordly-adk-turn-v4`.
- ADK run ID: `517b9f34-6f91-4827-92d9-ec6bda5acc70`.
- Framework: Google ADK for Java 1.7.0.
- Observed agent order:
  `nordly_evidence_agent` then `nordly_diagnosis_agent`.
- Handoff: ADK function response.
- Observed generation model: `gemini-3.1-flash-lite`.
- Model calls: 2.
- ADK function calls: 1.
- Backend read operations: 4.
- Embedding calls: 1.
- Retrieval: `pgvector_exact_cosine` over `runbook-corpus-v1`.
- Embedding model: `gemini-embedding-2`, 768 dimensions.
- Top similarities: 0.7845443651889121 and 0.7349866521263488.
- Query-embedding latency: 419 ms.
- Diagnostic probe: `service_health`, read-only, `action_executed=false`.
- Total investigation latency: 7,634 ms.
- Estimated generation cost: USD 0.002177. This is a list-price estimate,
  excludes planner cost and is not a provider invoice.

The request-local backend contained four synthetic logs. The model-selected log
query returned zero direct log matches, while the read-only service-health probe
cited the generated catalog error log and failure trace. This exposed a UI
projection gap: probe-cited logs were not included in
`highlighted_log_evidence_ids`. The backend mapping was fixed afterward and is
covered by automated tests; no second paid request was used to hide the original
receipt.

### Release decision

The ADK sequence, handoff, tool boundary, final author, schema, citation IDs and
direct evidence support passed. The candidate did not match the generated
case's factual diagnosis. Java therefore returned:

- `diagnosis=null` and no expected-answer comparison;
- no root cause or affected service as fact;
- the raw final model response withheld from public events;
- backend-authored Swedish and English business/developer projections;
- `write_tools_available=false` and `action_executed=false`;
- a required human approval boundary.

This paid run occurred before the later event-integrity, public-projection,
failure-classification, receipt-reconciliation and provenance hardening commits.
Those changes passed focused tests and the final full local suite. The run is
evidence of the earlier real execution path; it is not evidence that the final
working revision itself made another paid provider call, reached Vertex AI or
was deployed.
