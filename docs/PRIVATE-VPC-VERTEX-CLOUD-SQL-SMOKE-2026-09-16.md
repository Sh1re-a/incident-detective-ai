# Private VPC, Vertex, ADK and Cloud SQL smoke — 16 September 2026

Status: **verified end to end and cost-bearing staging capacity removed**.

This receipt covers one controlled backend-only smoke in the personal Google
Cloud project `shirwac-incident-detective`. All incident, customer and company
data was synthetic. The frontend was not connected, and the retained replay
service was not changed.

## Immutable application input

- Container digest:
  `sha256:4f5e6dbbb35d6ec933828843987e8c178ce9f21f0181780f15a98eefae12f651`.
- Container build commit:
  `916a2bd7a43168fa623bf16c7534ea736f202e3a`.
- Generation model: `gemini-3.1-flash-lite` through Vertex AI ADC in `eu`.
- Embedding model: `gemini-embedding-2`, 768 dimensions.
- Google ADK for Java: 1.7.0.
- Bootstrap script commits: `4ebbd23`, `ec7a53c` and `e7561a9`.

## Private network and database proof

The temporary staging path used:

- custom VPC `incident-detective-staging-vpc`;
- Direct VPC subnet `incident-detective-run-euw1`, `10.60.0.0/26`;
- Private Services Access range `10.133.30.0/24`;
- Cloud SQL for PostgreSQL 17.11 in `europe-west1-c`;
- private database address `10.133.30.3`;
- no public IPv4 address;
- no authorized network;
- `connectorEnforcement=REQUIRED`;
- `PRIVATE` Cloud SQL Java Connector routing;
- `private-ranges-only` Cloud Run VPC egress.

The first bootstrap execution reached the private database through the managed
Cloud SQL socket, proving the network path before any live model call.

## Database bootstrap and least privilege

The one-off bootstrap created `pgvector`, the isolated
`incident_detective` schema and two separate custom group roles. Login users
were created through the Cloud SQL Admin API with their custom role supplied at
creation time.

Database-side checks proved:

- migrator login is not `cloudsqlsuperuser`;
- runtime login is not `cloudsqlsuperuser`;
- neither login can create databases, roles or schemas;
- runtime can read Flyway history and vectors;
- runtime can insert/update only the database-global quota table;
- runtime cannot update the embedding corpus;
- runtime cannot delete quota rows;
- runtime cannot create a table;
- runtime has no sequence privileges.

The final bootstrap receipt was:

```text
BOOTSTRAP_STAGE=COMPLETE
VECTOR_EXTENSION=READY
FLYWAY_VERSION=7
EMBEDDING_ROWS=40
MIGRATOR_CLOUDSQLSUPERUSER=false
RUNTIME_CLOUDSQLSUPERUSER=false
RUNTIME_CORPUS_WRITE=false
RUNTIME_QUOTA_WRITE=true
RUNTIME_SCHEMA_CREATE=false
```

## Real failures handled safely

Three bootstrap attempts stopped instead of widening permissions:

1. the first attempt revoked the schema-owner role too early;
2. the second attempted to reapply superuser-only role attributes;
3. the first final verification asked the already-restricted admin login to
   inspect the application schema.

Each failure was visible as a non-zero Cloud Run Job execution. The bootstrap
was made idempotent, the privilege order was corrected, and the final check was
run by the schema owner. No public database route, broader role or model retry
was introduced as a workaround.

## Flyway and embedding imports

Two separate one-off jobs used the non-superuser migrator identity and the
reviewed application digest:

- Flyway applied V1 through V7 on PostgreSQL 17.11;
- runbook import: 12 total, 12 imported, 0 skipped;
- Nordly knowledge import: 28 total, 28 imported, 0 skipped;
- both imports used Vertex embeddings with the `vertex_ai` transport;
- the fresh database contained exactly 40 embedding rows.

## Runtime readiness

The private service started first with live AI and ADK disabled. Authenticated
readiness proved:

- health `UP`;
- `capabilities-v5`;
- Vertex AI with ADC in `eu`;
- exact pgvector cosine retrieval;
- runbook index ready at 12/12;
- Nordly corpus at 13 approved documents and 28 eligible chunks;
- database-global daily quota;
- no remediation capability;
- no write tool;
- Cloud Run IAM denied unauthenticated health with HTTP 403;
- authenticated root returned 404, so the backend-only service did not serve
  the frontend.

The paid-smoke revision used both a service maximum and revision maximum of one
instance, concurrency four and a 60-second request timeout. It had no public IAM
member.

## Safety gates before AI

The salary request returned HTTP 200 with:

- `blocked_before_ai`;
- reason `employee_compensation_request`;
- 0 model calls;
- 0 ADK tool calls;
- 0 reads;
- 0 embeddings;
- no action and no write capability.

