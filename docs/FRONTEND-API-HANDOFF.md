# Backend-API: handoff till frontend

- **Senast uppdaterad:** 10 september 2026

Det här dokumentet är kontraktet för chatten som bygger om frontend. Backend är
källan för generator-, scenario-, körnings-, capability- och evaldata.
Frontend ska inte återskapa dessa sanningar som hårdkodade konstanter.

## Anslutning

- Lokal backend: `http://localhost:8080`
- API-bas: `/api/v1`
- OpenAPI: `/v3/api-docs`
- Lokal Swagger: `/swagger-ui.html`
- JSON-fält använder `snake_case`.
- Felsvar använder `application/problem+json` och ett stabilt `code`-fält.

Använd helst samma origin: låt frontendens dev-proxy eller produktionsproxy
skicka `/api` till backend. Då behövs ingen CORS-policy i browsern.

Om frontend måste ligga på en separat origin sätts en exakt allowlist på
backend, till exempel:

```properties
INCIDENT_DETECTIVE_API_ALLOWED_ORIGINS=http://localhost:5173,https://demo.example.com
```

Wildcard, path, query, fragment och användarinfo avvisas. CORS gäller endast
`/api/v1/**`, tillåter `GET`, `POST`, `OPTIONS`, rubrikerna `Accept` och
`Content-Type`, exponerar svarsheadern `Retry-After`, och tillåter inte
credentials. Frontend ska därför inte bygga kontraktet runt cookies.

## Håll typerna synkroniserade med OpenAPI

`GET /v3/api-docs` är det auktoritativa kontraktet. Den nuvarande
`frontend/src/api/generated.ts` är typad från det lokala kontraktet, men någon
reproducerbar OpenAPI-generator är ännu inte verifierad. Behandla därför varje
ändring i filen som en manuell kontraktsändring tills ett build-time-kommando
finns och är testat. Flera modell-, usage- och kostnadsfält är avsiktligt
nullable.

Gör detta som ett lokalt/build-time-steg, inte genom att hämta OpenAPI från en
separat browser-origin i den färdiga appen. Den valfria CORS-allowlisten gäller
avsiktligt bara `/api/v1/**`, inte Swagger eller `/v3/api-docs`.

## De elva produkt- och proof-endpointsen

| Metod | Path | Användning |
|---|---|---|
| `GET` | `/api/v1/capabilities` | Aktiv runtime-konfiguration, säkra gränser och vilka AI-funktioner backend faktiskt erbjuder. |
| `GET` | `/api/v1/demo-world` | Nordlys versionshanterade företagsvärld, servicekarta, frågor och korpusantal. |
| `GET` | `/api/v1/knowledge/documents` | Read-only bibliotek över den syntetiska kunskapskorpusen och dokumentens lifecycle/access scope. |
| `POST` | `/api/v1/knowledge/questions/runs/rag` | Explicit bekräftad fri Nordly-fråga genom säkerhetsgrind, godkänd korpus, Gemini embedding, pgvector, avgränsad Gemini-syntes och Java-verifiering. Endast under profilen `rag`. |
| `POST` | `/api/v1/knowledge/questions/{questionId}/runs/recorded-replay` | Backenddriven kunskapsreplay med rankade källor, svar, verifiering, kvitto och uttryckliga runtime-begränsningar. |
| `POST` | `/api/v1/agent/turns` | Explicit bekräftat `SequentialAgent`-flöde med två avgränsade ADK-agenter, post-run-events, workflow-kvitto och separat Java release gate. |
| `GET` | `/api/v1/scenarios` | Säkra scenariosammanfattningar utan evidensinventarium eller facit. |
| `POST` | `/api/v1/scenarios/{scenarioId}/runs/recorded-replay` | Gratis, deterministisk körning utan modellrequest. |
| `POST` | `/api/v1/scenarios/{scenarioId}/runs/live-ai` | Ny, uttryckligen bekräftad Gemini-körning med read-only tools. |
| `POST` | `/api/v1/generated-cases/runs/live-ai` | Genererar en vald request-lokal Nordly-incidentfamilj och utreder den synkront med Gemini och read-only tools. |
| `GET` | `/api/v1/proof/evals/retrieval` | Publicerad, historisk och aggregerad retrieval-eval. |

Det finns ingen HTTP-endpoint som startar en eval, importerar embeddings eller
skriver till databasen. Proof-endpointen är en read-only publicerad snapshot.

För Generated Synthetic Case finns exakt **ett** API-anrop:
`POST /api/v1/generated-cases/runs/live-ai`. Det finns inget separat create-anrop,
ingen polling/status-endpoint, ingen TTL, ingen upload och ingen endpoint för
att hämta ett genererat fall senare. Generator, AI-utredning och verifiering
sker inom samma synkrona request. Fallet är request-lokalt och persisteras inte.

## Rekommenderad laddningsordning

1. Hämta `capabilities`, `demo-world`, `scenarios` och den read-only
   retrieval-proof som frontenden visar parallellt när appen startar.
2. Kräv `contract_version = capabilities-v4` för den här integrationen och
   bygg generatorns val från `generated_cases`, inte från egna konstanter.
