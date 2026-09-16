# Vertex and Cloud SQL connector smoke — 16 September 2026

Status: verified and torn down. This receipt records one controlled, private-IAM
backend smoke. It does **not** claim that the final private-IP network path is
deployed.

## Scope and immutable inputs

- Personal project: `shirwac-incident-detective`.
- Dedicated gcloud configuration: `incident-detective-personal`.
- Backend commits:
  - `4eee390` — Cloud SQL Java Connector and database hardening.
  - `916a2bd` — explicit, fail-closed `PRIVATE` or `PUBLIC` connector route.
- Container digest:
  `sha256:4f5e6dbbb35d6ec933828843987e8c178ce9f21f0181780f15a98eefae12f651`.
- Vertex location: `eu`.
- Generation model: `gemini-3.1-flash-lite`.
- Embedding model: `gemini-embedding-2`, 768 dimensions.
- All incident, customer and company data was synthetic.
- The replay service and frontend were not changed or connected.

## Temporary environment

The smoke used a separate IAM-protected Cloud Run service and a disposable
Cloud SQL for PostgreSQL 17 instance. The database used a public IP only because
Compute Engine API was stuck in a Google deactivation state. The Cloud SQL Java
Connector was mandatory, authorized networks were empty, and the Connector
provided the authenticated TLS path.

This was a transport and runtime compatibility test, not the target network
architecture. The target remains Direct VPC egress to Cloud SQL private IP.

## Verified database and retrieval evidence

- Spring Boot connected through Cloud SQL Java Connector 1.29.0.
- Flyway validated and applied migrations V1 through V7.
- PostgreSQL reported version 17.11.
- Runbook import completed through Vertex embeddings:
  - 12 total chunks;
  - 12 imported;
  - 0 skipped.
- Nordly knowledge import completed through Vertex embeddings:
  - 28 total chunks;
  - 28 imported;
  - 0 skipped.
- Capabilities reported the runbook index ready at 12/12.
- The live knowledge run reported its index ready at 28/28/28.
- Both retrieval paths used exact pgvector cosine search.

## Verified safety paths

### Blocked before AI

The Swedish request asking for an employee's salary returned
`blocked_before_ai` with reason `employee_compensation_request`.

Its backend receipt reported:

- 0 model calls;
- 0 ADK tool calls;
- 0 read operations;
- 0 embedding calls;
- no write capability;
- no action executed.

### Missing confirmation

The same endpoint with `confirm_live_ai=false` returned HTTP 400 and
`LIVE_AI_CONFIRMATION_REQUIRED`. No automatic retry was made.

## Verified ADK investigation

One confirmed incident investigation completed on the first attempt:

- contract: `nordly-adk-turn-v4`;
- outcome: `completed`;
- runtime: Google ADK for Java 1.7.0;
- orchestration: `SequentialAgent`;
- observed order:
  `nordly_evidence_agent` → `nordly_diagnosis_agent`;
- provider route: Vertex AI with ADC in `eu`;
- 2 model calls;
- 1 registered ADK tool call;
- 5 bounded read operations;
- 1 query embedding;
- 0 write operations;
- human approval required for the proposed next step.

The registered `inspect_incident_evidence` tool returned metrics, logs, one
trace and runbook retrieval. The runbook search ranked:

1. `runbook-cache-stale-checkout` — cosine similarity 0.7638620734;
2. `runbook-cdn-origin-failure` — cosine similarity 0.7312088128.

Java then verified the schema, citations, direct evidence support, agent order,
function-response handoff, tool boundary and synthetic factual result before it
released the deterministic diagnosis projection. Raw model narrative and the
private expected answer were not returned.

## Verified Nordly knowledge RAG

The confirmed question about an approved card refund returned `answered`:

- the question was embedded into 768 dimensions;
- 13 approved documents and 28 chunks were eligible;
- exact pgvector search ranked
  `nordly-evidence-refund-timing-card` first;
- the only published claim cited that retrieved evidence ID;
- Java verified schema, citation scope, document approval, PII policy and the
  no-write boundary;
- the public answer was the corpus's canonical summary, not free model prose.

The released Swedish summary was:

> Återbetalningar godkända före 15.00 skickas samma bankdag; banken kan
> därefter behöva 2–5 bankdagar.

The run used one embedding call and one generation call. The backend estimated
generation cost at USD 0.000842, excluding embedding cost.

## Access, logging and cost checks

- Unauthenticated Cloud Run requests returned 403.
- Authenticated health returned 200 `UP`.
- Authenticated `/` returned 404, proving that this backend-only service did not
  serve the frontend.
- The service and both import jobs had no `allUsers` or
  `allAuthenticatedUsers` IAM binding.
- Cloud Run contained zero error-severity log entries during the smoke.
- Logs contained neither the database password, request text nor raw model
  response shape.
- The ADK run estimated generation cost at USD 0.00340725.
- Combined estimated generation cost for the ADK and knowledge runs was
  USD 0.00424925. This is not a provider invoice and excludes embedding and
  short-lived infrastructure cost.
- A project-scoped 90 SEK monthly budget alert remains configured at 50%, 80%
  and 100%. Billing budgets alert; they do not stop spending.

## Teardown evidence

After receipts were captured, the following disposable resources were deleted:

- the live-smoke Cloud Run service;
- both import jobs;
- the Cloud SQL instance;
- the database password secret;
- the runtime service account's temporary Cloud SQL Client role.

The retained replay service was rechecked after teardown:

- revision `incident-detective-backend-staging-00001-jfv`;
- digest
  `sha256:5748c0205722b9099251753a48c011b745c39c65607855248ea790659641063a`;
- unauthenticated health 403;
- authenticated health 200 `UP`;
- authenticated root 404;
- no public IAM binding.

## Remaining release gate

The final private-IP deployment is still pending. A fresh enable attempt for
`compute.googleapis.com` failed with Google Service Usage precondition error
`160008`. Without Compute Engine API, Direct VPC egress, Private Services
Access and Private Service Connect cannot provide the required network path.

The final gate is therefore:

1. wait for Google to release the Compute API deactivation state;
2. enable Compute Engine API;
3. create the private network and Private Services Access range;
4. create Cloud SQL with private IP only;
5. create `vector` and two custom roles administratively;
6. run Flyway/imports as a non-superuser migrator and the service as a separate
   non-superuser runtime user;
7. verify that neither login is a `cloudsqlsuperuser`, while runtime can read
   vectors and atomically update only the quota table;
8. deploy the same reviewed digest through Direct VPC egress;
9. rerun this smoke matrix once;
10. delete cost-bearing database capacity after verification.

Until those checks pass, do not describe the system as deployed over a private
database network and do not connect the frontend to the live backend.

## Primary references

- [Cloud Run Direct VPC egress](https://docs.cloud.google.com/run/docs/configuring/vpc-direct-vpc)
- [Cloud SQL private services access](https://docs.cloud.google.com/sql/docs/postgres/configure-private-services-access)
- [Cloud SQL language connectors](https://docs.cloud.google.com/sql/docs/postgres/connect-connectors)
- [Cloud SQL users and database roles](https://docs.cloud.google.com/sql/docs/postgres/create-manage-users)
- [Cloud SQL PostgreSQL extensions](https://docs.cloud.google.com/sql/docs/postgres/extensions)
- [Google issue 204556342 — Service Usage error 160008](https://issuetracker.google.com/issues/204556342)
