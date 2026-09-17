# Incident Lab Cloud Logging

## What is implemented

The `cloud` Spring profile enables Spring Boot's built-in single-line Logstash
JSON console format:

```properties
logging.structured.format.console=logstash
```

Cloud Run automatically captures container stdout/stderr. A JSON console line
is therefore available to Cloud Logging as structured `jsonPayload`; the
application does not call the Cloud Logging API and does not consume
`entries.write` API quota.

Deploy the backend with both runtime profiles:

```text
SPRING_PROFILES_ACTIVE=rag,cloud
```

Local `replay` and `rag` runs retain the normal human-readable console unless
the operator explicitly adds `cloud`.

## Safe lifecycle events

The Incident Lab emits these server-side events:

| `event_name` | Meaning |
|---|---|
| `incident_lab.plan.decided` | Safety and deterministic Java plan decision |
| `incident_lab.run.started` | A bounded synthetic case was generated |
| `incident_lab.alarm.evaluated` | The deterministic signal rule did or did not fire |
| `incident_lab.adk.started` | A fired alarm handed the case to the read-only ADK workflow |
| `incident_lab.adk.completed` | ADK returned its post-run receipt |
| `incident_lab.verification.completed` | Java recorded the verification gates and release decision |
| `incident_lab.run.completed` | The backend completed the human-facing outcome |
| `incident_lab.operation.failed` | A plan or run failed; only the exception type is recorded |
| `incident_lab.follow_up.started` | A question started against a frozen server-owned run receipt |
| `incident_lab.follow_up.completed` | A bounded answer completed with aggregate citation/provider counters |
| `incident_lab.follow_up.failed` | A follow-up failed; only hashed references and exception type are recorded |

Common correlation fields are `correlation_id`, `plan_ref`, `scenario_id`,
`alarm_id` and, after ADK returns, `adk_run_id`. `plan_ref` is a fingerprint of
the executable enum/boolean controls. It deliberately excludes the free-text
summary.

ADK completion adds aggregate counters, total latency, provider-reported token
usage and estimated model cost when those values are available. It also records
that write tools were unavailable and whether any action executed.

Follow-up events use short SHA-256 fingerprints of the opaque run and turn
references. They add mode, answer state, citation count, read/provider/model
counts and provider latency. The question, conversation, answer and source text
are never logging fields.

The logging API accepts no prompt, user instruction, model output, evidence
content, retrieved document text, customer data, GroundTruth or exception
message. This is intentional and covered by unit tests.

Example Cloud Logging queries after a revision is deployed and exercised:

```text
resource.type="cloud_run_revision"
jsonPayload.event_name="incident_lab.alarm.evaluated"
jsonPayload.alarm_fired=true
```

```text
resource.type="cloud_run_revision"
jsonPayload.correlation_id="<run correlation id>"
```

```text
resource.type="cloud_run_revision"
jsonPayload.event_name="incident_lab.verification.completed"
jsonPayload.answer_released=false
```

## What is not implemented yet

- No Cloud Logging query or production log entry is streamed into the demo UI.
  The Incident Lab's visible backend logs are synthetic request-local evidence.
- No log-based metric or Cloud Monitoring alert policy has been created.
- No Pub/Sub notification channel or alert-ingestion endpoint exists.
- No automatic remediation is triggered from a Cloud Logging entry.
- No Cloud Trace export or Cloud Logging/Trace correlation is claimed here.

A later production-shaped path may create a log-based metric, a Cloud
Monitoring alert policy and a Pub/Sub notification that calls a separately
authenticated ingestion boundary. That is distinct from the current safe
portfolio flow: synthetic signal data -> deterministic Java alarm -> read-only
ADK investigation -> Java verification -> human-owned next action.

References:

- [Cloud Run logging](https://docs.cloud.google.com/run/docs/logging)
- [Spring Boot structured logging](https://docs.spring.io/spring-boot/reference/features/logging.html#features.logging.structured)