3. Visa fri Nordly-fråga som primärt läge när
   `capabilities.retrieval.active_profiles` innehåller `rag`; visa annars
   recorded replay som stabilt standardläge och erbjud inte en död live-route.
4. Visa live-kontrollen utifrån `live_ai.request_routing_configured`, men låt alltid
   serverns svar vara auktoritativt eftersom providerhälsa kan ändras efter
   laddningen.
5. Behandla retrieval-proof som kompletterande historiskt bevis: ett fel där
   får inte stoppa scenario/replay, och svaret får aldrig beskrivas som
   current-run-data.

## Nordly Knowledge Room

`GET /api/v1/demo-world` är källan till företagsnamn, marknader, servicekarta,
frågeval och korpusantal. Frontend får välja vilka backendfrågor som lyfts fram,
men får inte skriva egna företagsfakta, svar eller tekniska körvärden.

De befintliga fälten utan språksuffix är svenska kompatibilitetsfält. Använd
`truth_label_en`, `company.description_en`, `company.markets_en`,
`company.industry_en` samt `services[].display_name_en` och `role_en` i den
engelska vyn. `POST .../runs/rag` returnerar också `truth_label_en`. Vid ett
blockerat anrop är `question.text` redan maskerad på requestens `locale`
(`sv` eller `en`); frontend ska aldrig försöka återskapa den inskickade frågan.

Dokumentbiblioteket använder `nordly-knowledge-document-library-v2`.
`manifest_version` och `corpus_content_sha256` identifierar exakt den
godkända korpus som får bäddas in. Varje godkänd, publik chunk har
`content_sha256`; restricted, deprecated och untrusted material har både
`text = null` och `content_sha256 = null`. UI:t kan därför visa att ett
dokument finns och varför det är exkluderat utan att lämna ut dess body.

```http
POST /api/v1/knowledge/questions/{questionId}/runs/recorded-replay
```

Replayen är en validerad, lokalt lagrad snapshot. Dess sanning är bland annat:

- `mode = recorded_replay`;
- `model_backed = false`;
- `current_vector_search = false`;
- `retrieval.backend = recorded_snapshot`;
- `query_embedding.executed_in_this_run = false`;
- similarity, latency, tokens och uppskattad kostnad är nullable;
- `receipt.write_tools_available = false` och `action_executed = false`.

`embedding_profile` beskriver korpusens avsedda profil, inte ett anrop i denna
request. Ett dokumentkort får visa backendens svenska/engelska
`display_summary`, men den exakta källtexten måste fortfarande visas separat.
En replay med `refused` eller `insufficient_evidence` är ett lyckat säkert
produktutfall, inte ett frontendfel.

### Fri fråga genom live RAG

```http
POST /api/v1/knowledge/questions/runs/rag
Content-Type: application/json

{
  "question": "När syns en godkänd återbetalning på kortet?",
  "locale": "sv",
  "confirm_live_ai": true
}
```

Routen finns endast när Spring-profilen `rag` är aktiv. Tillåtna utfall är
`answered`, `refused`, `insufficient_evidence`, `confirmation_required`,
`unavailable` och `rejected_output`.

- Säkerhetsgrinden kör före databas och provider. Blockerad input redigeras och
  kvittot måste visa noll provider-/embedding-/generation-anrop.
- En tillåten fråga utan `confirm_live_ai = true` ger
  `confirmation_required` och noll provideranrop.
- En komplett körning gör högst ett query-embedding-anrop och ett
  Gemini-svarsanrop. Inga tools eller skrivfunktioner exponeras för modellen.
- Endast dokument med `lifecycle = APPROVED` och access scope `public_demo`
  finns i Nordly-indexet. Current-run-responsen rapporterar 13 möjliga dokument
  och 27 textstycken för den versionshanterade V2-korpusen.
- Kontraktet `nordly-knowledge-rag-v2` returnerar
  `retrieval.corpus_content_sha256`, ett nullable `index_snapshot` och
  `content_sha256` för varje faktisk träff. Saknat indexsnapshot betyder att
  readiness inte lästes i körningen; det får inte visas som ett redo index.
- `provider_route` finns bara när minst ett provideranrop registrerades. Den
  visar transport, auth-läge och eventuell Vertex-location, men aldrig
  project-id eller credentials. Nollanrop ger `provider_route = null`.
- `phases[]` är efterhandskvitton över observerat systemarbete, aldrig dold
  tankekedja eller simulerad streaming.
- `ranked_matches[].similarity` är rå cosine-likhet och får inte presenteras som
  säkerhet eller answer confidence.
- `verification` skiljer schema, käll-ID inom hämtad kontext, approved-only,
  avgränsad utdata-policy/PII-scan och read-only-förmåga. Den bevisar inte
  semantisk entailment för varje mening.
- `receipt.cost_status` och `receipt.cost_basis` avgör kostnadscopyn.
  `estimated_cost_usd` är nullable och generationens listprisestimat inkluderar
  inte embeddingkostnad eller faktisk fakturadebitering.
- Frontend ska uttryckligen varna att endast syntetiska frågor får skrivas och
  att demoskyddet inte är ett komplett DLP-system.

