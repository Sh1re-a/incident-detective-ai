# Incident Lab Backend v2

Status: implemented locally; 444 tests passed; post-v7 live validation pending

Scope: synthetic Nordly incidents, local backend first

Frontend: unchanged until the backend acceptance gate passes

## Current checkpoint

Implemented and verified locally:

- server-generated and client-replayable incident seeds;
- four deterministic alarm families;
- Google ADK `SequentialAgent` with an evidence agent followed by a tool-free
  diagnosis agent;
- one bounded ADK function backed by typed log, metric, trace, runbook and
  diagnostic-probe reads;
- embeddings and pgvector retrieval with explicit receipts;
- business, developer and action receipts derived from verified backend state;
- strict Java release control for supported, insufficient and withheld answers;
- OpenAPI coverage for optional inputs and safe failure responses;
- the full Maven suite: 444 tests, zero failures.

Live evidence is intentionally split from test evidence. One pre-v7 run crossed
Gemini, ADK, embeddings and pgvector and was safely withheld by Java. A later
post-v7 request returned HTTP 502, but its response body was not retained, so
the exact safe backend code and the reached workflow stage remain unknown. No
automatic retry was made. A captured post-v7 live run is therefore the only
remaining backend acceptance item before frontend integration.

## Product outcome

Incident Lab must behave like a controlled investigation partner:

1. Nordly's synthetic systems emit backend-owned telemetry.
2. Deterministic Java rules decide whether an alarm exists.
3. An ADK evidence agent may inspect only the current case through bounded,
   read-only tools.
4. A tool-free diagnosis agent explains what is known and what remains unknown.
5. Deterministic Java verification decides whether the answer may be released.
6. The API returns both a recruiter-readable business explanation and a
   developer receipt without exposing hidden reasoning.

An alarm is useful even when the agent cannot establish a root cause. The
supported answer states are therefore:

- `diagnosed`: enough directly supported evidence exists;
- `insufficient_evidence`: the symptoms are real but the cause cannot be proven;
- `withheld`: the model response or its evidence links failed verification;
- `not_started`: no alarm fired, so no agent was invoked.

## Non-negotiable truth boundaries

- Every incident, log, metric, trace and business-impact number is synthetic.
- Java owns scenario generation, alarm rules, tool scopes and release decisions.
- The model may select from an allowlist and write explanations. It may not
  invent telemetry, citations, tool results, provider receipts or actions.
- No shell, terminal, SQL, URL, path, project or environment name may come from
  model text.
- No write or remediation tool is available in v2.
- A proposed change always requires a separate human decision outside the
  investigation endpoint.
- Live AI never silently falls back to recorded replay.

## Backend flow

```text
free-text request
    -> pre-AI safety gate
    -> Gemini incident proposal
    -> Java canonical plan
    -> backend case generator
    -> deterministic alarm rule
    -> ADK evidence agent
    -> bounded backend reads and pgvector retrieval
    -> tool-free diagnosis agent
    -> Java schema, citation, support and ground-truth verification
    -> business response + developer response + receipts
```

## Delivery order

### Phase A — generation provenance and uniqueness

Make `seed` optional at the public Incident Lab boundary.

- Missing seed: the backend creates one and labels `seed_source=server`.
- Supplied seed: the run is reproducible and labels `seed_source=client`.
- Return a `generation_receipt` containing:
  - generator version;
  - seed and seed source;
  - scenario ID;
  - variant ID;
  - simulation anchor;
  - generation time;
  - evidence fingerprint.
- Namespace request IDs, idempotency keys, release IDs and trace IDs by the
  generated case.
- Preserve alarm invariants while allowing seeded timing jitter and bounded
  wording/data variants.
- The same family, seed, evidence mode and noise level must reproduce the same
  evidence fingerprint.

### Phase B — honest uncertainty

Expose a bounded evidence selection. Automatic selection is represented by
omitting `evidence_mode`; the literal string `auto` is not part of the enum:

- omitted `evidence_mode`: backend deterministically selects a diagnostic or
  incomplete variant from the seed;
- `diagnostic`: sufficient causal evidence is present;
- `insufficient_evidence`: causal evidence is deliberately absent.

For an incomplete case the agent must report observed symptoms, missing
evidence and a safe next check. It must not return a root cause or affected
service as fact.