The incident request with `confirm_live_ai=false` returned HTTP 400 and
`LIVE_AI_CONFIRMATION_REQUIRED`. Neither request consumed daily quota.

## One real ADK investigation

Exactly one confirmed ADK request was made. It completed on its first attempt:

- contract `nordly-adk-turn-v4`;
- `SequentialAgent` order:
  `nordly_evidence_agent` then `nordly_diagnosis_agent`;
- one registered read-only tool: `inspect_incident_evidence`;
- 2 model calls;
- 1 ADK tool call;
- 5 bounded read operations;
- 1 query embedding;
- 0 write operations;
- no action executed;
- human approval required for the proposed next step.

Exact pgvector retrieval ranked `runbook-cache-stale-checkout` first at cosine
similarity `0.7638620734`. Java then accepted the schema, citations, direct
evidence support, factual result, agent sequence, handoff, tool boundary and
final author before releasing the answer.

The conservative generation-only estimate was USD `0.00343550`. It excludes
embedding and infrastructure cost and is not a provider invoice.

## One real Nordly knowledge RAG request

Exactly one confirmed refund question was made. It returned:

- contract `nordly-knowledge-rag-v3`;
- outcome `answered`;
- exact pgvector cosine search;
- 13 eligible documents and 28 eligible chunks;
- one 768-dimensional Vertex query embedding;
- `nordly-evidence-refund-timing-card` ranked first at similarity
  `0.7963434706`;
- one cited approved source;
- Java checks for schema, citation scope, document approval, PII policy,
  output policy and the no-write boundary;
- 1 embedding call and 1 generation call;
- no action and no write capability.

The conservative generation-only estimate was USD `0.00083650`. Combined
generation estimate for both live requests was USD `0.00427200`, excluding
embeddings and infrastructure.

## Database-global quota and logging

Before the two live requests:

```text
QUOTA_CONSUMED_STARTS=0
QUOTA_CONSUMED_MICRO_USD=0
```

After exactly one ADK request and one knowledge request:

```text
QUOTA_CONSUMED_STARTS=2
QUOTA_CONSUMED_MICRO_USD=30000
```

No paid request was retried. The private service produced:

- 0 error-severity log entries;
- no database-password match;
- no request-text match;
- no raw provider-response-shape match.

## Replay invariant

Before and after the private smoke and again after teardown, the retained replay
service remained:

- service `incident-detective-backend-staging`;
- revision `incident-detective-backend-staging-00001-jfv`;
- digest
  `sha256:5748c0205722b9099251753a48c011b745c39c65607855248ea790659641063a`;
- service account `incident-detective-runtime@shirwac-incident-detective.iam.gserviceaccount.com`;
- no public IAM member;
- unauthenticated health 403;
- authenticated health 200 `UP`;
- authenticated root 404;
- deterministic recorded replay completed with no model or tool execution in
  the request.

The replay service account finished with its original
`roles/aiplatform.user` role only; it received no database or secret access.

## Teardown and residual network state

Deleted after evidence capture:

- private live-smoke service;
- bootstrap job;
- both import jobs;
- Cloud SQL instance;
- four temporary secrets;
- bootstrap, migrator and private-runtime service accounts;
- all temporary project IAM bindings.

Final provisioned-resource inventory:

```text
Cloud SQL instances: 0
Private Cloud Run services: 0
Cloud Run jobs: 0
Temporary service accounts: 0
Temporary database secrets: 0
VMs: 0
Disks: 0
Forwarding rules: 0
Cloud Routers / Cloud NAT: 0
External IPs: 0
```

Two non-application network references could not yet be removed immediately:

- the Direct VPC subnet is temporarily held by Google's managed
  `serverless-ipv4-*` reservation;
- Service Networking reports a recoverable producer resource after Cloud SQL
  deletion and temporarily blocks deleting the peering and reserved PSA range.

This is an expected asynchronous cleanup window after Direct VPC and private
Cloud SQL deletion. There is no remaining database, private service, job, VM,
disk, NAT or load balancer. Retry cleanup after Google releases the references:

1. delete the Service Networking peering;
2. delete `google-managed-services-incident-detective-staging`;
3. delete `incident-detective-run-euw1` after the managed serverless address
   disappears;
4. delete `incident-detective-staging-vpc`.

The retained Artifact Registry digest and replay service were intentionally not
deleted. The existing 90 SEK monthly budget alert remains configured; a budget
alert is not a spend stop.

## Primary references

- [Cloud Run Direct VPC egress](https://docs.cloud.google.com/run/docs/configuring/vpc-direct-vpc)
- [Cloud SQL private services access](https://docs.cloud.google.com/sql/docs/postgres/configure-private-services-access)
- [Cloud SQL connectors](https://docs.cloud.google.com/sql/docs/postgres/connect-connectors)
- [Cloud SQL PostgreSQL users and roles](https://docs.cloud.google.com/sql/docs/postgres/create-manage-users)