`live_ai.enabled_by_configuration` och
`provider.routing_configuration_complete` visar de två lokala
routingförutsättningarna separat. `live_ai.request_routing_configured = true`
betyder att båda är uppfyllda. `provider.credential_status = not_checked` för
Vertex eftersom capability-endpointen medvetet inte laddar eller validerar ADC.
Inget av fälten garanterar lyckad autentisering eller att providern är nåbar.

## Kontrollerat ADK-flöde

```http
POST /api/v1/agent/turns
Content-Type: application/json

{
  "seed": 42,
  "incident_family": "catalog_cache_invalidation",
  "evidence_mode": "diagnostic",
  "noise_level": "low",
  "message": "Undersök larmet och förklara vad bevisen faktiskt stödjer.",
  "confirm_live_ai": true
}
```

Endpointen använder Google ADK for Java och kontrakt
`nordly-adk-turn-v3`. Java kör säkerhetsgrinden före livebudget, ADK Runner,
Gemini, tools och embeddings. En tillåten och bekräftad request skapar en
request-lokal in-memory-session och kör ett `SequentialAgent` i denna fasta
ordning:

1. `nordly_evidence_agent` har det enda registrerade verktyget,
   `inspect_incident_evidence`, och får anropa det exakt en gång. Funktionen
   kan göra avgränsade read-only-läsningar av metrics, loggar, traces och
   runbooks.
2. ADK:s strukturerade `FunctionResponse` lämnas direkt vidare i eventströmmen
   till `nordly_diagnosis_agent`. Den agenten har inga tools och får bara skapa
   en diagnoskandidat från den överlämnade evidensen. Gemini-anropet binds till
   `application/json` och `diagnosis-schema-v4`; prompttext är inte den enda
   formatgränsen.
3. Efter ADK-körningen avgör deterministisk Java-kod om kandidaten får visas.
   Ingen agent kan skriva, genomföra remediation eller godkänna sitt eget svar.

Båda barnen har ett modellsteg och hela körningen har hard cap
`model_calls = 2`. Svaret får bara ha `outcome = completed` när det observerade
spåret dessutom visar exakt ett ADK-tool-anrop, rätt agentordning, giltig direkt
evidensöverlämning, tool-fri diagnosagent, rätt slutlig författare samt godkänt
schema, giltiga citerade ID:n, direkt stöd mellan påstående och källa samt
faktastöd. Annars är utfallet `verification_failed` och `diagnosis` hålls inne.
Om sluttexten inte ens klarar `Diagnosis`-kontraktet
returneras samma inspekterbara HTTP 200-utfall med de verkliga ADK-eventen,
tool-events och kvittot bevarade. Då är `verification_event.schema_valid = false`
och `diagnosis`, `verification` samt `comparison` är null; citationer och
faktastöd kördes inte.

### Workflow- och kontrollkvitto i v3

- `workflow.type = sequential_agent`.
- `workflow.expected_agent_order` kommer från backendens konfiguration.
- `workflow.observed_agent_order` härleds från de returnerade ADK-eventen.
- `workflow.evidence_handoff = adk_function_response`.
- `workflow.final_response_author` ska vara `nordly_diagnosis_agent`.
- `workflow.completed_in_order` är backendens samlade trajectory-utfall.
- `verification_event` redovisar separat `agent_sequence_valid`,
  `evidence_handoff_valid`, `tool_boundary_valid`, `final_author_valid`,
  `citations_valid`, `direct_evidence_support_valid` och `answer_released`.
  `citations_valid` betyder att de citerade ID:na faktiskt lästes;
  `direct_evidence_support_valid` betyder att varje källa också stöder det
  exakta påståendet. De två kontrollerna får inte slås ihop i UI:t.
- `receipt` redovisar modellanrop, ADK-tool-anrop, underliggande read-only-
  operationer, embeddings, registrerade tools, tokens, listprisestimat och
  latency. `write_tools_available` och `action_executed` ska vara `false`.
- `provider_route` finns bara när minst ett modell- eller embeddinganrop
  faktiskt registrerades. Blockerat före AI ger `null`.

`events[]` är sanerade, backendregistrerade post-run-events. Frontend får visa
författare, tool call, `FunctionResponse`, tokenmetadata och ordning, men inte
märka dem som streaming eller modellens privata resonemang. Text från
mellansteget hålls inne; den slutliga strukturerade diagnosen visas endast i
det separata `diagnosis`-fältet efter Java-godkännande.

### Säkerhetsblockering utan anrop

En känd riskfråga kan returnera HTTP `200` med
`outcome = blocked_before_ai`. Då gäller:

- `session_id`, `scenario`, `workflow`, `diagnosis`, `verification`,
  `comparison` och `verification_event` är `null`;
- `runtime.runner_invoked = false`;
- `events` och `tool_events` är tomma;
- `receipt.model_calls = 0`, `adk_tool_calls = 0`, `read_operations = 0` och
  `embedding_calls = 0`;
- `write_tools_available = false` och `action_executed = false`.

Frontend ska visa detta som ett lyckat skyddsutfall, inte som att agenten körde
och sedan vägrade. En tillåten request utan `confirm_live_ai = true` ger i
stället `400 LIVE_AI_CONFIRMATION_REQUIRED` före quota och provider.

## `GET /api/v1/capabilities`

Viktiga fält:

