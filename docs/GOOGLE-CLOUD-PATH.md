# Google Cloud path for Nordly

Status: private-IP deployment plan, updated 16 September 2026. A controlled
Vertex AI + Cloud SQL Java Connector smoke has now verified the current backend,
ADK workflow, pgvector retrieval and safety receipts. The disposable live
environment was deleted after the test. The final Direct VPC/private-IP path is
still blocked by Google Service Usage error `160008` for Compute Engine API.

The authoritative current-run evidence is recorded in
[`VERTEX-CLOUD-SQL-CONNECTOR-SMOKE-2026-09-16.md`](VERTEX-CLOUD-SQL-CONNECTOR-SMOKE-2026-09-16.md).
That temporary public-IP Connector smoke must not be described as the final
private network architecture.

## Personal cloud boundary

Incident Detective is a personal Applied AI portfolio project. Every future
cloud operation must use a dedicated personal Google Cloud project, a separate
`gcloud` configuration, separate ADC and a new runtime identity. Existing
customer-work projects, configurations, service accounts, registries and
deployed services must never be reused or updated for this application.

Before every Vertex or deployment command, verify the selected account,
project, configuration and ADC identity together. If any value belongs to a
customer-work environment, stop. The verified personal project is
`shirwac-incident-detective` and the dedicated configuration is
`incident-detective-personal`.

## Current local baseline

- Spring Boot 4.1.1 on Java 21.
- Google ADK for Java 1.7.0 with a two-child `SequentialAgent`.
- Google Gen AI Java SDK 1.67.0.
- One Google Gen AI client seam. The local default is Gemini Developer API with
  a server-side API key; `vertex_ai` selects Vertex AI with ADC, project and
  location configuration.
- `gemini-embedding-2`, 768 dimensions.
- PostgreSQL/pgvector exact cosine retrieval.
- Local PostgreSQL 17 with pgvector through Docker Compose.
- OpenTelemetry spans are disabled locally by default; exporting is a separate
  opt-in.
- The current multi-stage Docker build packages the Vite frontend and Spring
  Boot backend as one non-root image; that combined artifact has been verified
  locally.

The current backend has now completed a real, private-IAM Vertex AI and Cloud
SQL Connector smoke. ADC, generation, embeddings, explicit corpus import,
pgvector exact cosine retrieval, ADK orchestration and deterministic Java
verification were observed in one bounded run. The environment was then torn
down. This proves application/runtime compatibility, but it does not yet prove
Direct VPC or Cloud SQL private-IP readiness.

## Target with the smallest architecture change

```text
Browser
  -> Cloud Run: Spring Boot API and static frontend
       -> Vertex AI Gemini through service identity and ADC
       -> Cloud SQL for PostgreSQL with pgvector
       -> Cloud Logging for stdout/stderr
       -> OpenTelemetry trace export only after payload review
```

The first cloud version keeps pgvector. A managed Vertex AI Vector Search index
is a later scaling experiment, not a prerequisite for a small portfolio corpus.
Spring Boot remains the public API and deterministic control plane, while the
Google ADK Java runtime stays inside the same process. No Python service or
Vertex AI Agent Engine migration is part of this phase.

## Gate 1: provider seam in Java

Implementation status: provider-free tests and one bounded Vertex execution are
verified. The final private-network rerun remains pending Compute Engine API.

Add one server-side provider setting:

```text
developer_api | vertex_ai
```

Developer API requires an API key. Vertex AI requires a project, location and
Application Default Credentials. Generation and embeddings must obtain their
client from the same factory so the two code paths cannot silently use
different providers.

Capabilities must report, without returning secrets or the Google Cloud project
ID/service-account identity:

- provider transport;
- authentication mode;
- configured Vertex location;
- generation model;
- embedding model and dimensions;
- vector store and index readiness.

Candidate change points:

- `src/main/java/dev/shirwac/incidentdetective/ai/GeminiAiProperties.java`
- new `src/main/java/dev/shirwac/incidentdetective/ai/GoogleGenAiClientFactory.java`
- `src/main/java/dev/shirwac/incidentdetective/ai/GeminiInvestigationModelGateway.java`
- `src/main/java/dev/shirwac/incidentdetective/rag/GoogleGenAiEmbeddingApi.java`
- `src/main/java/dev/shirwac/incidentdetective/live/LiveInvestigationService.java`
- `src/main/java/dev/shirwac/incidentdetective/capabilities/CapabilitiesResponse.java`
- `src/main/java/dev/shirwac/incidentdetective/capabilities/CapabilitiesService.java`
- `src/main/resources/application.properties`

