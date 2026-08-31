# API- och presentationsguide

- **Mål:** Jag ska kunna prova API:t i Swagger och förklara varför varje del finns.
- **Sanning:** All incidentdata är syntetisk. Replay är inspelad och deterministisk. Live-läget gör riktiga Gemini-anrop.
- **Viktigt:** Systemet rekommenderar bara nästa steg. Det ändrar aldrig ett system eller gör en rollback.

## Berättelsen i en mening

En webbshops checkout börjar fallera. Incident Detective låter en AI undersöka syntetiska metrics, logs, traces och runbooks, kräver bevis för slutsatsen och låter sedan Java kontrollera resultatet mot ett syntetiskt facit.

## Sex endpoints och varför de finns

| Endpoint | Vad den gör | Varför den finns |
|---|---|---|
| `GET /api/v1/capabilities` | Beskriver aktiv runtime, säkra gränser, modellbudget, retrieval och cachepolicy. | Frontend ska kunna visa vad just denna backend faktiskt kör utan hårdkodade påståenden. |
| `GET /api/v1/scenarios` | Listar säkra sammanfattningar av incidenterna. | Besökaren behöver välja ett fall utan att få evidens eller facit i förväg. |
| `POST /api/v1/scenarios/{scenarioId}/runs/recorded-replay` | Spelar upp en färdig, deterministisk utredning. | Demon ska alltid kunna visas snabbt, gratis och reproducerbart. Ingen modell körs. |
| `POST /api/v1/scenarios/{scenarioId}/runs/live-ai` | Låter Gemini välja read-only tools och skapa en ny diagnos. | Det bevisar verklig tool calling, structured output och verifiering. Varje request kräver uttryckligt godkännande. |
| `POST /api/v1/generated-cases/runs/live-ai` | Genererar ett request-lokalt Payment Timeout-fall och låter Gemini utreda det i samma request. | Besökaren kan skapa ett reproducerbart fall utan logguppladdning, lagring eller extra API-livscykel. |
| `GET /api/v1/proof/evals/retrieval` | Returnerar en publicerad, historisk retrieval-eval på aggregatnivå. | RAG- och embeddingpåståenden ska kunna granskas utan att en ny eval startas. |

`GET` läser en resurs. `POST` startar en ny incidentkörning och skapar därför
bland annat ett nytt `run_id`. Det finns ingen HTTP-endpoint som startar en
eval, importerar embeddings eller utför remediation.

## Prova i Swagger

Starta backend:

```bash
./mvnw spring-boot:run
```