- `contract_version = capabilities-v4`: capability-kontraktet som innehåller
  stöd för Generated Synthetic Case och aktuell vector-index-readiness.
- `synthetic_only`: är alltid `true` i den här demon.
- `remediation_enabled`: är alltid `false`.
- `provider`: vald Google Gen AI-transport, auth-läge, eventuell location,
  routingstatus och en explicit credential-status. Det är inte ett health check.
- `deployment`: `local` eller `cloud_run` samt nullable revision och injicerad
  build-SHA. Frontend får inte fylla nullvärden med antaganden.
- `knowledge_corpus`: manifestversion, corpusversion, deterministisk hash och
  antal godkända dokument/chunks.
- `modes`: truth label, model-backed-status och bekräftelsekrav per körläge.
- `tools`: de typade funktioner som finns; alla är read-only.
- `diagnostic_probe`: namnet på ADK-funktionen, backendens exakta
  probe-allowlist och bevis på att varje probe är case-bound, read-only och
  aldrig utför en åtgärd.
- `live_ai`: separat serveraktivering, lokal request-routing, aktiv
  modell/prompt, thinking level och backendens
  hårda call-/tidsbudgeter.
- `generated_cases`: generatorns kontraktsversion, version, truth label,
  tillåtna controls och data-/persistensgräns.
- `retrieval`: den retrieval-backend som är aktiv i just denna backendprocess.
- `prompt_cache`: faktisk cachepolicy, inte ett marknadsföringspåstående.

Visa inte egna hårdkodade budgetar eller modellnamn när samma värde finns här.
Endpointen returnerar aldrig API-nyckeln.

### Exakt `generated_cases`-capability i `capabilities-v4`

```json
{
  "enabled": true,
  "contract_version": "generated-live-run-v1",
  "generator_version": "nordly-incident-generator-v2",
  "truth_label": "Generated synthetic incident — real AI investigation.",
  "user_supplied_data_accepted": false,
  "request_local_only": true,
  "incident_families": [
    "payment_timeout",
    "catalog_cache_invalidation",
    "order_event_backlog",
    "order_idempotency_failure"
  ],
  "evidence_modes": ["diagnostic", "insufficient_evidence"],
  "noise_levels": ["none", "low"],
  "allowed_tools": [
    "get_metrics",
    "search_logs",
    "get_trace",
    "retrieve_runbooks"
  ]
}
```

Använd `generated_cases.enabled` för att visa funktionen och arrayerna för att
bygga valen. `live_ai.request_routing_configured = true` betyder bara att
backendens lokala routingförutsättningar finns; det garanterar inte lyckad
autentisering eller providerhälsa. Fältet
`live_ai.budget.daily_live_run_limit = 20` visar taket och
`daily_quota_scope` visar om det är `process_local` eller
`database_global`. Standardprofilens processlokala räknare återställs vid
omstart; `rag`-profilens räknare är atomisk och delad via PostgreSQL. API:t
returnerar inte hur många körningar som återstår. Frontend får inte hitta på en
remaining-counter eller kalla `process_local` för ett globalt kostnadsskydd.

### Exakt `diagnostic_probe`-capability

```json
{
  "function_name": "run_diagnostic_probe",
  "allowed_probe_ids": [
    "service_health",
    "dependency_status",
    "release_metadata",
    "config_fingerprint_diff"
  ],
  "case_bound": true,
  "read_only": true,
  "action_executed": false
}
```

Frontend använder detta objekt för att förklara vad agenten får göra. Den ska
inte bygga en egen lista eller antyda terminalåtkomst, skrivverktyg eller
automatisk reparation.

### Aktiv retrieval är inte samma sak som evalbevis

`capabilities.retrieval.backend` har två möjliga sanningar:

- `deterministic_fixture`: standard/replay-profilen använder lokal,
  deterministisk matching. `vector_database_backend_active` är `false` och
  `active_embedding_profile` är `null`.
- `pgvector_exact_cosine`: `rag`-profilen använder Gemini embeddings och exakt
  cosine-sökning i PostgreSQL/pgvector. `vector_database_backend_active` är `true` och
  `active_embedding_profile` är ifyllt. `index_status` rapporterar dessutom
  `ready`, korpusversion samt indexed/current/expected chunks från den databas
  som den nuvarande processen faktiskt använder.

`index_status = null` betyder att fixture-profilen inte har något aktivt
vektorindex. `index_status.ready = true` kräver både rätt antal chunks och att
varje lagrad content-hash matchar den versionshanterade korpusen. Readiness är
aktuell backendstatus; den är fortfarande inte bevis för att en viss körning
anropade `retrieve_runbooks`.

Visa alltså inte “Vector database active” bara för att den publicerade
retrieval-evalen finns. Proof visar vad som mättes i en fryst historisk körning;
capabilities visar vad den nuvarande processen kör.

När livekörningen faktiskt väljer `retrieve_runbooks` finns motsvarande
metadata i tool-eventets `runbook_retrieval`. För andra tools är fältet `null`.
Vid fixture-backend är corpus-, embedding- och provider-usagefält `null`; vid
pgvector-backend innehåller svaret bland annat rank, similarity och hash.

## Scenario och körningar

### Scenario-lista

