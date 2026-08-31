# Incident Detective

Incident Detective är en Applied AI-demo i Java och Spring Boot. Demon visar
hur en LLM kan placeras i ett vanligt mjukvarusystem
utan att modellen får obegränsad kontroll eller automatiskt betraktas som
korrekt.

> **Kärnan:** Gemini väljer begränsade read-only functions, den aktiva
> retrieval-backenden kan hämta relevant runbook-evidens och vanlig Java
> verifierar om det strukturerade svaret stöds av evidensen modellen faktiskt
> såg.

All incidentdata är syntetisk. Systemet gör aldrig rollback, deploy eller annan
remediation.

## Vad projektet bevisar

| Område | Konkret implementation |
|---|---|
| LLM | Ett opt-in liveflöde använder Gemini. Recorded replay är ett separat, providerfritt demoläge och märks aldrig som live. |
| Function calling | Gemini får välja mellan `get_metrics`, `search_logs`, `get_trace` och `retrieve_runbooks`. Alla tools är typade, scenarioavgränsade och read-only. |
| Bounded orchestration | Flödet är `COLLECT → SYNTHESIZE → VERIFY`, med hårda gränser för rundor, tool calls, model calls och tid. Synthesis får inga tools. |
| Structured output | Diagnosen måste följa ett strikt JSON-schema och valideras som Java-typer. |
| RAG och embeddings | Endast runbooks bäddas in med Gemini embeddings och lagras i PostgreSQL/pgvector. Metrics, logs och traces hålls bakom typade tools. |
| Eval | Varje körning graderas deterministiskt för schema, citationer, evidensstöd, claim coverage och korrekt diagnos. En liten opt-in RAG-eval mäter riktig embedding- och pgvectorretrieval i testspåret. |
| Observability | Resultatet innehåller latency, model/tool calls, nullable tokenusage, cacheobservation och listprisestimat. Sanerade fel och Micrometer-mått finns. |

Projektet innehåller medvetet **inte** ett generellt agentramverk eller en stor
benchmarkplattform. Den tidigare offline diagnosis-evalmotorn togs bort eftersom
den gjorde projektet svårare att förstå utan att förbättra själva demon.

## Så fungerar en livekörning

1. Klienten väljer ett katalogscenario eller genererar ett request-lokalt
   Payment Timeout-fall och bekräftar live-AI.
2. Gemini får scenariot, aktuell tool-budget och en allowlist med tillåtna tools.
3. Backend kör modellens function calls och samlar endast tool-returnerad evidens.
4. Runbooktoolen använder den aktiva retrieval-backenden: fixture i standardläge
   eller Gemini embeddings och pgvector i `rag`-profilen.
5. Gemini gör en separat tool-fri synthesis till ett strikt diagnosschema.
6. Java verifierar svaret mot sedd evidens och öppnar dolt `GroundTruth` först
   efter sista modellanropet.
7. API:t returnerar tool events, evidens, diagnos, separata verifieringsmått och
   körmetadata till frontend.

Detta är en observerbar evidence chain, inte modellens privata chain-of-thought.

## API

Backend har sex publika paths:

| Metod | Path | Syfte |
|---|---|---|
| `GET` | `/api/v1/capabilities` | Aktiv modell-, retrieval-, cache- och budgetkonfiguration utan credentials. |
| `GET` | `/api/v1/scenarios` | Säkra scenariosammanfattningar utan facit eller evidensinventarium. |
| `POST` | `/api/v1/scenarios/{scenarioId}/runs/recorded-replay` | Stabil providerfri referenskörning. |
| `POST` | `/api/v1/scenarios/{scenarioId}/runs/live-ai` | Explicit bekräftad Gemini-utredning. |
| `POST` | `/api/v1/generated-cases/runs/live-ai` | Genererar och utreder ett reproducerbart syntetiskt fall i samma request. Ingen logguppladdning eller persistens. |
| `GET` | `/api/v1/proof/evals/retrieval` | Fryst historisk RAG-eval, utan möjlighet att starta en eval. |

Swagger finns lokalt på `http://localhost:8080/swagger-ui.html` och OpenAPI på
`http://localhost:8080/v3/api-docs`.

Frontend ska generera typer från aktuell OpenAPI. Se
[frontendkontraktet](./docs/FRONTEND-API-HANDOFF.md) och
[API-genomgången](./docs/API-WALKTHROUGH.md).

## Kör lokalt

