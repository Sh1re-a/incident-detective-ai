# Backend hardening gate — 2026-09-14

Status: passed locally for backend integration; frontend migration and cloud
deployment are separate, unperformed steps.

## Decision

The backend is the only authority for dynamic product claims. A future rich
frontend may compose and animate these receipts, but it may not invent logs,
alarm values, AI progress, retrieved documents, model identity, tool calls,
verification results, cost, deployment state or actions.

The runtime is synchronous. While a request is pending, the UI may say only
that it is waiting for the backend. After completion, it may replay the ordered
backend receipt as a behind-the-scenes explanation. It must not label that
playback as a streamed live trace.

## Backend-owned sources for the frontend

| UI need | Backend source |
| --- | --- |
| Nordly company, services and suggested questions | `GET /api/v1/demo-world` |
| Current capabilities, configured model target, deployment and vector-index readiness | `GET /api/v1/capabilities` |
| Synthetic company documents, eligibility and exact approved text | `GET /api/v1/knowledge/documents` |
| Historical retrieval measurement and its limitations | `GET /api/v1/proof/evals/retrieval` |
| Recorded incident choices and fixture provenance | `GET /api/v1/scenarios` |
| Controlled knowledge question | `POST /api/v1/knowledge/questions/runs/rag` |
| AI-proposed incident plan | `POST /api/v1/incident-lab/plans` |
| Canonical incident, alarm, ADK events, tools, RAG and final receipts | `POST /api/v1/incident-lab/runs` |
| Machine-readable schemas and nullable fields | `GET /v3/api-docs` |

Incident Lab is the primary integration for the recruiter-facing drift-agent
experience. `/api/v1/agent/turns` remains a lower-level ADK proof surface.

## Contracts at this gate

- `capabilities-v5`;
- `incident-lab-plan-v1`;
- `incident-lab-run-v3`;
- nested `nordly-adk-turn-v4`;
- `nordly-knowledge-rag-v3`;
- `scenario-catalog-v2`.

The knowledge RAG contract explicitly reports whether verification was
`completed`, `not_run` or `not_applicable`. It verifies schema, citation
membership, approved-document scope and output policy checks when applicable.
It does **not** claim semantic entailment; `semantic_claim_support_evaluated`
is false.

## Fail-closed rules now enforced

- Unsafe input is blocked before ADK, Gemini, embeddings and tools.
- ADK must show the fixed evidence-agent then diagnosis-agent order.
- Runtime errors, interruptions, non-STOP terminal events, mixed tool/narrative
  output and unexpected functions invalidate the trajectory.
- Only the evidence agent can call the one registered read-only ADK function.
- A released incident diagnosis requires valid schema, valid citations, direct
  evidence support and a factual match to the request-local synthetic case.
- Failed live verification returns `diagnosis=null` and `comparison=null`.
- The public low-level ADK endpoint strips model-written narrative and private
  expected answers; Java projects released text from verified codes and
  evidence IDs.
- Local pgvector, index, embedding, invalid-tool, timeout, interruption, rate
  limit and provider failures retain distinct safe error classifications.
- Incident Lab rejects a control receipt when model, ADK-tool, backend-read or
  embedding counters disagree with the returned events/tools/probe.
- Metrics and all evidence are case-bound; cross-case data is rejected.
- Probe-cited log IDs are included in the backend's highlighted-log projection.
- Recorded replays identify themselves as versioned synthetic fixtures and say
  that investigation, tool and model execution did not happen in the current
  request. Current deterministic verification is labeled separately.
- No write or remediation tool is registered, no action is executed and every
  proposed next step requires a human decision.

## Automated verification

Command: `./mvnw -Pdatabase-it verify`

- Unit/API/contract tests discovered: 480.
- Unit/API/contract tests passed: 480.
- Database integration tests discovered: 8.
- Database integration tests passed: 6.
- Explicit cost-bearing integration tests skipped by this command: 2.
- Failures: 0.
- Errors: 0.
- PostgreSQL: 17.
- pgvector image: 0.8.6 for PostgreSQL 17.
- Flyway migrations validated and applied in integration tests: 6.

The real opt-in retrieval eval was run separately during this hardening pass
with `gemini-embedding-2`, 768 dimensions and exact pgvector cosine search:

- development Hit@4: 5/5;
- held-out Hit@4: 4/5;
- no-match decisions: 3/3;
- configured threshold: 0.6620781500197453.

This is a small synthetic retrieval measurement, not a general quality claim.
Its historical proof endpoint also states that adversarial synthesis safety was
not evaluated by that retrieval-only run.

## Running-backend black-box gate

The final local backend was restarted from the tested Git revision with its
build SHA injected. Without making another cost-bearing provider call, the gate
verified:

- health, capabilities, demo world, document library, retrieval proof,
  scenario catalog and OpenAPI all respond and agree on the active contracts;
- the active vector backend is `pgvector_exact_cosine` and its 12/12 index is
  ready;
- salary/PII requests are blocked before AI with zero provider, ADK, tool,
  read and embedding calls;
- a request targeting a real environment is blocked before the planner;
- an unknown replay scenario fails closed with HTTP 404;
- recorded incident and knowledge replays identify that no current model,
  embedding or tool execution occurred.

## Paid live evidence retained honestly

One real planner request returned HTTP 502 `MODEL_PROVIDER_ERROR`. It was not
retried and no fallback plan was fabricated.

One separate canonical catalog case returned HTTP 200 after two observed ADK
model calls, one ADK function, four backend reads, one real embedding and exact
pgvector retrieval. Java withheld the diagnosis because the factual result did
not match the generated case. Estimated generation cost was USD 0.002177, not a
provider invoice. See `INCIDENT-LAB-V2-LIVE-SMOKE-2026-09-14.md` for the bounded
receipt and its timing/version limitations.

## Not proved by this gate

- The current revision has not been deployed.
- Vertex AI, ADC/IAM, Cloud Run and Cloud SQL were not exercised here.
- The active local provider route is Gemini Developer API, not Vertex AI.
- A configured model ID is a target; the actually observed model belongs to a
  specific run's provider event.
- Provider stability, broad diagnosis accuracy, production safety and latency
  percentiles are not established by one paid smoke.
- Knowledge RAG does not yet run semantic claim-entailment verification.
- The existing frontend has not yet been migrated to these final contracts.