`GET /api/v1/scenarios` är säker att använda i startvyn. Den läcker inte dolt
`GroundTruth`, ett evidence inventory eller en färdig diagnos.

### Recorded replay

```http
POST /api/v1/scenarios/{scenarioId}/runs/recorded-replay
```

Ingen body krävs. Ett replay-resultat har:

- `mode = recorded_replay`
- `truth_label = Simulated incident — recorded deterministic replay.`
- `status = completed`
- `model_id = null`
- `prompt_version = null`
- `token_usage = null`
- `estimated_cost_usd = null`

Null-värdena betyder att ingen modell kördes. Byt dem inte mot noll eller ett
modellnamn i presentationen.

### Live AI

```http
POST /api/v1/scenarios/{scenarioId}/runs/live-ai
Content-Type: application/json

{"confirm_live_ai": true}
```

Live kräver både serveraktivering och `confirm_live_ai: true` för varje ny
körning. Ett lyckat svar har `mode = live_ai` och truth label `Simulated
incident — real AI investigation.` Körstatus kan vara `completed` eller
`verification_failed`; det senare är fortfarande ett inspekterbart HTTP
200-resultat, inte ett transportfel.

Frontend får aldrig ersätta ett livefel med replay under samma truth label.
Erbjud i stället recorded replay som ett separat, tydligt användarval.

### Generated Synthetic Case: ett synkront API

```http
POST /api/v1/generated-cases/runs/live-ai
Content-Type: application/json

{
  "seed": 42,
  "incident_family": "catalog_cache_invalidation",
  "evidence_mode": "diagnostic",
  "noise_level": "low",
  "confirm_live_ai": true
}
```

`seed`, `evidence_mode`, `noise_level` och `confirm_live_ai` är obligatoriska.
`incident_family` är valfritt för bakåtkompatibilitet och defaultar till
`payment_timeout`. Frontend ska ändå skicka användarens val uttryckligen.
Backend avvisar även okända JSON-fält.

| Requestfält | Tillåtna värden | Faktisk betydelse |
|---|---|---|
| `seed` | JSON-heltal inom Java `Long` | Styr den deterministiska Java-generatorn. Samma seed och controls ger samma genererade scenario/signaler, men en ny `run_id` och inte nödvändigtvis identiskt Gemini-resultat. |
| `incident_family` | `payment_timeout`, `catalog_cache_invalidation`, `order_event_backlog`, `order_idempotency_failure` | Väljer en av fyra avgränsade syntetiska Nordly-familjer. Utelämnat värde betyder `payment_timeout`. Det är inte fri logguppladdning eller fri agentgenerering. |
| `evidence_mode` | `diagnostic` | Innehåller den syntetiska evidens som krävs för att ställa en diagnos. |
| `evidence_mode` | `insufficient_evidence` | Utelämnar avsiktligt avgörande konfiguration/trace. En korrekt modellrespons ska avstå med `diagnosis.status = insufficient_evidence`. |
| `noise_level` | `none` | Lägger inte till generatorns distraktorsignal. |
| `noise_level` | `low` | Lägger till en begränsad, uttryckligen syntetisk och orelaterad varning från en annan Nordly-service. |
| `confirm_live_ai` | endast `true` startar | Är ett nytt uttryckligt godkännande för just denna potentiellt kostnadsbärande modellkörning. `false` eller utelämnat fält ger `LIVE_AI_CONFIRMATION_REQUIRED`. |

En ogiltig enum, saknat `seed`, `evidence_mode` eller `noise_level`, fel JSON-typ
eller ett okänt fält ger `400 INVALID_REQUEST_BODY`. Låt helst en
OpenAPI-genererad klient skapa body:n.

#### Exakt response-wrapper

HTTP `200` returnerar alltid en `GeneratedCaseRunResult` med exakt tre
toppnivåfält; det finns inget mellanliggande case- eller jobbsvar:

```text
GeneratedCaseRunResult {
  contract_version: "generated-live-run-v1"
  generation: {
    generator_version: "nordly-incident-generator-v2"
    seed: int64
    incident_family: "payment_timeout" | "catalog_cache_invalidation" |
                     "order_event_backlog" | "order_idempotency_failure"
    evidence_mode: "diagnostic" | "insufficient_evidence"
    noise_level: "none" | "low"
  }
  investigation: LiveInvestigationResult
}
```

`investigation` är samma typade `LiveInvestigationResult` som den befintliga
live-endpointen använder. Objektets fullständiga toppnivåfält är:

```text
run_id, scenario_id, mode, truth_label, status,
started_at, completed_at, latency_ms, scenario, tool_events,
diagnosis, verification, comparison, model_id, prompt_version,
model_calls, token_usage, prompt_cache, estimated_cost_usd,
model_cost_breakdown, estimated_cost_basis, tool_call_count,
model_call_count, limitations
```

Följande värden är särskilt viktiga för generated-läget:

- `mode = live_ai`
- `truth_label = Generated synthetic incident — real AI investigation.`
- `status` är `completed` eller `verification_failed`
- `scenario_id` börjar med ett familjebundet prefix, exempelvis
  `generated-payment-timeout-` eller `generated-order-event-backlog-`
- `diagnosis.status` är `diagnosed` eller `insufficient_evidence`