Öppna sedan [Swagger UI](http://localhost:8080/swagger-ui.html).

En trygg demoordning är:

1. Kör `GET /api/v1/capabilities` och visa de aktiva säkerhets- och runtimegränserna.
2. Kör `GET /api/v1/scenarios` och visa att svaret bara innehåller incidentens startläge.
3. Kör recorded replay för `checkout-orders-at-risk-v1`.
4. Öppna tool events, evidence IDs, diagnosen och verifieringen i svaret.
5. Visa generated-endpointens fyra controls och förklara att samma seed ger
   samma syntetiska signaler men inte nödvändigtvis identiskt LLM-svar.
6. Visa retrieval-proof och skilj den historiska mätningen från aktiv runtime.
7. Kör live endast när servern är aktiverad och du medvetet vill använda en modellrequest.

## 0. Capabilities

`GET /api/v1/capabilities` är frontendens maskinläsbara källa för:

- recorded/live-läge och deras truth labels,
- de fyra read-only-verktygen,
- liveaktivering, providerkonfiguration, modell/prompt och hårda call-/tidsbudgeter,
- generated-case-controls samt om dygnskvoten är processlokal eller databasgemensam,
- aktiv retrieval-backend: `deterministic_fixture` eller `pgvector_exact_cosine`,
- aktiv embeddingprofil endast när pgvector faktiskt är aktivt,
- cachepolicy: `provider_implicit`, explicit caching avstängd.

`live_ai.enabled_by_configuration` och `credentials_configured` visar de två
lokala förutsättningarna separat. `request_configured = true` betyder att båda
är uppfyllda, men garanterar inte att providern är nåbar eller frisk. Endpointen
returnerar aldrig en providernyckel.

## 1. Scenario-listan

Förkortat svar:

```json
{
  "scenarios": [
    {
      "scenario_id": "checkout-orders-at-risk-v1",
      "title": "Checkout errors threaten orders",
      "business_impact_summary": "Synthetic estimate for 10:02–10:12 UTC: 147 of 800 checkout attempts failed.",
      "affected_services": ["STOREFRONT", "CHECKOUT_API", "PAYMENT_ADAPTER"]
    }
  ]
}
```

Det viktiga är vad som **inte** returneras: inget evidence inventory, ingen färdig diagnos och inget `GroundTruth`.

## 2. Recorded replay

Replay kräver ingen request body:

```text
POST /api/v1/scenarios/checkout-orders-at-risk-v1/runs/recorded-replay
```

Förkortat svar:

```json
{
  "mode": "recorded_replay",
  "truth_label": "Simulated incident — recorded deterministic replay.",
  "status": "completed",
  "tool_events": [
    {
      "tool_name": "get_metrics",
      "safe_summary": "Read checkout failures and payment latency for the incident window.",
      "evidence": [
        {
          "evidence_id": "cpt-v1-metric-checkout-failure-rate",
          "evidence_type": "metric"
        }
      ]
    }
  ],
  "diagnosis": {
    "status": "diagnosed",
    "root_cause_code": "PAYMENT_TIMEOUT_CONFIG",
    "affected_service": "PAYMENT_ADAPTER"
  },
  "model_id": null,
  "prompt_version": null,
  "token_usage": null,
  "estimated_cost_usd": null
}
```

Null-värdena är en viktig sanningssignal: replay använder ingen modell, inga tokens och ingen modellkostnad.

## 3. Live AI

Request body:

```json
{
  "confirm_live_ai": true
}
```

Live-svaret har samma grundberättelse som replay, men innehåller verklig körmetadata:

- `mode = live_ai`,
- sanningsetiketten `Simulated incident — real AI investigation.`,
- de tools Gemini faktiskt valde och deras säkra argument,
- endast evidens som toolsen faktiskt returnerade,
- modell- och promptversion,
- antal model/tool calls,
- tokenanvändning, latency och kostnadsgrund,
- en deterministisk verifieringsrapport.

`confirm_live_ai` är ett medvetet kostnadsval, inte autentisering. Replay är standardläget och kostar inget modellanrop.

## 4. Publicerade evalbevis

Proof-endpointen läser en versionsmärkt snapshot som paketerats med backend. Den
startar inga evals och gör inga provider- eller databasanrop.

`GET /api/v1/proof/evals/retrieval` visar den frysta pgvector-/embeddingmätningen
med egen git SHA och tidpunkt: development 5/5 och held-out 4/5 Hit@4. Null
providerusage och null kostnad betyder **Not reported**, inte noll. Endpointen
visar också uttryckligen att adversarial synthesis-säkerhet inte mättes av just
retrieval-evalen.

Git SHA och tidpunkt i proof-svaret hör till den publicerade mätningen. De ska
inte beskrivas som den nuvarande API-processens checkout. Diagnoskvalitet visas
i stället per körning genom schema, citationer, evidensstöd, claim coverage och
korrekt diagnos.

## De fyra read-only-verktygen

| Tool | Enkel fråga det svarar på | Exempel i checkoutfallet |
|---|---|---|
| `get_metrics` | Hur stort är problemet? | Felandel, antal misslyckade checkoutförsök och p95-latency. |
| `search_logs` | Vad hände i tjänsterna? | Release-, konfigurations- och timeout-händelser. |
| `get_trace` | Var gick ett enskilt requestflöde sönder? | En checkout spenderade nästan all tid i payment authorization. |
| `retrieve_runbooks` | Vilken säker arbetsmetod rekommenderas? | Jämför timeoutkonfiguration med observerad latency och kräv mänskligt godkännande. |

Alla fyra är scenarioavgränsade och kan bara läsa syntetisk data. Modellen får aldrig terminalåtkomst, deployverktyg eller ett rollback-tool.

### Vad `retrieve_runbooks` är just nu

Recorded replay spelar upp sina frysta tool-resultat och gör ingen ny
retrieval. När den vanliga backendprofilen faktiskt kör runbookverktyget används
deterministisk scenario-fixture, så demon fungerar utan databas eller provider.
I den uttryckliga `rag`-profilen söker samma tool i en fristående korpus med 10
syntetiska dokument och 12 chunks. Gemini skapar 768-dimensionella embeddings
och PostgreSQL/pgvector rankar högst fyra chunks med exakt cosine-sökning.

En framtida Engineering View kan visa dokument, chunk, version, rank, similarity, embeddingmodell, innehållshash, korpusversion och retrieval-backend när modellen väljer runbookverktyget. Metrics, logs och traces stannar bakom sina typade tools. Retrieval v1 är mätt till development 5/5 och held-out 4/5 Hit@4; det missade held-out-fallet och den osäkra topprankade runbooken är öppna kvalitetsproblem.

## Vad verifieraren kontrollerar

Verifiering är vanlig deterministisk Java-kod, inte ett andra AI-svar. Den håller fyra frågor isär:

1. **Citation validity:** Finns varje citerat evidence-ID bland evidensen modellen faktiskt såg?
2. **Evidence precision/support:** Stöder just den evidensen just det påståendet enligt det definierade facitstödet?
3. **Claim coverage:** Innehåller svaret de förväntade claimnycklarna, även när ett kortare svar skulle kunna få perfekt precision på det lilla det faktiskt sade?
4. **Diagnosis correctness:** Matchar rotorsak och påverkad tjänst facit, eller avstod modellen korrekt när evidensen inte räckte?

De måste hållas isär. En modell kan gissa rätt rotorsak men använda dåliga bevis. Den kan citera ett riktigt ID som inte stöder påståendet eller utelämna trigger och kundpåverkan trots att de finns i referensen. API:t visar bara coverage-antal och score; det läcker inte vilka dolda claims som saknas.

### Två statusnivåer som inte ska blandas ihop

- `diagnosis.status = insufficient_evidence` betyder att modellen ärligt avstod. Körningen kan fortfarande vara tekniskt `completed`.
- `run.status = verification_failed` betyder att en strukturerad diagnos kom tillbaka men att den deterministiska kontrollen hittade ett hårt fel, till exempel ett påhittat evidence-ID.

Ett `verification_failed`-resultat returneras som HTTP 200 eftersom API-körningen lyckades och verifieringsutfallet är det resultat som ska inspekteras. Providerfel och ogiltiga requests använder däremot 4xx/5xx.

## GroundTruth utan överdrift

`GroundTruth` är dolt för modellen under `COLLECT` och `SYNTHESIZE`. Verifieraren öppnar det först efter sista modellanropet.

Det är däremot inte en hemlighet för en människa: de två nuvarande demofaciten
finns i repot och fungerar som transparenta replay-referenser. Backenden
publicerar ingen batch-accuracy för diagnoser. I stället verifieras varje
replay- eller livekörning separat efter modellens sista anrop.

## Felsvar som går att förstå

Felsvar använder `application/problem+json` och en stabil `code`.

| HTTP | Exempel på kod | Betydelse |
|---:|---|---|
| 400 | `LIVE_AI_CONFIRMATION_REQUIRED` | Liveanropet bekräftades inte. |
| 400 | `INVALID_REQUEST_BODY` | JSON saknas, är trasig eller innehåller oväntade fält. |
| 404 | `SCENARIO_NOT_FOUND` | Scenario-ID finns inte. |
| 404 | `ROUTE_NOT_FOUND` | Ingen route matchar klientens path. |
| 405 | `METHOD_NOT_ALLOWED` | Rätt path användes med fel HTTP-metod. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Live-body skickades inte som `application/json`. |
| 429 | `LIVE_AI_RATE_LIMITED` | En annan körning pågår eller den lokala startgränsen är nådd. |
| 429 | `LIVE_AI_DAILY_LIMIT_REACHED` | Den konfigurerade dygnskvoten är slut; dess scope framgår av capabilities. |
| 429 | `MODEL_PROVIDER_RATE_LIMITED` | Providern avvisade det begränsade modellanropet tillfälligt. |
| 502 | `MODEL_PROVIDER_ERROR` | Leverantören eller modellkontraktet misslyckades. Rå leverantörsdata returneras inte. |
| 502 | `RAG_EMBEDDING_PROVIDER_ERROR` | Embeddingprovidern kunde inte slutföra retrievalanropet. |
| 502 | `RAG_EMBEDDING_RESPONSE_INVALID` | Embeddingsvaret bröt det förväntade kontraktet. |
| 503 | `LIVE_AI_DISABLED` | Live AI är avstängt på servern. |
| 503 | `LIVE_AI_NOT_CONFIGURED` | Servern saknar modellkonfiguration. |
| 503 | `RAG_EMBEDDING_NOT_CONFIGURED` | Embeddingprovidern är inte konfigurerad. |
| 503 | `RAG_INDEX_NOT_READY` | Korpusantal eller innehållshash matchar inte aktivt pgvectorindex. |
| 503 | `RAG_DATABASE_UNAVAILABLE` | pgvector-databasen är tillfälligt otillgänglig. |
| 504 | `MODEL_PROVIDER_TIMEOUT` | Modellleverantören svarade inte inom sitt timeoutfönster. |
| 504 | `LIVE_INVESTIGATION_TIMEOUT` | Hela den begränsade utredningen nådde 45 sekunder. |

Ett fel triggar aldrig en dold automatisk live-retry och märks aldrig om till
en lyckad Live AI-körning. `LIVE_AI_RATE_LIMITED` kan ha `Retry-After`;
provider-rate-limit utlovar inget tillförlitligt sådant värde. En ny livekörning
kräver alltid ett nytt medvetet användarval.

## Vad de automatiska API-testerna bevisar

- Båda recorded-scenarierna returnerar rätt verktygsordning, klickbara evidence IDs, diagnos och verifiering.
- Scenario-listan läcker inte facit eller evidens.
- Ett komplett stubbat liveflöde returnerar fyra tool events, fyra evidenstyper, tre model calls, tokens och verifieringsresultat. Ett verkligt liveflöde får välja färre tools och testas därför mot bounds och kontrakt i stället för en påhittad fast sekvens.
- `insufficient_evidence` fungerar som ett giltigt avstående.
- Ett påhittat evidence-ID blir `verification_failed`.
- Modellnyckel, råa providersvar, GroundTruth och evidens som modellen inte såg hålls borta från publika svar.
- Trasig JSON, fel content-type, okänt scenario, rate limit, providerfel och timeout mappas till avsedda felkontrakt.
- Capability-kontraktet visar aktiv profil/retrieval utan credentials eller hårdkodad frontendlogik.
- Retrieval-proof läcker inte GroundTruth, modelltext eller råa providerfel och kan inte starta en eval.
- OpenAPI innehåller exakt de sex produkt- och proof-endpointsen och skiljer på replay, live, generated live och historisk retrieval.

De viktigaste testerna finns i:

- [`ScenarioCatalogApiTest`](../src/test/java/dev/shirwac/incidentdetective/scenario/ScenarioCatalogApiTest.java)
- [`RecordedReplayApiTest`](../src/test/java/dev/shirwac/incidentdetective/replay/RecordedReplayApiTest.java)
- [`LiveInvestigationApiTest`](../src/test/java/dev/shirwac/incidentdetective/live/LiveInvestigationApiTest.java)
- [`GeneratedCaseApiTest`](../src/test/java/dev/shirwac/incidentdetective/generated/GeneratedCaseApiTest.java)
- [`CapabilitiesApiTest`](../src/test/java/dev/shirwac/incidentdetective/capabilities/CapabilitiesApiTest.java)
- [`ProofEvalApiTest`](../src/test/java/dev/shirwac/incidentdetective/proof/ProofEvalApiTest.java)
- [`ApiCorsConfigurationTest`](../src/test/java/dev/shirwac/incidentdetective/api/ApiCorsConfigurationTest.java)
- [`OpenApiDocumentationTest`](../src/test/java/dev/shirwac/incidentdetective/openapi/OpenApiDocumentationTest.java)

## Förslag på presentation, cirka 7 minuter

### 0:00–0:45 – problemet

“När checkout börjar fallera behöver både verksamheten och utvecklarna snabbt förstå vad som är påverkat, vad slutsatsen bygger på och vad som är säkert att göra härnäst.”

### 0:45–1:30 – vad jag har byggt

“Incident Detective använder bara syntetisk incidentdata, men live-läget gör en riktig AI-utredning. Det är inte en chatbot och det gör inga ändringar i systemen.”

### 1:30–2:20 – välj scenario

Visa scenario-listan och förklara kundpåverkan. Säg att facit och evidens inte skickas med i startläget.

### 2:20–3:20 – kör replay

Visa truth label och tool-tidslinjen. Förklara att replay är en gratis, stabil referens och att `model_id`, tokens och kostnad därför är null.

### 3:20–4:20 – öppna bevisen

Välj metric, log och trace. Koppla varje verktyg till en enkel fråga: storlek, händelse och requestflöde. Visa sedan att diagnosens claims pekar på evidence IDs.

### 4:20–5:20 – varför ska man tro på svaret?

Förklara verifierarens tre frågor: ID:t finns, beviset stöder påståendet och diagnosen matchar facit. Visa att ett påhittat ID skulle ge `verification_failed`.

### 5:20–6:15 – vad som är verklig AI

Förklara `COLLECT → SYNTHESIZE → VERIFY`: Gemini väljer read-only tools, lämnar ett strukturerat svar och Java kontrollerar det. Visa model ID, promptversion, calls, tokens, latency och kostnadsgrund.

### 6:15–7:00 – ärlig avslutning

“Retrieval-evalen mätte riktig pgvector- och embeddingretrieval: development
5/5 och held-out 4/5 Hit@4. Varje diagnos graderas separat av Java mot den
evidens modellen såg. Jag visar därför ett verkligt failure case utan att
låtsas ha full systemaccuracy. OpenTelemetry, lokal verifiering av containerimagen
och Cloud Run återstår.”

## Om publiken är icke-teknisk

Fokusera på tre frågor:

1. Vad gick sönder för kunden?
2. Vilka bevis använde systemet?
3. Varför är nästa steg säkert?

Hoppa över JSON Schema och klassnamn om ingen frågar.

## Om publiken är teknisk

Var beredd att förklara:

- varför metrics, logs och traces går genom typade tools medan endast runbooks använder pgvector i `rag`-profilen,
- varför state machine är begränsad till två collection-rundor och tre model calls,
- hur trace-ID måste upptäckas i tidigare tool-evidens innan `get_trace` får köras,
- varför synthesis saknar tools,
- varför verifieraren är deterministisk,
- skillnaden mellan verifiering av aktuell körning och en historisk retrieval-eval,
- varför concurrency och rolling rate limit är per backendinstans medan
  dygnskvoten kan vara `process_local` eller `database_global`.

## Bra kodordning i IntelliJ

Följ ett liveanrop i denna ordning:

1. [`LiveInvestigationController`](../src/main/java/dev/shirwac/incidentdetective/live/LiveInvestigationController.java) – HTTP-kontraktet.
2. [`LiveInvestigationService`](../src/main/java/dev/shirwac/incidentdetective/live/LiveInvestigationService.java) – den begränsade state machinen.
3. [`InvestigationToolExecutor`](../src/main/java/dev/shirwac/incidentdetective/investigation/tools/InvestigationToolExecutor.java) – typad routing till read-only tools.
4. [`GeminiInvestigationModelGateway`](../src/main/java/dev/shirwac/incidentdetective/ai/GeminiInvestigationModelGateway.java) – function calling och structured output.
5. [`DeterministicVerifier`](../src/main/java/dev/shirwac/incidentdetective/domain/verification/DeterministicVerifier.java) – kontrollen efter modellen.

För officiellt läsmaterial och korta övningar, använd [lärspåret](./LEARNING-PATH.md).

## Ännu inte färdigt

- Full modellaccuracy, held-out live-kvalitet och p95 är inte mätta.
- En retrieval-eval är inte ett bevis på säker adversarial synthesis; den säkerhetsgränsen är uttrycklig i proof-svaret.
- Git SHA sparas ännu inte i körresultatet.
- Strukturerade JSON-loggar och OpenTelemetry återstår.
- Concurrency och rolling rate limit är minnesbaserade per
  applikationsinstans. Dygnskvotens scope exponeras i capabilities.
- Swagger ska omprövas före publik Cloud Run-deploy.
- Dockerfile och CI finns, men lokal imageverifiering, Cloud Run, budgetlarm och
  smoke-serien 20/20 återstår.

Detta är medvetet en funktionell demo under utveckling, inte ett påstående om ett färdigt produktionssystem.

För exakt frontendintegration, null-regler, CORS och retry-policy, se
[`FRONTEND-API-HANDOFF.md`](./FRONTEND-API-HANDOFF.md).
