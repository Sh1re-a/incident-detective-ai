# AI-systemkort och riskregister

- **System:** Incident Detective
- **Ägare:** Shirwac Abib
- **Status:** Portfolio- och utbildningsprojekt under aktiv utveckling
- **Datagräns:** Endast syntetisk incidentdata
- **Senast uppdaterad:** 26 augusti 2026

## Syfte och avsedd användning

Incident Detective visar hur ett avgränsat AI-system kan undersöka en syntetisk mjukvaruincident. Modellen får samla evidens genom fyra typade read-only tools, lämna en strukturerad diagnos och rekommendera ett säkert nästa steg. Resultatet verifieras sedan med vanlig Java-kod mot ett dolt syntetiskt facit.

Systemet är byggt för demonstration, lärande och reproducerbar utvärdering. Det är inte anslutet till riktiga företagsmiljöer och är inte ett produktionssystem för incidenthantering.

## Systemet får inte

- läsa riktiga kund- eller företagsloggar,
- genomföra rollback, deploy eller annan remediation,
- fatta ett operativt beslut utan en människa,
- beskriva en replay som live-AI,
- användas som bevis för kvalitet utanför den publicerade evalmängden,
- beskrivas som NIST-, OWASP- eller företagscompliant.

## Begränsad AI-agency

Modellen kan endast välja mellan `get_metrics`, `search_logs`, `get_trace` och `retrieve_runbooks`. Verktygen är skrivskyddade. State machinen begränsar collection-rundor, modellanrop, tool calls och tid. Den sista synthesize-rundan saknar tools och följs endast av deterministisk verifiering. Modellen kan svara `insufficient_evidence`. Ett föreslaget nästa steg kräver alltid mänskligt godkännande.

## Verifierad status just nu

| Område | Status | Evidens |
|---|---|---|
| Syntetisk datagräns | Verifierat | Scenariofixtures, evidens och runbooks är skapade för projektet; inga riktiga företagsloggar används. |
| Begränsad agency | Verifierat | Fyra read-only tools, bounded state machine och ingen remediationväg. |
| Structured output | Verifierat | Java-validering och deterministisk verifiering hanterar schema, citationer, evidensstöd och facit separat. |
| Runbook-RAG | Byggt och delvis verifierat | PostgreSQL/pgvector, 10 dokument/12 chunks, Gemini embeddings, hash-readiness och explicit import fungerar lokalt. |
| Retrieval-kvalitet | Mätt, förbättring krävs | Development Hit@4 5/5; held-out 4/5; no-match 3/3. Unsafe legacy-runbook var top-1 i det missade held-out-fallet. |
| Prompt-injection-säkerhet | Inte verifierat | Den osäkra runbooken hämtades rank 1 i adversarial-fallet. Ett separat synthesis-test återstår. |
| Full diagnos-eval | Inte verifierat | 18-falls-harnessen, baseline och korrekta abstentioner återstår. Enskilda live-smokes är inte accuracy. |
| Observability | Inte byggt | Run metadata finns, men strukturerade JSON-loggar och OpenTelemetry-spans återstår. |
| Deployment | Inte verifierat | GitHub och lokal demo finns; ingen Cloud Run-version är deployad. |

## Riskregister

