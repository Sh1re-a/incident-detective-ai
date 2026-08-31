# Backend-API: handoff till frontend

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

## Generera typer från OpenAPI

Generera TypeScript-typer eller klient från `GET /v3/api-docs`. Behandla den
genererade diffen som en kontraktskontroll när backend ändras. Undvik manuellt
skrivna kopior av Java-responserna: bland annat är flera modell-, usage- och
kostnadsfält avsiktligt nullable.

Gör detta som ett lokalt/build-time-steg, inte genom att hämta OpenAPI från en
separat browser-origin i den färdiga appen. Den valfria CORS-allowlisten gäller
avsiktligt bara `/api/v1/**`, inte Swagger eller `/v3/api-docs`.

## De sex produkt- och proof-endpointsen

| Metod | Path | Användning |
|---|---|---|
| `GET` | `/api/v1/capabilities` | Aktiv runtime-konfiguration, säkra gränser och vilka AI-funktioner backend faktiskt erbjuder. |
| `GET` | `/api/v1/scenarios` | Säkra scenariosammanfattningar utan evidensinventarium eller facit. |
| `POST` | `/api/v1/scenarios/{scenarioId}/runs/recorded-replay` | Gratis, deterministisk körning utan modellrequest. |
| `POST` | `/api/v1/scenarios/{scenarioId}/runs/live-ai` | Ny, uttryckligen bekräftad Gemini-körning med read-only tools. |
| `POST` | `/api/v1/generated-cases/runs/live-ai` | Genererar ett request-lokalt Payment Timeout-fall och utreder det synkront med Gemini och read-only tools. |
| `GET` | `/api/v1/proof/evals/retrieval` | Publicerad, historisk och aggregerad retrieval-eval. |

Det finns ingen HTTP-endpoint som startar en eval, importerar embeddings eller
skriver till databasen. Proof-endpointen är en read-only publicerad snapshot.

För Generated Synthetic Case finns exakt **ett** API-anrop:
`POST /api/v1/generated-cases/runs/live-ai`. Det finns inget separat create-anrop,
ingen polling/status-endpoint, ingen TTL, ingen upload och ingen endpoint för
att hämta ett genererat fall senare. Generator, AI-utredning och verifiering
sker inom samma synkrona request. Fallet är request-lokalt och persisteras inte.

## Rekommenderad laddningsordning

1. Hämta `capabilities` och `scenarios` parallellt när appen startar.
2. Kräv `contract_version = capabilities-v2` för den här integrationen och
   bygg generatorns val från `generated_cases`, inte från egna konstanter.
3. Visa recorded replay som stabilt standardläge.
4. Visa live-kontrollen utifrån `live_ai.request_configured`, men låt alltid
   serverns svar vara auktoritativt eftersom providerhälsa kan ändras efter
   laddningen.
5. Hämta retrieval-proof först när en RAG/Eval-vy behöver den, eller cacha den
   som ett vanligt read-only GET-svar i frontendens querylager.

`live_ai.enabled_by_configuration` och `live_ai.credentials_configured` visar
de två lokala förutsättningarna separat. `live_ai.request_configured = true`
betyder att båda är uppfyllda. Inget av fälten garanterar att providern är
nåbar eller frisk just nu.

## `GET /api/v1/capabilities`

Viktiga fält:

- `contract_version = capabilities-v2`: capability-kontraktet som innehåller
  stöd för Generated Synthetic Case.
- `synthetic_only`: är alltid `true` i den här demon.
- `remediation_enabled`: är alltid `false`.
- `modes`: truth label, model-backed-status och bekräftelsekrav per körläge.
- `tools`: de typade funktioner som finns; alla är read-only.
- `live_ai`: separat serveraktivering, credential-status, lokal
  request-konfiguration, aktiv modell/prompt, thinking level och backendens
  hårda call-/tidsbudgeter.
- `generated_cases`: generatorns kontraktsversion, version, truth label,
  tillåtna controls och data-/persistensgräns.
- `retrieval`: den retrieval-backend som är aktiv i just denna backendprocess.
- `prompt_cache`: faktisk cachepolicy, inte ett marknadsföringspåstående.

Visa inte egna hårdkodade budgetar eller modellnamn när samma värde finns här.
Endpointen returnerar aldrig API-nyckeln.

### Exakt `generated_cases`-capability i `capabilities-v2`