`verification_failed` är ett komplett, inspekterbart HTTP `200`-resultat där
Java-verifieraren hittade hard errors. Det är inte samma sak som
`diagnosis.status = insufficient_evidence`: en korrekt abstention för
`evidence_mode = insufficient_evidence` kan ha körstatus `completed`.

#### GroundTruth- och datagräns

Det råa facitobjektet `GroundTruth` exponeras aldrig. Det serialiseras inte i
response-wrappern och ges inte till Gemini, modellprompten eller ett tool. Det
finns alltså inget `ground_truth`- eller `hidden_ground_truth`-fält som
frontend ska läsa.

Efter att modellen har svarat använder Java det dolda facitobjektet för
verifiering. De avsiktligt publika **resultaten** av kontrollen finns i
`verification` och `comparison`; exempelvis schema-/citation-/coveragefält och
`comparison.expected_*`. Visa dessa endast som post-run verifieringsresultat,
inte som bevis på att modellen såg facit eller som en aggregerad accuracy.

Backend tar inte emot fri text, filer, logguploads eller riktiga företagsdata i
det här flödet. De enda användarindata som accepteras är de fem avgränsade
controls ovan.

#### Tool events och evidens

Varje post i `investigation.tool_events` har exakt dessa fält:

```text
event_id, collection_round, tool_name, arguments, safe_summary,
evidence, runbook_retrieval
```

- `tool_name` är `get_metrics`, `search_logs`, `get_trace` eller
  `retrieve_runbooks`.
- `collection_round` är den riktiga modellrundan. Backend tillåter högst två
  collection-rundor, tre tool calls per runda och åtta totalt.
- `arguments` är de validerade read-only-argument som modellen faktiskt
  skickade; `safe_summary` är backendens säkra sammanfattning av resultatet.
- `evidence` är en lista med discriminatorn `evidence_type`: `metric`, `log`,
  `trace` eller `runbook`. Använd `evidence_id` som stabil länk från ett
  tool-event till `diagnosis.claims[].evidence_ids` och verifieringen.
- `runbook_retrieval` är `null` för alla andra tools. För
  `retrieve_runbooks` beskriver objektet den faktiskt använda
  retrievalbackendens metadata; dess egna providerfält kan vara `null`.

Tool-eventsen finns först när det synkrona svaret är färdigt. Frontend kan
animera dem i ordning som en visuell replay av den returnerade spårningen, men
får inte märka animationen som streaming eller “AI:s tankar”. Backend exponerar
tool calls, evidens och verifieringsutfall — inte dold chain-of-thought.

#### Begränsningar som ska synas i UI

`investigation.limitations` är det auktoritativa värdet. Generated-svaret
innehåller följande stabila gränser plus den aktiva retrieval-backendens egen
limitation:

- incident och signaler kommer från en versionerad syntetisk template;
- användartext, filer och riktiga företagsdata accepteras inte;
- correctness kontrolleras endast mot det genererade fallets GroundTruth och
  är inte ett generellt påstående om modellaccuracy;
- fallet finns endast under requesten och persisteras inte;
- systemet rekommenderar next steps men utför aldrig remediation.

### Visuell frontend: bind varje scen till faktisk responsdata

| Scen i demon | Fält som driver den | Vad UI:t sanningsenligt kan visa |
|---|---|---|
| 1. Generator | request-controls + `generation.*` | Seed, evidence mode, noise level och version. Visa “reproducible synthetic case”, inte “uploaded logs”. |
| 2. Incident | `investigation.scenario` | Titel, tidsfönster, affected services, business impact och initial symptoms. |
| 3. Tool calls | `tool_events[]` | Runda, tool, arguments och `safe_summary` i verklig returordning. |
| 4. Evidens | `tool_events[].evidence[]` | Färgkoda metric/log/trace/runbook och korslänka samma `evidence_id` till claims. |
| 5. Diagnos | `diagnosis` | Status, business/technical summary, typade claims och ett säkert next step som kräver mänskligt godkännande. |
| 6. Verifiering | `status`, `verification`, `comparison` | Visa separata checks för schema, citation validity, evidence precision, claim coverage och correctness; skapa inte ett eget “AI score”. |
| 7. Kostnad | `model_calls`, `token_usage`, `prompt_cache`, `estimated_cost_usd`, `model_cost_breakdown`, `estimated_cost_basis` | Modellrundor, rapporterade tokens/cache och list-price-estimat med rätt null-läge och tydlig “estimate, not invoice”-text. |

Det starkaste visuella mönstret är alltså
**generator → tool calls → evidens → diagnos → verifiering → kostnad**. Visa
truth label permanent i resultatvyn och låt varje claim öppna den evidens som
dess `evidence_ids` pekar på.

### Daterad live-smoke — observation, inte accuracy

En faktisk smoke den **2026-08-31** med `seed = 42`,
`evidence_mode = diagnostic` och `noise_level = low` gav HTTP `200` och
`status = completed`.
Gemini valde `get_metrics`, två `search_logs` och `get_trace`; dessa fyra
tool-events gav sex evidensitems. Diagnosen blev `PAYMENT_TIMEOUT_CONFIG` /
`PAYMENT_ADAPTER`, citationerna var giltiga, claim coverage var `1.0` och
`hard_errors` var tom. Tre model calls rapporterade totalt `6 879` tokens,
`cache_hit_observed = false` och ett beräknat listprisestimat på
`$0.00305850`.

