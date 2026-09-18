# Incident Lab golden replay capture — 16 September 2026

Status: **published as a provider-free historical playback**

This manifest binds the first public Incident Lab replay to one real,
synthetic plan-to-run journey. The recording is intentionally a withheld
answer: Google ADK completed its bounded workflow, but deterministic Java did
not release the proposed diagnosis because one claim lacked direct evidence.
That outcome is preserved rather than rewritten as a success.

## Capture identity

- Replay ID: `nordly-payment-timeout-withheld-2026-09-16`
- Recorded at: `2026-09-16T07:49:47.367407Z`
- Source-content identity:
  `f890fbfe4afd50bb91d87dce3956e9d22696a494`
- Runtime build identity: **not observed or injected**
- Recording source: `captured_public_api`
- Runtime: local Spring Boot backend with the `rag` profile
- Provider route: Gemini Developer API with API-key authentication
- Model: `gemini-3.1-flash-lite`
- ADK: Google ADK for Java `1.7.0`
- Embedding model: `gemini-embedding-2`, 768 dimensions
- Vector search: PostgreSQL/pgvector exact cosine retrieval

The runtime started from the backend source tree that was subsequently
committed as the source identity above. The capture occurred before that
commit was created; session evidence shows no `src/main` or `src/test` edit
between the capture and the commit. Only separate Docker and Cloud Build files
were added in that interval. The SHA is therefore used as the durable content
identity, not as a claim that the commit already existed when the request ran.
The public provenance therefore exposes this value as
`source_content_git_sha`, leaves `runtime_build_git_sha` empty and reports
`runtime_build_identity_verified = false`. A future runtime-injected capture
may set the separate runtime fields; this recording does not.

## Request path

The Swedish instruction was:

> Efter en syntetisk release börjar betalningsanrop ge återkommande 500-fel.
> Skapa ett säkert lokalt fall som agenten kan utreda med read-only verktyg.

The planner returned a Java-approved `payment_timeout` plan. The run endpoint
revalidated that exact canonical plan and generated a request-local synthetic
case. Both HTTP requests returned 200.

Original artifact hashes before packaging:

- planner response:
  `077c2767ea4d9a55f56896b484143a6d89d58416d569129b5f50c7032334de0d`
- canonical run request:
  `0efe06d03929676bce31975d4c6db8e78f8839dae2693fd8e8a56b0fbd03af8f`
- run response:
  `7e065f1aa0445ee588db43f4546a9fe525dce02beec35573c90048b29ea95eac`
- packaged replay resource:
  `94d0f5ef5e77a21cd7073300c7a3c21386858e4222911f5676d390f1e40bce8a`

The packaged checksum is verified during application startup. The application
refuses to start if the resource and checksum differ.

## Recorded proof

- Real Gemini planner response with reported token usage
- Deterministic Java narrowing and canonical plan validation
- Deterministic Java alarm over synthetic HTTP 504 telemetry
- Real Google ADK `SequentialAgent` in the expected two-agent order
- One allowlisted `inspect_incident_evidence` function call
- Four read operations and no write capability
- One real query embedding
- Exact pgvector cosine retrieval over approved runbook chunks
- Backend-owned Swedish and English result projections
- Human approval required and no action executed

The candidate diagnosis was withheld because direct evidence support failed
for one of two claim-to-evidence links. Public model prose, private expected
answers and raw credentials are absent from the recording.

## Sanitization review

The exact packaged resource was scanned before publication for API-key,
Bearer-token, private-key, password, email and Swedish personal-number
patterns. No credential or personal-data value was found. All incident,
company, service and customer information is synthetic.

The replay loader also validates at startup that:

- the final model text is withheld;
- the only registered function is the bounded read-only evidence tool;
- function arguments and responses use exact public allowlists;
- evidence IDs stay inside the recorded synthetic case;
- receipt counts match the recorded events;
- no write tool or automated action is represented;
- planner and agent provider routes agree.

## Playback boundary

Calling the replay endpoint does not invoke Gemini, Google ADK, a database,
an embedding model, vector search, quota accounting or remediation. Historical
provider, latency, retrieval, token and cost fields describe the capture only.
Each playback returns a fresh playback ID and a zero-current-execution receipt.
