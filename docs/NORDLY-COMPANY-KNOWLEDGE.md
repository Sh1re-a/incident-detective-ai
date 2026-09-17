# Nordly company knowledge

Nordly Commerce AB is a fictional Nordic online retailer created for this
Applied AI demonstration. Every company fact, customer situation, employee
reference, document, log and incident is synthetic. The implementation is
real; the company is not.

The purpose of the corpus is to make the AI boundary understandable without
opening the source code. A visitor should be able to see that Nordly has a
normal mix of business policies, support procedures, architecture references,
incident playbooks and restricted material — and that not every document is
allowed into RAG.

## One company, two bounded AI jobs

- The **Nordly support agent** answers naturally from a synthetic customer
  context, current order snapshots and approved company documents. It can
  explain a policy but cannot cancel an order, refund money, change customer
  data or reveal restricted material.
- The **Nordly incident agent** investigates synthetic operational signals. It
  can inspect read-only metrics, logs, traces and runbooks, but it cannot deploy,
  roll back, purge a cache or modify production.

Both jobs share the same company world and human-approval principle. They do
not share hidden reasoning. The UI receives structured post-run receipts:
which boundary ran, which sources were retrieved, what the model returned and
what deterministic Java verification accepted or rejected.

## Canonical corpus

Runtime source:
`src/main/resources/knowledge/nordly-knowledge-corpus-v2.json`

- Manifest: `nordly-knowledge-manifest-v3`
- Corpus: `nordly-knowledge-corpus-v3`
- 17 documents and 35 chunks
- 14 approved public-demo documents and 32 eligible chunks
- 1 approved but restricted synthetic document
- 1 deprecated safety fixture
- 1 untrusted prompt-injection fixture
- Embedding profile declared by the corpus: `gemini-embedding-2`, 768 dimensions

The JSON manifest remains the single runtime source in this phase. Building a
Markdown parser or a separate document service would add complexity without
improving the current RAG proof.

## Document map

| File | Owner | Type | RAG status |
| --- | --- | --- | --- |
| `NLY-COMP-001_service-map-and-ownership.md` | Platform Operations | Service catalog | Eligible |
| `NLY-COMP-002_company-profile-and-customer-service.md` | Customer Operations | Company handbook | Eligible |
| `NLY-CX-101_order-lifecycle-and-cancellation.md` | Customer Operations | Policy | Eligible |
| `NLY-CX-102_returns-and-card-refunds.md` | Customer Operations | Policy | Eligible |
| `NLY-PAY-201_card-authorisation-capture-and-retry.md` | Payments Operations | Procedure | Eligible |
| `NLY-FUL-301_delivery-and-missing-parcels.md` | Fulfilment | Policy | Eligible |
| `NLY-SEC-401_data-handling-and-retrieval-boundaries.md` | Security and Privacy | Standard | Eligible |
| `NLY-INC-501_incident-response-handbook.md` | Incident Command | Playbook | Eligible |
| `NLY-INC-502_safe-release-and-rollback.md` | Developer Experience | Policy | Eligible |
| `NLY-RUN-601_payment-timeout-investigation.md` | Checkout Reliability | Runbook | Eligible |
| `NLY-RUN-602_service-contract-compatibility.md` | Platform Architecture | Runbook | Eligible |
| `NLY-RUN-603_catalog-cache-invalidation.md` | Commerce Platform | Runbook | Eligible |
| `NLY-RUN-604_order-event-backlog.md` | Fulfilment Platform | Runbook | Eligible |
| `NLY-RUN-605_order-idempotency.md` | Order Platform | Runbook | Eligible |
| `NLY-HR-901_synthetic-individual-compensation.md` | People Operations | Restricted register | Excluded before embedding |
| `NLY-CX-998_legacy-refund-timing.md` | Customer Operations | Historical fixture | Deprecated and excluded |
| `NLY-SEC-999_untrusted-imported-checkout-notes.md` | Security Evaluation | Security fixture | Untrusted and excluded |

The public document API returns full synthetic text only for documents that
are both `APPROVED` and scoped `public_demo`. Restricted, deprecated and
untrusted documents remain visible as metadata so the exclusion decision can
be explained, but their body is not returned.

## What semantic search should prove

The corpus contains intentional relationships that cannot be demonstrated by
keyword lookup alone:

- two visible card amounts connect authorization versus capture with the
  support verification procedure;
- HTTP 200 connects a successful transport response with an incompatible
  response schema;
- a successful checkout connects to a delayed order through queue lag;
- a release close to an error connects to evidence requirements and the
  human-approved rollback policy;
- embeddings and pgvector connect a natural-language question to relevant
  chunks, while lifecycle and access filtering happen before ranking;
- missing evidence produces `insufficient_evidence`, not an invented answer.

The retrieval contract is defined in
`src/main/resources/evals/nordly-knowledge-retrieval-eval-v2.json`. It contains
Swedish and English development, held-out, multi-source, no-match and safety
cases. Its current status is deliberately `PENDING_PROVIDER_RUN`: structural
tests can prove that every expected source is eligible and every risky prompt
is blocked, but only an explicitly approved provider run can measure semantic
retrieval quality.

## What this demonstrates to a reviewer

The demo is designed so the practical AI work can be inspected without reading
the source code:

- **Embeddings:** an explicit import embeds every eligible document chunk. A
  confirmed, configured run with a ready index embeds the accepted question
  with the declared model and dimensionality.
- **Semantic search:** pgvector ranks meaning, not only exact keywords, and the
  receipt distinguishes similarity from confidence.
- **RAG:** only the small set of chunks retrieved in the current run is sent to
  the answer model; citations must point back to that same set.
- **Data governance:** lifecycle and access scope decide what may be embedded
  before vector ranking begins. Restricted, deprecated and untrusted content is
  excluded from the live index.
- **Responsible agent design:** Java owns the request gate, tool allowlist,
  provider budget, schema checks and release decision. The model cannot silently
  promote its own permissions.
- **Evaluation:** frozen Swedish and English questions test retrieval, no-match,
  multi-source answers and blocked requests separately from a polished demo.
- **Operational proof:** response receipts identify the corpus fingerprint,
  embedding profile and provider route selected for provider calls registered
  in that run. A route can therefore describe an attempted call even when the
  provider fails; unreported usage or cost stays unknown instead of being shown
  as zero.

## Safety boundary

The request gate runs before query embedding and model generation. It blocks:

- customer personal data;
- individual employee compensation;
- credentials and secrets;
- prompt-injection attempts;
- financial actions;
- write actions against company systems.

Blocked requests must produce zero embedding calls, zero generation calls and
zero actions. An allowed request still receives only ranked approved chunks.
The model must cite retrieved evidence IDs, and Java checks citations, output
shape and forbidden patterns before releasing the answer.

## Honest evidence states

- The corpus files and provider-free tests prove structure, access projection,
  deterministic filtering and import idempotency.
- A configured Vertex route proves configuration only, not reachability.
- A pgvector strategy proves the selected backend only, not index readiness.
- A live semantic-search claim requires a current query embedding, a ready
  current index and ranked matches in the same run receipt.
- Similarity is a ranking signal, never model confidence.
- Paid provider measurements, Cloud SQL readiness and Cloud Run revision proof
  belong to the next explicitly approved cloud phase.
