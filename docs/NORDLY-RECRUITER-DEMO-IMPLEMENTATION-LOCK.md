# Nordly Recruiter Demo — implementation lock

Status: active implementation plan
Audience: LinkedIn recruiters, colleagues, and technical interviewers
Product owner: Shirwac Abib
Primary language: Swedish, with complete English parity

## Outcome

A visitor should understand within 30 seconds that Shirwac built a real, bounded
Applied AI system—not a scripted chatbot or an autonomous production agent.

The demo must show one coherent Nordly world:

1. A synthetic commerce system emits realistic telemetry.
2. A deterministic alarm decides whether an investigation is justified.
3. A controlled Google ADK agent may use read-only tools.
4. RAG turns the incident into an embedding and searches approved runbooks in
   pgvector when that tool is actually used.
5. Deterministic Java validation decides whether the answer may be released.
6. The visitor receives a human explanation first and technical proof on demand.

The portal has two views of the same controlled architecture:

- **Nordly customer assistant:** answers ordinary order and policy questions in
  conversational language. It may read the one fixed synthetic customer's
  current order and approved company documents, but it cannot cancel, refund,
  return, contact, or change anything.
- **Nordly drift agent:** wakes when deterministic telemetry crosses an alarm
  boundary, investigates with ADK-orchestrated read-only tools, and returns a
  human incident summary plus a machine-verifiable receipt.

These are not two visual products. They share the same light Nordly portal,
language system, source drawer, safety vocabulary, and behind-the-scenes reveal.
The customer assistant demonstrates safe external AI; the drift agent
demonstrates safe internal AI.

Live AI is the primary experience. A recorded, verified replay remains available
when live AI is unavailable or the daily AI budget is exhausted. Replay is never
silently substituted for live AI.

## Truth rules

- The frontend renders backend contracts; it does not invent logs, metrics,
  traces, tool calls, RAG use, costs, or verification outcomes.
- `Live AI` means provider calls occurred for this request.
- `Recorded replay` means no provider, embedding, vector-search, or agent call is
  occurring now.
- Synthetic telemetry is always labelled synthetic.
- Returned runtime events are an after-the-run receipt, not chain-of-thought and
  not fake streaming.
- Missing provider usage or cost is `not reported`, never zero.
- Similarity ranks text relevance; it is not model confidence.
- No write tool or automated remediation is added for this release.
- A withheld or malformed response is a valid safety outcome, not replaced with
  a fabricated success.

## Visitor story

### Customer-assistant path

The visitor enters a clean chat and asks a normal question such as `Var är min
beställning?`. The first response reads like customer support, not an engineering
dashboard. A single secondary action reveals the completed backend receipt:

1. the safety gate classified the request;
2. Spring selected the fixed synthetic customer context;
3. an exact order read or a bounded RAG search was performed;
4. only approved source IDs were returned;
5. Java checked the authority and evidence boundary;
6. the released answer and zero-write receipt were produced.

Follow-ups such as `När kommer den?` resolve against the same backend-owned demo
context without claiming persistent model memory. Ambiguous commands such as
`Gör det` request clarification. Requests for personal data stop before customer
reads. Requests to cancel, refund, return, or change an address produce a useful
human answer, show the relevant policy source, and prove that no business write
tool existed.

Policy questions use live RAG only after explicit confirmation. The model may
select among retrieved evidence IDs, but the public claim text is projected from
the versioned Java-owned company corpus. Missing evidence, budget exhaustion,
provider errors, and malformed model output remain visible controlled outcomes;
none is replaced with a guessed answer.

### Scene 1 — invitation

The visitor sees a single premium Nordly portal, a short company context, one
plain-language incident prompt, and one primary action: start a real AI test.

Visible trust labels:

- Live AI available/unavailable, derived from backend status.
- Synthetic environment.
- Read-only investigation.
- Human approval required for any next action.

The internal monetary limit is not shown in the hero.

### Scene 2 — alarm