```json
{
  "enabled": true,
  "contract_version": "generated-live-run-v1",
  "generator_version": "payment-timeout-generator-v1",
  "truth_label": "Generated synthetic incident — real AI investigation.",
  "user_supplied_data_accepted": false,
  "request_local_only": true,
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
bygga valen. `live_ai.request_configured = true` betyder bara att backendens
lokala förutsättningar finns; det garanterar inte providerhälsa. Fältet
`live_ai.budget.daily_live_run_limit = 20` visar taket och
`daily_quota_scope` visar om det är `process_local` eller
`database_global`. Standardprofilens processlokala räknare återställs vid
omstart; `rag`-profilens räknare är atomisk och delad via PostgreSQL. API:t
returnerar inte hur många körningar som återstår. Frontend får inte hitta på en
remaining-counter eller kalla `process_local` för ett globalt kostnadsskydd.

### Aktiv retrieval är inte samma sak som evalbevis

`capabilities.retrieval.backend` har två möjliga sanningar:

- `deterministic_fixture`: standard/replay-profilen använder lokal,
  deterministisk matching. `vector_database_backend_active` är `false` och
  `active_embedding_profile` är `null`.
- `pgvector_exact_cosine`: `rag`-profilen använder Gemini embeddings och exakt
  cosine-sökning i PostgreSQL/pgvector. `vector_database_backend_active` är `true` och
  `active_embedding_profile` är ifyllt.

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
  "evidence_mode": "diagnostic",
  "noise_level": "low",
  "confirm_live_ai": true
}
```

Alla fyra fält är obligatoriska. Backend avvisar även okända JSON-fält.

| Requestfält | Tillåtna värden | Faktisk betydelse |
|---|---|---|
| `seed` | JSON-heltal inom Java `Long` | Styr den deterministiska Java-generatorn. Samma seed och controls ger samma genererade scenario/signaler, men en ny `run_id` och inte nödvändigtvis identiskt Gemini-resultat. |
| `evidence_mode` | `diagnostic` | Innehåller den syntetiska evidens som krävs för att ställa en diagnos. |
| `evidence_mode` | `insufficient_evidence` | Utelämnar avsiktligt avgörande konfiguration/trace. En korrekt modellrespons ska avstå med `diagnosis.status = insufficient_evidence`. |
| `noise_level` | `none` | Lägger inte till generatorns distraktorsignal. |
| `noise_level` | `low` | Lägger till en begränsad, uttryckligen syntetisk och orelaterad inventory-varning. |
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
    generator_version: "payment-timeout-generator-v1"
    seed: int64
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
- `scenario_id` börjar i generator v1 med `generated-payment-timeout-`
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
det här flödet. De enda användarindata som accepteras är de fyra controls ovan.

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
| 404 | `ROUTE_NOT_FOUND` | Klientens path finns inte i aktuell backendversion; uppdatera den OpenAPI-genererade klienten. |
| 405 | `METHOD_NOT_ALLOWED` | Använd metoden som OpenAPI beskriver för pathen. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Skicka live-body som JSON. |
| 429 | `LIVE_AI_RATE_LIMITED` | En annan livekörning pågår eller fem starter har nåtts inom det rullande tiominutersfönstret. Respektera `Retry-After`, men starta inte om automatiskt. |
| 429 | `LIVE_AI_DAILY_LIMIT_REACHED` | Den konfigurerade livebudgeten på 20 starter per UTC-dygn är slut. `Retry-After` anger sekunder till nästa UTC-dygn; `daily_quota_scope` avgör om räknaren är processlokal eller databasgemensam. |
| 429 | `MODEL_PROVIDER_RATE_LIMITED` | Provider rate limit; inget pålitligt `Retry-After` utlovas. |
| 502 | `MODEL_PROVIDER_ERROR` / `MALFORMED_MODEL_RESPONSE` / `INVALID_MODEL_TOOL_ARGUMENTS` | Visa sanerat livefel. |
| 502 | `RAG_EMBEDDING_PROVIDER_ERROR` / `RAG_EMBEDDING_RESPONSE_INVALID` | Visa sanerat retrievalfel; ingen automatisk fallback. |
| 503 | `LIVE_AI_DISABLED` / `LIVE_AI_NOT_CONFIGURED` | Inaktivera eller förklara live utan att påverka replay. |
| 503 | `RAG_EMBEDDING_NOT_CONFIGURED` / `RAG_INDEX_NOT_READY` / `RAG_DATABASE_UNAVAILABLE` | Förklara att aktiv RAG-backend inte kan genomföra retrieval. Märk inte om körningen till fixture-RAG. |
| 504 | `MODEL_PROVIDER_TIMEOUT` / `LIVE_INVESTIGATION_TIMEOUT` | Visa timeout som ett livefel. |

Generated- och catalog-live delar samma livegränser:

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

- Typerna kommer från aktuell `/v3/api-docs`.
- Frontend använder endast `POST /api/v1/generated-cases/runs/live-ai` för
  Generated Synthetic Case; den försöker inte skapa, polla, ladda upp eller
  återhämta ett persisterat case.
- Generatorvalen kommer från `capabilities-v2.generated_cases` och requesten
  skickar endast `seed`, `evidence_mode`, `noise_level` och
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