| ID | Risk | Nuvarande skydd | Nästa verifiering |
|---|---|---|---|
| R-01 | Modellen ger en felaktig eller dåligt stödd diagnos | Structured output, evidence IDs, separat citation/support/correctness och `insufficient_evidence` | 18 fulla evalfall med held-out-data |
| R-02 | En runbook innehåller indirekt prompt injection | Runbooks behandlas som data, tools är read-only och modellen saknar åtgärdsbehörighet | Tvinga in den redan hämtade adversarial chunken i synthesis och kontrollera output/approval |
| R-03 | Retrieval returnerar relevant-looking men fel text | Development-only tröskel, exact cosine, rank/similarity/hash och no-match-test | Förbättra corpus/query-kontrakt i en ny benchmarkversion utan held-out-tuning |
| R-04 | Indexet är gammalt eller ofullständigt | Retrieval kontrollerar både antal och innehållshash före query-embedding | Behåll stale/missing-index-tester i CI |
| R-05 | Modellen använder för många eller okända verktyg | Dynamisk tillåtelselista från återstående budget, atomisk preflight, per-tool-gränser, totalbudget och hard timeout | Behåll negativa kontraktstester i CI |
| R-06 | Schema, citation eller facit underkänns men presenteras som korrekt | Deterministisk verifiering och separat `verification_failed` | Mäta hela evalmängden och visa failure cases |
| R-07 | Publik användning orsakar kostnad eller överbelastning | Replay som standard, explicit livebekräftelse, lokal concurrency/rate limit och timeout | Cloud Run max-instances, persistent global gräns och budgetlarm före deploy |
| R-08 | Hemligheter eller onödiga data hamnar i telemetry | Nyckelfilen ignoreras av Git; publika payloads utesluter `GroundTruth` | Implementera och testa en allowlist för JSON-loggar/OpenTelemetry |
| R-09 | Leverantören är långsam eller otillgänglig | Sanerade providerfel, kontrollerad timeout, mätt låg-latensstandard och ingen tyst replay | Mäta stabilitet; historiken innehåller timeout även om de två senaste RAG-smokesen slutfördes |
| R-10 | Evalresultat överanpassas | Development och held-out hålls isär; tröskeln fryses före held-out | Versionshantera framtida dataset och ändra aldrig v1 efter resultatet |

## Säkra felutfall

- `insufficient_evidence` är ett giltigt avstående, inte ett tekniskt fel.
- `verification_failed` betyder att en strukturerad diagnos underkändes efter modellen.
- Providerfel och timeout visas som explicita fel. Systemet märker aldrig en replay som liveutredning.
- Rate limit returnerar ett tydligt svar och klienten gör inga automatiska live-retries.
- Inget felutfall genomför eller påstår att remediation har utförts.

## Evals och ändringskontroll

En smoke visar att ett flöde kan fungera i ett enskilt fall. Den visar inte accuracy, stabilitet eller p95.

Varje evalrapport ska identifiera dataset, korpus, embeddingprofil, modell/prompt när de används, schema, git SHA och tidpunkt. När prompt, schema, korpus, chunkning, embeddingkonfiguration, modell eller scorer ändras behandlas tidigare resultat som historiska tills berörd evalsvit har körts igen. Om dataset eller scorer ändras blir det en ny benchmarkversion, inte en direkt jämförbar modellförbättring.

## Observability och dataminimering

Planerade spans följer `investigation → collect → tool/retrieval → synthesize → verify`. En framtida allowlist får innehålla run ID, scenario ID, fas, toolnamn, evidence IDs, antal, durationer, versionsmetadata och sanerad felkategori. Den får inte innehålla API-nycklar, rå providerrespons, dolt `GroundTruth`, fulla råprompter, full evidenstext, osanerade tool arguments eller privat chain-of-thought.

OpenTelemetry är ännu planerat och får inte beskrivas som implementerat innan spans och sanering har testats.

## Ramverk som referens, inte certifiering

Riskarbetet är inspirerat av [NIST AI Risk Management Framework](https://www.nist.gov/itl/ai-risk-management-framework), [NIST:s GenAI-profil](https://nvlpubs.nist.gov/nistpubs/ai/NIST.AI.600-1.pdf) och OWASP:s vägledning om [prompt injection](https://genai.owasp.org/llmrisk/llm01-prompt-injection/), [excessive agency](https://genai.owasp.org/llmrisk/llm062025-excessive-agency/), [improper output handling](https://genai.owasp.org/llmrisk/llm052025-improper-output-handling/) och [unbounded consumption](https://genai.owasp.org/llmrisk/llm102025-unbounded-consumption/). Projektet har inte genomgått en compliancegranskning och gör inget påstående om certifiering eller full ramverkstäckning.