Backend telemetry appears in chronological order. Normal rows establish context;
the alarm-triggering rows receive restrained visual emphasis. The UI explains:

> Nordly detected an unusual pattern and called the incident agent.

The frontend does not animate rows that the backend did not return.

### Scene 3 — investigation

While the synchronous request is pending, the UI says that the agent is working
and that the complete receipt will be shown after the backend responds. No fake
percentage or fabricated intermediate reasoning is displayed.

After the response, the recorded backend sequence is replayed visually:

1. Alarm accepted.
2. ADK session created.
3. Read-only tool selected.
4. Logs, metrics, traces, and approved runbooks returned.
5. Embedding and pgvector retrieval shown only if returned by the run.
6. Java release gate evaluated.

### Scene 4 — answer

The first layer answers four non-technical questions:

- What did Nordly notice?
- What is the likely explanation?
- What should a developer safely check next?
- Did the AI change anything?

One action, `Show how the answer was built`, opens the proof layer.

### Scene 5 — proof

Progressive disclosure has three levels:

1. Highlighted source evidence and plain-language interpretation.
2. RAG path: question → embedding → pgvector → selected runbook passage.
3. Technical receipt: ADK events, model, tools, token usage, latency, estimated
   list-price cost, verification, identifiers, and limitations.

## Live AI budget boundary

The public demo receives a database-global daily AI list-price guard. The target
is a conservative ceiling equivalent to approximately 2–3 SEK per UTC day for
provider inference and query embeddings. Hosting, database, storage, networking,
tax, and currency movement are outside this application-level AI estimate.

The backend must:

1. Assign a conservative maximum cost to every public paid operation.
2. Atomically consume that allowance before contacting a provider.
3. Reject an operation before provider contact if it would exceed the daily cap.
4. Retain the full conservative allowance even when actual usage is lower,
   missing, or the provider outcome is uncertain.
5. Charge nothing when a safety check blocks before provider contact.
6. Use one provider attempt and no automatic paid retry.
7. Disable repeated submission in the client while a paid request is pending.
8. Apply the same budget boundary to every public live endpoint.

This intentionally under-uses the daily allowance but keeps the Friday MVP's
cost ceiling simple and fail-closed. Exact usage reconciliation and persistent
idempotency are pre-public-deploy hardening items, not preview blockers.

The status contract must expose only presentation-safe state:

- `live_state`
- `reason_code`
- `resets_at`
- `retry_after_seconds`
- `replay_available`
- `daily_cost_guard_active`
- `quota_scope`

The visitor sees availability and reset time, not the exact internal budget.

## Recorded replay boundary

Incident Lab receives a backend-owned replay contract instead of frontend
fixtures.

Minimum API:

- `GET /api/v1/incident-lab/recorded-replay`
- `POST /api/v1/incident-lab/runs/recorded-replay`

The replay response contains:

- its own matching recorded plan and run;
- permanent truth labels in Swedish and English;
- recording date, source build, fixture version, and content checksum;
- whether model, ADK, embedding, and vector search ran when recorded;
- a playback receipt proving zero current provider/tool/embedding calls;
- limitations and a unique playback identifier.

One truthful, sanitized golden recording is sufficient for the first release. It
must originate from a real complete plan-to-run journey and pass startup
validation. A second withheld recording is optional only after the first path is
finished.

The JSON file and its checksum prove content integrity, not capture origin. A
public replay therefore stays unavailable until the operator has captured one
real post-reset live run, reviewed its synthetic logs and typed evidence for
secrets, and stored a capture manifest that binds the recording to the deployed
build SHA and documented capture procedure. Tests may exercise the contract,
but a test fixture can never be promoted or described as a real recording.

## Frontend state machine

The Incident Lab UI may be in exactly one of these states:

- `checking_availability`
- `live_ready`
- `screening_or_planning`
- `awaiting_confirmation`
- `live_running`
- `live_result`
- `live_withheld`
- `live_failed`
- `live_budget_exhausted`
- `replay_ready`
- `replay_running`
- `replay_result`

