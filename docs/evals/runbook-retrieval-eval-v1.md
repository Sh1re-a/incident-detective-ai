# Runbook retrieval eval

> Measured retrieval result. This is not a full diagnosis or prompt-injection safety eval.

## Reproducibility

- Suite: `runbook-retrieval-eval-v1`
- Suite SHA-256: `ec5c3138cd8c6e569107acb2b5e14ebdd6c2ea0f19a876396ce07136171a8303`
- Corpus: `runbook-corpus-v1`
- Corpus content SHA-256: `32a07893b823e5845cd4f24de3fcc3b401f655ff5d3883087240d0ff2ef7e41d`
- Git SHA: `522d90fa514b89cfe4a01e5217e271d6dfccb722`
- Backend: `pgvector_exact_cosine`
- Embedding: `gemini-embedding-2`, 768 dimensions
- Executed at: `2026-08-26T11:35:51.512474Z`

## Results

| Split | Positive Hit@4 | MRR | No-match accuracy | Unsafe top-1 on benign cases |
|---|---:|---:|---:|---:|
| development | 100.0% | 1.000000 | 100.0% | 0.0% |
| held_out | 80.0% | 0.800000 | 100.0% | 20.0% |

## Threshold

The threshold was selected from the development split only and frozen before held-out scoring.

- Frozen threshold: `0.662078`
- Configured runtime threshold: `0.662078`
- Runtime matches calibration: `true`
- Objective: 0.5 * Hit@4 + 0.5 * no-match accuracy
- Tie-break: higher no-match accuracy, then higher MRR, then lower threshold

## Usage and cost boundary

- Embedding calls: 14
- Local input characters: 2051
- Provider billable characters: not reported
- Provider input tokens: not reported
- Provider call latency: 4681 ms
- Estimated provider cost: not reported
- Cost status: not_calculated: provider usage metadata and billing tier are not both verified

## Cases

| Case | Split | Type | Pass | Relevant rank | Accepted | Top hit |
|---|---|---|---:|---:|---:|---|
| `dev-payment-timeout-config` | development | positive | true | 1 | 4 | runbook-payment-timeout-precedence (0.778271) |
| `dev-contract-response-decode` | development | positive | true | 1 | 2 | runbook-contract-breaking-response (0.811662) |
| `dev-database-pool` | development | positive | true | 1 | 2 | runbook-database-pool-exhaustion (0.852088) |
| `dev-stale-cache` | development | positive | true | 1 | 4 | runbook-cache-stale-checkout (0.828459) |
| `dev-order-idempotency` | development | positive | true | 1 | 4 | runbook-order-idempotency (0.845417) |
| `dev-no-match-sso` | development | no_match | true | not reported | 0 | runbook-unsafe-legacy-instructions (0.625747) |
| `dev-no-match-compute-capacity` | development | no_match | true | not reported | 0 | runbook-database-pool-exhaustion (0.659454) |
| `held-payment-incident-paraphrase` | held_out | positive | true | 1 | 4 | runbook-payment-timeout-precedence (0.751610) |
| `held-contract-incident-paraphrase` | held_out | positive | false | not reported | 4 | runbook-unsafe-legacy-instructions (0.724818) |
| `held-provider-degradation` | held_out | positive | true | 1 | 4 | runbook-payment-provider-degradation (0.870888) |
| `held-feature-flag-cohort` | held_out | positive | true | 1 | 3 | runbook-feature-flag-segment (0.786595) |
| `held-order-queue-lag` | held_out | positive | true | 1 | 4 | runbook-queue-order-backlog (0.849647) |
| `held-no-match-email` | held_out | no_match | true | not reported | 0 | runbook-queue-order-backlog (0.648240) |
| `held-adversarial-legacy-runbook` | held_out | adversarial | true | 1 | 4 | runbook-unsafe-legacy-instructions (0.829767) |

## Safety boundary

not_evaluated: retrieval of untrusted text is observed here; synthesis safety needs a separate pipeline eval

The adversarial runbook remains untrusted data. Its retrieval is an observation, not proof that synthesis resists indirect prompt injection.
