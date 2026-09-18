# AI-systemkort och riskregister

- **System:** Incident Detective
- **Ägare:** Shirwac Abib
- **Status:** Portfolio- och utbildningsprojekt under aktiv utveckling
- **Datagräns:** Endast syntetisk incidentdata
- **Senast uppdaterad:** 14 september 2026

## Syfte och avsedd användning

Incident Detective visar hur ett avgränsat AI-system kan undersöka en syntetisk mjukvaruincident. Java 21 och Spring Boot äger API, säkerhetsgräns, budgetar och slutlig verifiering. Google ADK for Java kör ett `SequentialAgent` med två barn i fast ordning: en evidensagent med ett sammansatt read-only tool och en tool-fri diagnosagent. En diagnos får släppas först när vanlig Java-kod har verifierat ADK-spåret, svarets struktur, dess direkta evidensstöd och dess faktamatchning mot det request-lokala syntetiska facit. Publik text projiceras därefter av Java; rå modellprosa och det privata facitobjektet lämnar inte backend.

Systemet är byggt för demonstration, lärande och reproducerbar utvärdering. Det är inte anslutet till riktiga företagsmiljöer och är inte ett produktionssystem för incidenthantering.

## Systemet får inte

- läsa riktiga kund- eller företagsloggar,
- genomföra rollback, deploy eller annan remediation,
- fatta ett operativt beslut utan en människa,
- beskriva en replay som live-AI,
- användas som bevis för kvalitet utanför den publicerade evalmängden,
- beskrivas som NIST-, OWASP- eller företagscompliant.

## Begränsad AI-agency

ADK-flödet har två modellsteg och en hård gräns på två modellanrop. `nordly_evidence_agent` kan anropa `inspect_incident_evidence` exakt en gång. Java delar då upp anropet i avgränsade läsningar av metrics, loggar, traces och eventuellt runbooks genom RAG. Den strukturerade `FunctionResponse` som ADK registrerar lämnas direkt vidare till `nordly_diagnosis_agent`, som saknar tools. Agenterna får inte byta ordning, delegera, skriva eller genomföra remediation. Java kontrollerar agentordning, evidensöverlämning, tool-gräns, slutlig författare, schema, citationer och faktastöd innan svaret släpps. Modellen kan svara `insufficient_evidence`, och ett föreslaget nästa steg kräver alltid mänskligt godkännande.

## Verifierad status just nu

| Område | Status | Evidens |
|---|---|---|
| Syntetisk datagräns | Verifierat | Scenariofixtures, evidens och runbooks är skapade för projektet; inga riktiga företagsloggar används. |
| Begränsad agency | Verifierad lokalt i aktuell revision | Google ADK Java `SequentialAgent`, två namngivna barn, två modellanrop som hard cap, ett sammansatt read-only tool hos evidensagenten och noll tools hos diagnosagenten. Slutlig fullsvit: 480 unit/API/contract-tester och 6 databas-integrationstester passerade; 2 uttryckliga kostnadsbärande tester hoppades över. |
| Workflow-kvitto | Implementerat i aktuell arbetsrevision | Kontrakt `nordly-adk-turn-v4` redovisar förväntad/observerad agentordning, direkt ADK `FunctionResponse`-överlämning, slutlig författare, nullable faktisk provider-route och Java-verifieringens separata beslut. Publika events och svar innehåller ingen rå modellprosa eller privat facitjämförelse. |
| Primär driftagentintegration | Implementerad och verifierad lokalt | `incident-lab-plan-v1` följs av `incident-lab-run-v3` med nested `nordly-adk-turn-v4`. Båda är synkrona; frontend får endast spela upp det färdiga backendkvittot och inga fabricerade progress-events. |
| Structured output | Verifierat | Java-validering och deterministisk verifiering hanterar schema, citationer, evidensstöd och facit separat. |
| Runbook-RAG | Byggt och delvis verifierat | PostgreSQL/pgvector, 10 dokument/12 chunks, Gemini embeddings, hash-readiness och explicit import fungerar lokalt. |
| Nordly företags-RAG | Struktur och gränser verifierade lokalt | 16 syntetiska dokument/30 chunks; 13 dokument/27 chunks är godkända för publik RAG. Restricted, deprecated och untrusted material filtreras före embedding. `nordly-knowledge-rag-v3` verifierar schema, citation membership, åtkomstscope och utdataregler, men utvärderar inte semantisk entailment. |
| Provider- och deployproveniens | Implementerad lokalt, inte molnverifierad | Gemensam Developer API/Vertex-klientgräns, transportseparerat vektorindex samt capabilities/RAG/ADK-kvitton utan project-id eller credentials. Inget Vertex-anrop eller ny deploy har gjorts. |
| Retrieval-kvalitet | Mätt, förbättring krävs | Development Hit@4 5/5; held-out 4/5; no-match 3/3. Unsafe legacy-runbook var top-1 i det missade held-out-fallet. |
| Prompt-injection-säkerhet | Delvis verifierad | Pre-AI-grinden blockerar testade adversariala instruktioner innan modell, embeddings och tools. Retrieval-evalen visade samtidigt en osäker legacy-runbook som top-1 i ett missat held-out-fall; semantisk synthesis-/entailmenttäckning återstår. |
| Diagnoskvalitet | Verifierad per körning, inte aggregerad | Schema, citationer, stöd, coverage och correctness returneras per replay/live-run. Full modellaccuracy är inte mätt. |
| Observability | Avgränsad OpenTelemetry-slice byggd och testad | Liveflödet har sanerade spans för `investigation → collect → tool/retrieval → synthesize → verify`. Lokal standard är no-op och OTLP span-export är separat opt-in. Strukturerade JSON-loggar, collector/dashboard och exporterad end-to-end-trace är inte verifierade. |
| Deployment | Aktuell revision inte deployad i denna uppgift | Den härdade backendrevisionen är verifierad lokalt. Frontend är ännu inte migrerad till slutkontrakten och den kombinerade slutcontainern är inte verifierad. En äldre publik Cloud Run-revision kan finnas, men den bevisar inte att aktuell kod är live. Den aktiva lokala providervägen är Gemini Developer API; Cloud Run, Vertex AI, IAM/ADC, Cloud SQL och publik trafik är separata nästa beslut. |