Providerfri backend och replay:

```bash
./mvnw spring-boot:run
```

Vanliga verifieringar:

```bash
./mvnw test
./mvnw -Pdatabase-it verify
```

`database-it` använder Testcontainers för PostgreSQL/pgvector. Providerbaserade
smokes är opt-in och hoppas över i den vanliga sviten.

## RAG-profil

Starta lokal pgvector och importera den versionshanterade korpusen explicit:

```bash
docker compose up -d
./mvnw -q spring-boot:run -Dspring-boot.run.arguments=--import-runbooks
```

Starta sedan backend med RAG och uttryckligen aktiverad live-AI:

```bash
SPRING_PROFILES_ACTIVE=rag \
INCIDENT_DETECTIVE_LIVE_AI_ENABLED=true \
./mvnw spring-boot:run
```

Importen är idempotent och vanlig applikationsstart gör inga embedding-anrop.
RAG-profilen vägrar retrieval om indexets antal eller innehållshash inte matchar
den aktuella korpusen.

### Liten verklig RAG-eval

Den versionerade sviten innehåller positiva development-/held-out-frågor och
no-match-fall. Den kör riktiga Gemini embeddings mot en tillfällig pgvector-
databas, men endast efter explicit opt-in:

```bash
./mvnw -Pdatabase-it \
  -Drun.rag.eval=true \
  -Dit.test=RunbookRetrievalEvalIT \
  verify
```

Det publicerade historiska resultatet är development 5/5 och held-out 4/5
Hit@4, med 3/3 no-match. Det missade held-out-fallet är kvar som failure case.
Det bevisar retrieval på en liten syntetisk korpus — inte storskalig vector
search eller full systemsäkerhet.

## Live Gemini

Live kräver en ignorerad lokal Gemini-nyckel, serverflaggan
`INCIDENT_DETECTIVE_LIVE_AI_ENABLED=true` och `confirm_live_ai: true` i varje
request. Ett livefel ersätts aldrig tyst av replay.

Det separata opt-in-smoketestet kör en verklig utredning:

```bash
INCIDENT_DETECTIVE_LIVE_AI_ENABLED=true \
./mvnw -Pdatabase-it \
  -Drun.gemini.smoke=true \
  -Dit.test=GeminiLiveSmokeIT \
  verify
```

En smoke visar att ett flöde kan fungera. Den är inte accuracy, p95 eller ett
stabilitetsbevis.

## Viktiga sanningsgränser

- Replay: `Simulated incident — recorded deterministic replay.`
- Live: `Simulated incident — real AI investigation.`
- I replay betyder `null` för modell/usage/kostnad att ingen modell kördes. I
  live- och providertelemetri betyder `null` **Not reported** eller att ett
  tillförlitligt estimat saknas, aldrig noll.
- Provider implicit cachetelemetri betyder inte att explicit prompt caching är
  implementerat eller att en cache hit observerades.
- Den frysta retrievalrapporten beskriver en historisk körning. Capabilities och
  aktuella tool events beskriver den process som kör nu.
- Lokal concurrency/rate limiting är per instans och är inte distribuerat
  missbruksskydd.
- Capabilities visar om dygnstaket är `process_local` eller
  `database_global`; endast det senare är beständigt och delat mellan
  instanser.
- Projektet är en syntetisk portfolio-/utbildningsdemo, inte production incident
  response.

## Vad frontenden ska göra tydligt

Huvudresan är:

`Scenario → körläge → tool/evidence trace → verifierad diagnos`

Tekniska fördjupningar visar RAG/retrieval samt runtime/tokens/cache/fel. UI:t
ska skilja mellan **Current run**, **Current backend** och **Historical eval**.
Det ska aldrig skapa egna modellnamn, budgetar, confidence scores eller
chain-of-thought.

## Möjliga senare utbyggnader

- Fler incidentfamiljer endast om de förbättrar demon; generatorn stödjer redan
  både diagnostic och `insufficient_evidence`.
- Explicit prompt caching först efter mätning av återanvändbar promptstorlek och
  faktisk kostnadsnytta.
- Persistenta/async runs, auth och distribuerad rate limiting inför publik
  flerinstansdrift.
- Större eller approximate vector search först när korpusstorleken kräver det.
- En liten framtida model-quality eval i test/CI, aldrig som ett ramverk i
  webbappens runtime.

Ingen deploy eller extern publicering sker automatiskt från dessa kommandon.