Provider construction is tested without network calls. A real provider smoke
remains a separate, explicit and cost-bearing command.

The pgvector identity also includes `provider_transport`. Existing rows are
classified as `developer_api`; selecting `vertex_ai` therefore reports the
index as not current until the corpus is explicitly re-embedded through that
transport. The embedding client rejects a configuration where its selected
provider and the stored embedding profile disagree.

Changing provider transport, model revision, task semantics, dimensions or
input formatting marks the current vector index stale. Live RAG stays disabled
until the full corpus has been re-embedded and the frozen retrieval eval has
passed again. Migrating to Vertex transport and changing the current manual
query/document prefixes are two separate experiments with separate
`embedding_format_version` values.

## Gate 2: one deployable artifact

The preferred deployment remains one Cloud Run service that serves the built
frontend and the API from the same origin. This preserves the current `/api`
contract and avoids a public CORS dependency.

Locally implemented and verified container properties:

1. The Vite frontend builds in a Node stage.
2. The frontend output is copied into Spring Boot static resources before packaging.
3. The final Java 21 runtime runs as a non-root user.
4. `server.port=${PORT:8080}` reads Cloud Run's injected
   `PORT` value.
5. The minimal health endpoint remains available.
6. `.dockerignore` allows the frontend source required by the build stage.

Local frontend development remains Vite plus the API proxy. Repeating the
container check on the exact release SHA remains mandatory before a cloud
write; the earlier local result does not verify a later revision automatically.

## Gate 3: identity and secrets

Use separate service identities for runtime and migration/import work. Vertex
AI access uses ADC, not downloaded service-account keys. The runtime identity's
exact permissions are:

- `roles/aiplatform.user`;
- `roles/cloudsql.client`;
- `roles/secretmanager.secretAccessor`, scoped to the exact database secret.

The migration/import identity receives Cloud SQL Client, Vertex AI User and
access only to the separate migrator secret. The runtime identity must never be
able to read that secret.

Build and deployment identities stay separate from this runtime identity.

Secrets such as the Cloud SQL database password are stored in Secret Manager
and exposed only to the backend. If a secret is injected through an environment
variable, pin a specific secret version for reproducible revisions.

Do not put credentials in the frontend, container image, repository or trace
attributes.

## Gate 4: Cloud SQL and pgvector

Use Cloud SQL for PostgreSQL 17 in the same region as Cloud Run and enable the
`vector` extension. Keep the existing Flyway schema and exact cosine query for
the first corpus. Create the extension once with the administrative
`cloudsqlsuperuser`, then use two custom non-login roles and two built-in login
users:

- a non-superuser migrator that owns the application schema and runs Flyway and
  the explicit corpus imports;
- a non-superuser runtime that can read Flyway history and embeddings and can
  insert/update only the database-global quota table.

Neither login may belong to `cloudsqlsuperuser`. Runtime receives no schema
`CREATE`, extension, sequence or corpus-import rights. The same datasource may
still call Flyway on startup because an already-current V7 schema requires only
read access to the history table; a missing migration then fails closed until
the migrator job runs.

For V1, use the Cloud SQL Java Connector with Hikari,
`cloudSqlRefreshStrategy=lazy`, a pool size of 4 and the password from Secret
Manager. Add the connector dependency in `pom.xml` and its configuration in
`RagDatabaseConfiguration.java`.

The Cloud Run revision must set `SPRING_PROFILES_ACTIVE=rag,cloud`; `rag`
enables the database-backed runtime and `cloud` enables single-line structured
console JSON for Cloud Logging. Without `rag`, the current default remains
replay-only and database-free. Its first smoke also sets
`INCIDENT_DETECTIVE_LIVE_AI_ENABLED=false`.

The corpus import remains an explicit operator command or one-off job. Normal
application startup must not call the embedding provider or rebuild the index.

## Gate 5: observability

Start with the request logs and stdout/stderr that Cloud Run captures. With the
`cloud` profile, Spring Boot writes one Logstash JSON object per console line;
Cloud Run can ingest those entries into Cloud Logging as `jsonPayload` without
an application-side Logging client or `entries.write` calls. Incident Lab emits
bounded lifecycle events for plan decisions, synthetic run start, deterministic
alarm evaluation, ADK start/completion, Java verification and final outcome.
The event schema contains correlation IDs, server-controlled identifiers,
booleans, counts, latency, provider token usage and estimated model cost when
available. It intentionally has no field for prompts, user questions, retrieved
passages, evidence bodies, GroundTruth, provider responses or customer data.

This is stdout integration, not proof of authenticated Cloud Trace delivery or
trace/log correlation. Keep custom trace export disabled for V1. See
`docs/CLOUD-LOGGING.md` for the exact event contract and current boundary.