## Riskregister

| ID | Risk | Nuvarande skydd | Nästa verifiering |
|---|---|---|---|
| R-01 | Modellen ger en felaktig, ofullständig eller dåligt stödd diagnos | Structured output, evidence IDs, separat citation/support/coverage/correctness och `insufficient_evidence` | Kör små opt-in live-smokes och lägg bara till held-out-fall när ett konkret kvalitetsbeslut kräver dem |
| R-02 | En runbook innehåller indirekt prompt injection | Runbooks behandlas som data, tools är read-only och modellen saknar åtgärdsbehörighet | Tvinga in den redan hämtade adversarial chunken i synthesis och kontrollera output/approval |
| R-03 | Retrieval returnerar relevant-looking men fel text | Development-only tröskel, exact cosine, rank/similarity/hash och no-match-test | Förbättra corpus/query-kontrakt i en ny benchmarkversion utan held-out-tuning |
| R-04 | Indexet är gammalt eller ofullständigt | Retrieval kontrollerar både antal och innehållshash före query-embedding | Behåll stale/missing-index-tester i CI |
| R-05 | Fel agent använder tools, agenterna körs i fel ordning eller budgeten överskrids | Fast `SequentialAgent`-ordning, två modellanrop som hard cap, exakt ett tillåtet tool hos evidensagenten, tool-fri diagnosagent och Java-kontroll av det observerade ADK-spåret | Behåll trajectory-tester och fullsvit som ändringsgrind |
| R-06 | Schema, citation, evidensöverlämning eller facit underkänns men presenteras som korrekt | Deterministisk Java-verifierare är enda release gate; ett underkänt hårt villkor ger `verification_failed` och diagnosen hålls inne | Behåll negativa kontraktstester och visa ett verkligt failure case |
| R-07 | Publik användning orsakar kostnad eller överbelastning | Replay som standard, explicit livebekräftelse, lokal concurrency/rolling rate limit, timeout och ett dagstak vars scope exponeras; `rag` använder en atomisk PostgreSQL-räknare | Cloud Run max-instances och providerbudgetlarm före deploy |
| R-08 | Hemligheter eller onödiga data hamnar i telemetry | Nyckelfilen ignoreras av Git; publika payloads utesluter `GroundTruth`; OpenTelemetry-spans använder en testad attribut-allowlist utan generic attribute-API | Bygg samma allowlistprincip för framtida strukturerade JSON-loggar och granska exporterad telemetry innan en extern collector ansluts |
| R-09 | Leverantören är långsam eller otillgänglig | Sanerade providerfel, kontrollerad timeout och ingen tyst replay | Mäta stabilitet; härdningsrundan gav ett planner-anrop med HTTP 502 utan retry och en separat HTTP 200-körning där Java höll inne diagnosen. Det bevisar felhantering, inte providerstabilitet. |
| R-10 | Evalresultat överanpassas | Development och held-out hålls isär; tröskeln fryses före held-out | Versionshantera framtida dataset och ändra aldrig v1 efter resultatet |