Rules:

- Live is primary only when the backend says it is available.
- Replay requires an explicit visitor click.
- A successful live plan is never combined with a recorded run.
- A live error remains visible when replay is offered.
- A replay truth label remains visible for the entire replay experience.
- Waiting beyond eight seconds explains that no second request or cost starts.
- All motion is interruptible and respects `prefers-reduced-motion`.
- Desktop and mobile preserve the same narrative order.

## Delivery phases

### Phase 0 — backend contract hardening

Deliver:

- fixed synthetic customer and order catalogs with explicit provenance;
- stateless customer intent classification for Swedish and English;
- early PII and prompt-injection stops;
- explicit authority outcomes for all attempted business actions;
- bounded RAG whose released claims come from canonical approved metadata;
- strict replay sanitization and coherent verification gates;
- real HTTP, PostgreSQL, pgvector, failure, and OpenAPI tests.

Exit gate:

- Human chat copy contains no implementation jargon.
- Every displayed source, tool event, metric, and outcome is returned by backend.
- No model prose, raw tool payload, hidden reasoning, or fabricated replay can
  cross the public contract.
- Business writes remain impossible even when a user asks directly, indirectly,
  in English, or together with a malicious data request.

### Phase A — backend admission truth

Deliver:

- versioned cost profile;
- atomic database-global consumption of conservative operation allowances;
- read-only live availability endpoint;
- stable reason codes and `Retry-After` behavior;
- targeted boundary, reset, and failure tests.

Exit gate:

- No paid public request can bypass the guard.
- Budget exhaustion stops before provider contact.
- Status accurately describes currently observable admission states. Busy and
  provider-limited outcomes remain explicit request results until the backend
  has a durable source for them.

### Phase B — backend replay truth

Deliver:

- Incident Lab replay catalog and run endpoints;
- validated golden recording and checksum;
- proof that playback does not use provider, database, live quota, embeddings,
  vector search, or tools;
- OpenAPI and contract tests.

Exit gate:

- Replay works with live AI disabled.
- Historical and current activity cannot be confused.

### Phase C — recruiter portal

Deliver:

- live status in the existing Nordly portal;
- primary live action and secondary replay action;
- backend-driven alarm scene and evidence highlights;
- human result before technical proof;
- one expandable behind-the-scenes sequence;
- exact Swedish/English parity;
- responsive desktop and mobile composition.

Exit gate:

- A non-technical visitor can state the problem, the AI's role, the safety
  boundary, and the result without opening technical proof.
- A technical visitor can verify ADK, tool calling, RAG/pgvector, Java validation,
  usage, and cost from returned backend data.

### Phase D — bounded verification

Run once:

- backend tests for affected modules;
- frontend tests and production build;
- real live success or withheld outcome;
- budget-exhausted → explicit replay path;
- malformed/provider failure → truthful stopped state;
- desktop and narrow mobile visual inspection;
- keyboard focus and reduced-motion inspection.

Then stop for product-owner visual review. Do not deploy or expand scope before
approval.

## Explicitly out of scope before visual approval

- Public deployment or Vertex migration.
- Write tools, shell access, terminal control, or automated remediation.
- SSE/WebSocket streaming.
- More agent roles merely to demonstrate more agents.
- A second visual system or dashboard.
- Accuracy percentages without a separately verified evaluation.
- Automatic live retries or hidden replay fallback.
- More replay cases before the first complete recruiter journey works.
- Exact usage reconciliation, persistent request idempotency, and anonymous
  abuse protection beyond the database-global cap.

## Anti-drift decision order

When a new idea appears, decide in this order:

1. Does it help a recruiter understand the system within 30 seconds?
2. Is it backed by real returned backend data?
3. Does it preserve the read-only and explicit-live boundary?
4. Is it necessary for the current phase exit gate?

If any answer is no, record the idea for later and continue the active phase.