### Phase C — safe diagnostic tool calling

Preserve the existing ADK `SequentialAgent`:

1. `nordly_evidence_agent` performs one bounded inspection.
2. `nordly_diagnosis_agent` receives the structured tool response and has no
   tools of its own.

The existing `inspect_incident_evidence` function remains the ADK boundary. Its
nested operations must be returned as explicit receipts:

- `get_metrics`;
- `search_logs`;
- `get_trace`;
- `retrieve_runbooks` using embeddings and pgvector;
- optional `run_diagnostic_probe`.

`run_diagnostic_probe` accepts only enum values and a service from the current
case. Initial probe IDs:

- `SERVICE_HEALTH`;
- `DEPENDENCY_STATUS`;
- `RELEASE_METADATA`;
- `CONFIG_FINGERPRINT_DIFF`.

Java maps each probe ID to a fixed read-only handler. The model cannot provide
a command, path, URL or environment. Every receipt includes status, safe
summary, evidence IDs, latency, read-only status and `action_executed=false`.

### Phase D — dual response contract

The API returns two projections from verified facts:

`business_response`

- headline;
- what happened;
- customer or operational impact;
- what is known;
- what remains unknown;
- safe next step;
- certainty;
- human approval requirement.

`developer_response`

- root-cause code when verified;
- affected service when verified;
- technical summary;
- structured claims and evidence IDs;
- highlighted log evidence;
- missing evidence;
- tool receipts;
- verification receipt.

Free model prose is never reused after a failed verifier. In a withheld run,
Java returns a neutral explanation and the failed verification checks.

### Phase E — four alarm families

Incident Lab currently runs only payment timeout. Enable the remaining family
only after its own deterministic symptom rule and tests exist.

| Family | Alarm signal | Example rule |
| --- | --- | --- |
| Payment timeout | HTTP 5xx burst | at least 3 HTTP 5xx in 30 seconds |
| Catalog cache | stale response ratio | ratio above threshold with version-divergence evidence |
| Order backlog | consumer lag | sustained lag above the bounded threshold |
| Idempotency | duplicate creation | repeated idempotency key produces more than one order |

The alarm must rely on observable symptoms, not hidden ground truth. Each rule
returns its own threshold, observed value, unit, time window and evidence IDs.

## Stable response identity

- `scenario_id`: the logical generated incident;
- `variant_id`: the exact evidence/noise package;
- `alarm_id`: the deterministic alarm decision;
- `run_id`: one unique ADK execution;
- `evidence_fingerprint`: proof that an explicit seed can reproduce the case.

These identifiers must never be model-generated.

## Acceptance gate before frontend work

### Generation

- Two server-seeded requests produce different cases.
- An explicit seed reproduces the same evidence fingerprint.
- Different evidence modes produce different variant IDs.
- Hidden ground truth never appears in the public case or model prompt.

### Alarm and orchestration

- No alarm means zero ADK, model and tool calls.
- One generated case instance flows unchanged through alarm and ADK.
- Every enabled family has a family-specific rule and cited triggering evidence.
- Cross-case evidence causes a hard failure.

### Tools and safety

- Unknown probe ID or service is rejected before execution.
- Prompt injection inside a log or runbook cannot request another tool.
- No shell, command text, filesystem path, URL or environment is accepted.
- Budgets, deadlines, result limits and output-size limits are enforced.
- Receipts always show `write_tools_available=false` and
  `action_executed=false`.

### Answers

- A supported diagnosis is released only with valid direct evidence links.
- An incomplete case returns `insufficient_evidence`, missing evidence and a
  safe next read without guessing a cause.
- Malformed or unsupported model output returns a withheld receipt, never a
  fabricated fallback.
- Business and developer text contain only verified facts or explicit
  uncertainty.

### Verification

- Focused tests pass after every phase.
- The full Maven suite passes once after integration.
- One real Gemini planner call and one real ADK run are captured as a local
  smoke receipt.
- OpenAPI documents every status and nullable field truthfully.

## Explicitly deferred

- arbitrary terminal or shell access;
- automatic remediation;
- production/customer data;
- Cloud Logging or Cloud Trace identities;
- Vertex AI deployment and IAM changes;
- frontend redesign.

Those are separate decisions after this local backend contract is approved.