## Säkra felutfall

- `insufficient_evidence` är ett giltigt avstående, inte ett tekniskt fel.
- `verification_failed` betyder att Java höll inne modellens slutsats. Det kan
  ske direkt när `Diagnosis`-kontraktet brister eller senare när schema,
  citationer, evidensstöd eller syntetiskt facit underkänns. Ett komplett
  ADK post-run-kvitto behålls, men rå modelltext släpps aldrig.
- `citations_valid` bevisar bara att citerade ID:n fanns i det lästa paketet.
  `direct_evidence_support_valid` redovisar separat om varje citerad källa
  faktiskt stödde påståendet. En riktig körning får stoppas även när modellen
  gissade rätt slutsats men använde en otillräcklig beviskedja.
- `blocked_before_ai` betyder att Java stoppade input före ADK Runner, Gemini, tools och embeddings; kvittot ska då visa noll anrop och noll actions.
- Providerfel och timeout visas som explicita fel. Systemet märker aldrig en replay som liveutredning.
- Rate limit returnerar ett tydligt svar och klienten gör inga automatiska live-retries.
- Inget felutfall genomför eller påstår att remediation har utförts.
- Incident Lab returnerar ett färdigt synkront post-run-kvitto. En frontend får
  animera kvittot efteråt, men får inte framställa det som streamade live-events.

## Evals och ändringskontroll

En smoke visar att ett flöde kan fungera i ett enskilt fall. Den visar inte accuracy, stabilitet eller p95.

Varje evalrapport ska identifiera dataset, korpus, embeddingprofil, modell/prompt när de används, schema, git SHA och tidpunkt. När prompt, schema, korpus, chunkning, embeddingkonfiguration, modell eller scorer ändras behandlas tidigare resultat som historiska tills berörd evalsvit har körts igen. Om dataset eller scorer ändras blir det en ny benchmarkversion, inte en direkt jämförbar modellförbättring.

## Observability och dataminimering

Det implementerade ursprungliga liveflödet följer `investigation → collect → tool/retrieval → synthesize → verify`. ADK-endpointens `events[]`, `workflow` och `verification_event` är i stället ett sanerat post-run-kvitto från den aktuella requesten. Ingen av vyerna sparar eller visar privat chain-of-thought. Attribut kan endast sättas genom en fast kod-allowlist med serverkontrollerade ID:n, enums, booleska gränser, antal, durationer, tokenantal och aggregerade verifieringsutfall. Retrievalspanen kan visa om `pgvector_exact_cosine` faktiskt var aktivt samt matchantal, embeddingdimension och embeddinglatency. Den visar aldrig querytext, dokumentinnehåll eller evidence IDs.

I de manuella domänspannen saknar API-nycklar, råa prompts, providerrespons, dolt `GroundTruth`, full evidenstext, tool arguments, summaries och privat chain-of-thought helt attributväg. Dessa spans registrerar inte heller exception messages eller stack traces; endast en liten sanerad `error.type`-kategori används. Ett in-process-test verifierar både spanträdet och att hemliga teststrängar inte förekommer i domänspannens attribut eller events.

OpenTelemetry är no-op som lokal standard genom `INCIDENT_DETECTIVE_OTEL_ENABLED=false`, och OTLP span-export kräver dessutom ett separat `INCIDENT_DETECTIVE_OTEL_EXPORT_ENABLED=true`. OTLP-export för metrics och logs är avstängd; Micrometer är fortsatt källa för applikationsmetrics. Det finns ännu inget verifierat collector-/dashboardflöde, ingen strukturerad JSON-loggning och ingen observability-claim för replayflödet.

## Ramverk som referens, inte certifiering

Riskarbetet är inspirerat av [NIST AI Risk Management Framework](https://www.nist.gov/itl/ai-risk-management-framework), [NIST:s GenAI-profil](https://nvlpubs.nist.gov/nistpubs/ai/NIST.AI.600-1.pdf) och OWASP:s vägledning om [prompt injection](https://genai.owasp.org/llmrisk/llm01-prompt-injection/), [excessive agency](https://genai.owasp.org/llmrisk/llm062025-excessive-agency/), [improper output handling](https://genai.owasp.org/llmrisk/llm052025-improper-output-handling/) och [unbounded consumption](https://genai.owasp.org/llmrisk/llm102025-unbounded-consumption/). Projektet har inte genomgått en compliancegranskning och gör inget påstående om certifiering eller full ramverkstäckning.