Before enabling OTLP export:

1. run the existing allowlist tests;
2. inspect one exported trace manually;
3. confirm trace and log correlation without sensitive payloads;
4. document sampling and retention;
5. keep metrics/log export separate from trace export.

No collector/dashboard stack is required for the first public demo.

## Gate 6: cost and abuse controls

Initial Cloud Run settings use scale-to-zero, no minimum instances and service
plus revision maximum instances set to 1. Cloud Run may briefly exceed a maximum
instance setting, so this is a budget guard rather than a hard cap.

The current concurrency limit and five-starts-per-ten-minutes guard are
process-local to one container. Only the daily live quota is database-global
under the `rag` profile. The backend's hard deadline and both guards remain
active, but `confirm_live_ai=true` is consent in the UX, not authentication or
abuse protection.

Before public access:

- configure a Cloud Billing budget and alerts;
- set service and revision maximum instances to 1;
- configure Cloud Run request timeout just above the application's hard limit;
- verify database connection capacity at that maximum;
- keep replay as the default user journey;
- never automatically retry a paid live AI request in the browser.

Billing budgets alert; they do not stop spending. Cloud Run can scale to zero,
while Cloud SQL has an ongoing provisioned cost. Real cost bounds are the
replay-first journey, live AI disabled until explicitly requested, one maximum
Cloud Run instance, the database-global daily quota, provider quotas and a
deliberately small Cloud SQL instance with an operator-owned stop/delete policy.

The first newly approved public revision should remain replay-only. Before
exposing a live endpoint, add and verify a separate authentication or anti-abuse
boundary; a caller can otherwise script `confirm_live_ai=true` directly.

Before choosing the Cloud Run region and Vertex location, verify that both the
generation model and `gemini-embedding-2` are available there. Successful ADC
alone is not Vertex readiness.

## When managed Vertex AI Vector Search is justified

Consider it only when at least one measured need exists:

- the corpus no longer fits predictable exact search latency;
- retrieval load must scale independently from transactional storage;
- approximate-nearest-neighbour latency is measured as a bottleneck;
- filtering or index operations require a dedicated managed service;
- the project explicitly compares pgvector and managed retrieval using the same
  frozen eval set.

Until then, Cloud SQL plus pgvector proves embeddings, semantic search, vector
storage, metadata filtering and RAG with less infrastructure and clearer
evidence.

## External-action boundary

The following remain separate operator decisions for the next revision:

1. enabling Google Cloud APIs;
2. creating Artifact Registry, service accounts, secrets or Cloud SQL;
3. running a paid Vertex AI smoke;
4. creating a new Cloud Run revision from the exact reviewed SHA;
5. assigning public traffic or unauthenticated access;
6. publishing a public URL.

## Correct deployment order

1. Implement and provider-test the shared client factory without network calls.
2. Rebuild and verify the combined frontend/backend container from the exact
   reviewed SHA.
3. Create the dedicated runtime identity with least-privilege IAM.
4. Create Cloud SQL in the chosen region, create `vector` with the admin user,
   and bootstrap separate non-superuser migrator/runtime roles.
5. Build the image into Artifact Registry.
6. Run Flyway and both explicit corpus imports with the migrator identity.
7. Verify V1–V7, both current indexes and the runtime user's negative DDL test.
8. Deploy a private, replay-only Cloud Run revision with `rag`, live AI disabled
   and zero public traffic.
9. Verify health, capabilities, index hash/chunk readiness and global quota
   scope.
10. Run one explicitly approved Vertex generation-and-embedding smoke.
11. Freeze its sanitised result as replay evidence.
12. Expose the replay journey only after a separate public traffic decision.
13. Keep live AI private until its anti-abuse boundary is verified.

## Primary references

- [Google Gen AI SDK](https://cloud.google.com/vertex-ai/generative-ai/docs/sdks/overview)
- [Cloud Run configuration](https://docs.cloud.google.com/run/docs/configuring)
- [Cloud Run service identity](https://docs.cloud.google.com/run/docs/securing/service-identity)
- [Cloud Run secrets](https://docs.cloud.google.com/run/docs/configuring/services/secrets)
- [Cloud Run to Cloud SQL for PostgreSQL](https://docs.cloud.google.com/sql/docs/postgres/connect-instance-cloud-run)
- [Cloud SQL PostgreSQL extensions](https://docs.cloud.google.com/sql/docs/postgres/extensions)
- [Cloud Trace instrumentation](https://docs.cloud.google.com/trace/docs/setup)
- [Cloud Run pricing](https://cloud.google.com/run/pricing)