Detta är en verifierad enskild end-to-end-observation, inte en accuracy-,
latency- eller kostnadsgaranti. Modellen valde **inte** `retrieve_runbooks` i
den körningen. Frontend ska därför inte animera RAG som använt i just den
spårningen. RAG kan visas som aktiv capability, genom `runbook_retrieval` när
ett faktiskt tool-event innehåller metadata, genom recorded replay eller som
det tydligt frysta retrieval-proofet — aldrig som ett fabricerat steg i den
aktuella körningen.

## Null-kontrakt för modell, cache och kostnad

Följ null-värdena bokstavligt:

- `model_calls[]` har fälten `phase`, `round`, `provider_response_id`,
  `model_version`, `token_usage` och `latency_ms`. `phase` är `collect` eller
  `synthesize`.
- `model_calls[].provider_response_id` kan vara `null`.
- `model_calls[].model_version` kan vara `null` när providern inte rapporterar
  den.
- `model_calls[].token_usage` kan vara `null`.
- aggregerad `token_usage` kan vara `null`, och dess enskilda delvärden kan
  också vara `null` när rapporteringen inte är komplett. De åtta delvärdena är
  `input_tokens`, `cached_input_tokens`, `uncached_input_tokens`,
  `candidate_output_tokens`, `thinking_output_tokens`, `output_tokens`,
  `tool_use_prompt_tokens` och `total_tokens`.
- `estimated_cost_usd` kan vara `null`. `model_cost_breakdown` kan också vara
  `null`; när objektet finns är dess required-but-nullable fält
  `uncached_input_usd`, `cached_input_usd`, `output_usd` och
  `observed_cache_savings_usd`.
- `prompt_cache.cached_input_tokens` är `null` när ingen model call rapporterade
  cached-tokenfältet.

`prompt_cache` finns alltid och har `strategy`,
`provider_reported_model_calls`, `model_call_count`, `cached_input_tokens` och
`cache_hit_observed`. `estimated_cost_basis` finns också alltid och ska visas
intill kostnaden: beloppet är ett listprisestimat, inte en providerfaktura eller
ett påstående om faktisk debitering.

Kostnaden gäller endast Gemini-modellens generation i den aktuella körningen
och antar paid Standard-listpris. Den inkluderar inte Cloud Run, PostgreSQL,
nätverk eller embeddinganrop. Gemini-responsen rapporterar tokenantal men inget
faktiskt debiterat USD-belopp; free tier, credits eller en annan service tier
kan därför göra den verkliga kostnaden annorlunda. UI-texten ska vara
**Estimated paid list price**, aldrig **Actual cost**.

Live-svarets `prompt_cache.strategy = provider_implicit` betyder endast att
backend läser providertelemetri. I capability-svaret är
`prompt_cache.explicit_caching_enabled = false`. Visa en cache hit endast när
`cache_hit_observed = true` och `cached_input_tokens` är större än noll. När
`cached_input_tokens = null`, visa **Not reported** — aldrig noll, cache miss
eller en beräknad besparing. Ett uttryckligen provider-rapporterat nollvärde kan
beskrivas som “No provider-reported cache hit”, inte som bevis på att all
implicit caching misslyckades.

## Proof-kontrakt

### Retrieval

`GET /api/v1/proof/evals/retrieval` är en historisk snapshot med egen
`provenance.git_sha` och `executed_at`. Den mätte
`pgvector_exact_cosine`, `gemini-embedding-2`, 768 dimensioner och Hit@4 på
development och held-out. Providerusage och kostnad är nullable; null betyder
att metadatan inte var komplett nog för ett sant kostnadspåstående.

`safety_boundary.adversarial_synthesis_safety_evaluated = false` måste synas om
frontend presenterar säkerhetsbevis. Retrievaltestet observerade hämtning; det
bevisade inte att en modell hanterade det hämtade innehållet säkert.

Diagnoskvalitet visas per aktuell replay/live-körning genom de separata
verifieringsfälten för schema, citationer, evidensstöd, claim coverage och
korrekt diagnos. Det finns ingen publik batch-accuracy eller diagnosis-proof-
endpoint. Frontend får därför inte konstruera en aggregerad “AI score”.

## Fel och retry-policy

Branching ska använda `code`, inte den mänskliga `title` eller `detail`.

| HTTP | Code | Frontendbeteende |
|---:|---|---|
| 400 | `LIVE_AI_CONFIRMATION_REQUIRED` | Kräv ett nytt uttryckligt användarval. |
| 400 | `INVALID_REQUEST_BODY` | Visa valideringsfel; skicka bara förväntade fält. |
| 404 | `SCENARIO_NOT_FOUND` | Uppdatera scenariolistan eller låt användaren välja om. |
| 404 | `ROUTE_NOT_FOUND` | Klientens path finns inte i aktuell backendversion; synkronisera klientkontraktet mot aktuell OpenAPI. |
| 405 | `METHOD_NOT_ALLOWED` | Använd metoden som OpenAPI beskriver för pathen. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Skicka live-body som JSON. |
| 429 | `LIVE_AI_RATE_LIMITED` | En annan livekörning pågår eller fem starter har nåtts inom det rullande tiominutersfönstret. Respektera `Retry-After`, men starta inte om automatiskt. |
| 429 | `LIVE_AI_DAILY_LIMIT_REACHED` | Den konfigurerade livebudgeten på 20 starter per UTC-dygn är slut. `Retry-After` anger sekunder till nästa UTC-dygn; `daily_quota_scope` avgör om räknaren är processlokal eller databasgemensam. |
| 429 | `MODEL_PROVIDER_RATE_LIMITED` | Provider rate limit; inget pålitligt `Retry-After` utlovas. |
| 502 | `MODEL_PROVIDER_ERROR` / `INVALID_MODEL_TOOL_ARGUMENTS` | Visa sanerat livefel. |
| 502 | `MALFORMED_MODEL_RESPONSE` | Visa sanerat livefel för endpoints som saknar ett komplett post-run-kvitto. Ett färdigkört ADK-turn där bara slutligt `Diagnosis`-kontrakt underkänns returnerar i stället HTTP 200 `verification_failed` med modelltexten dold. |
| 502 | `RAG_EMBEDDING_PROVIDER_ERROR` / `RAG_EMBEDDING_RESPONSE_INVALID` | Visa sanerat retrievalfel; ingen automatisk fallback. |
| 503 | `LIVE_AI_DISABLED` / `LIVE_AI_NOT_CONFIGURED` | Inaktivera eller förklara live utan att påverka replay. |
| 503 | `RAG_EMBEDDING_NOT_CONFIGURED` / `RAG_INDEX_NOT_READY` / `RAG_DATABASE_UNAVAILABLE` | Förklara att aktiv RAG-backend inte kan genomföra retrieval. Märk inte om körningen till fixture-RAG. |
| 504 | `MODEL_PROVIDER_TIMEOUT` / `LIVE_INVESTIGATION_TIMEOUT` | Visa timeout som ett livefel. |

Generated-, catalog- och ADK-live delar samma livegränser:

- högst **en** pågående liveutredning;
- högst **fem** starter per rullande tio minuter per applikationsinstans;
- högst **20** live-starter per UTC-dygn enligt
  `live_ai.budget.daily_live_run_limit`; kontrollera `daily_quota_scope` innan
  taket beskrivs som databasgemensamt.

Concurrent- och rolling-avslag använder båda `LIVE_AI_RATE_LIMITED` och
returnerar `Retry-After`; användaren kan få den sanerade `detail`-texten men
frontendlogiken ska fortsätta brancha på `code`. Daily-avslag använder det
separata `LIVE_AI_DAILY_LIMIT_REACHED`. Provideravslag använder
`MODEL_PROVIDER_RATE_LIMITED` och utlovar inte `Retry-After`.

Gör ingen automatisk retry av live-AI, inte heller efter `429`, `502`, `503`
eller `504`. En ny livekörning kan kosta pengar och kräver därför ett nytt
medvetet användarval. Vanliga idempotenta `GET`-anrop kan använda normal,
begränsad query-retry om frontendramverket behöver det.

## Definition of done för frontend-integrationen

- Typerna speglar aktuell `/v3/api-docs`; reproducerbar automatisk generering
  återstår att verifiera.
- Driftläget använder `POST /api/v1/agent/turns` och kräver
  `contract_version = nordly-adk-turn-v3`.
- Workflow-vyn visar endast `workflow`, `events`, `tool_events`,
  `verification_event` och `receipt` från samma avslutade backendrequest.
- Ett ADK-svar presenteras bara när `verification_event.answer_released = true`;
  frontend återskapar aldrig Java-grindens beslut.
- Frontend använder endast `POST /api/v1/generated-cases/runs/live-ai` för
  Generated Synthetic Case; den försöker inte skapa, polla, ladda upp eller
  återhämta ett persisterat case.
- Generatorvalen kommer från `capabilities-v4.generated_cases` och requesten
  skickar endast `seed`, `incident_family`, `evidence_mode`, `noise_level` och
  `confirm_live_ai`.
- Replay fungerar utan livekonfiguration.
- Live kräver uttrycklig bekräftelse och faller aldrig tyst tillbaka till replay.
- Truth labels kommer från API-svaret.
- `null` visas som okänt/Not reported, inte som noll.
- Tool-animationen återspelar faktiska `tool_events`; den hittar inte på
  streaming, chain-of-thought eller ett RAG-steg som saknas i körningen.
- Claims och evidens korslänkas med `evidence_id`, och verifieringsresultaten
  visas separat från diagnosen.
- Det råa `GroundTruth`-objektet efterfrågas eller exponeras aldrig;
  frontend använder endast post-run `verification` och `comparison`.
- Kostnad märks som listprisestimat, visar `estimated_cost_basis` och behandlar
  cache-/token-null som **Not reported**.
- Aktiv retrieval visas från capabilities eller aktuell tool-metadata.
- Retrieval-proof märks som historisk och blandas inte ihop med aktuell runtime.
- Ingen klient försöker starta eval, embeddingimport eller remediation.
- Inga credentials, API-nycklar eller råa providerfel exponeras.
