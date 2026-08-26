# API- och presentationsguide

- **Mål:** Jag ska kunna prova API:t i Swagger och förklara varför varje del finns.
- **Sanning:** All incidentdata är syntetisk. Replay är inspelad och deterministisk. Live-läget gör riktiga Gemini-anrop.
- **Viktigt:** Systemet rekommenderar bara nästa steg. Det ändrar aldrig ett system eller gör en rollback.

## Berättelsen i en mening

En webbshops checkout börjar fallera. Incident Detective låter en AI undersöka syntetiska metrics, logs, traces och runbooks, kräver bevis för slutsatsen och låter sedan Java kontrollera resultatet mot ett syntetiskt facit.

## Tre endpoints och varför de finns

| Endpoint | Vad den gör | Varför den finns |
|---|---|---|
| `GET /api/v1/scenarios` | Listar säkra sammanfattningar av incidenterna. | Besökaren behöver välja ett fall utan att få evidens eller facit i förväg. |
| `POST /api/v1/scenarios/{scenarioId}/runs/recorded-replay` | Spelar upp en färdig, deterministisk utredning. | Demon ska alltid kunna visas snabbt, gratis och reproducerbart. Ingen modell körs. |
| `POST /api/v1/scenarios/{scenarioId}/runs/live-ai` | Låter Gemini välja read-only tools och skapa en ny diagnos. | Det bevisar verklig tool calling, structured output och verifiering. Varje request kräver uttryckligt godkännande. |

`GET` läser en resurs. `POST` startar en ny körning och skapar därför bland annat ett nytt `run_id`.

## Prova i Swagger

Starta backend:

```bash
./mvnw spring-boot:run
```

Öppna sedan [Swagger UI](http://localhost:8080/swagger-ui.html).

En trygg demoordning är:

1. Kör `GET /api/v1/scenarios` och visa att svaret bara innehåller incidentens startläge.
2. Kör recorded replay för `checkout-orders-at-risk-v1`.
3. Öppna tool events, evidence IDs, diagnosen och verifieringen i svaret.
4. Visa live-endpointens request body och förklara kostnadsbekräftelsen.
5. Kör live endast när servern är aktiverad och du medvetet vill använda en modellrequest.

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

## De fyra read-only-verktygen

| Tool | Enkel fråga det svarar på | Exempel i checkoutfallet |
|---|---|---|
| `get_metrics` | Hur stort är problemet? | Felandel, antal misslyckade checkoutförsök och p95-latency. |
| `search_logs` | Vad hände i tjänsterna? | Release-, konfigurations- och timeout-händelser. |
| `get_trace` | Var gick ett enskilt requestflöde sönder? | En checkout spenderade nästan all tid i payment authorization. |
| `retrieve_runbooks` | Vilken säker arbetsmetod rekommenderas? | Jämför timeoutkonfiguration med observerad latency och kräv mänskligt godkännande. |

Alla fyra är scenarioavgränsade och kan bara läsa syntetisk data. Modellen får aldrig terminalåtkomst, deployverktyg eller ett rollback-tool.

### Vad `retrieve_runbooks` är just nu

Tool-kontraktet, metadata och citationerna är verkliga, men retrievalalgoritmen är ännu en lokal keyword-sökning i runbooks som redan ligger i respektive scenariofixture. Det är en fungerande övergång och ett bra kontraktstest, men inte ett trovärdigt RAG-bevis.

Nästa slice flyttar runbooks till en fristående korpus med realistiska distraktorer. Gemini skapar 768-dimensionella embeddings och PostgreSQL/pgvector rankar högst fyra chunks med exakt cosine-sökning. Engineering View och evalrapporten ska då visa dokument, chunk, version, rank, similarity, embeddingmodell och korpusversion. Metrics, logs och traces stannar bakom sina typade tools.

## Vad verifieraren kontrollerar

Verifiering är vanlig deterministisk Java-kod, inte ett andra AI-svar. Den ställer tre olika frågor:

1. **Citation validity:** Finns varje citerat evidence-ID bland evidensen modellen faktiskt såg?
2. **Evidence precision/support:** Stöder just den evidensen just det påståendet enligt det definierade facitstödet?
3. **Diagnosis correctness:** Matchar rotorsak och påverkad tjänst facit, eller avstod modellen korrekt när evidensen inte räckte?

De måste hållas isär. En modell kan gissa rätt rotorsak men använda dåliga bevis. Den kan också citera ett riktigt ID som inte stöder påståendet.

### Två statusnivåer som inte ska blandas ihop

- `diagnosis.status = insufficient_evidence` betyder att modellen ärligt avstod. Körningen kan fortfarande vara tekniskt `completed`.
- `run.status = verification_failed` betyder att en strukturerad diagnos kom tillbaka men att den deterministiska kontrollen hittade ett hårt fel, till exempel ett påhittat evidence-ID.

Ett `verification_failed`-resultat returneras som HTTP 200 eftersom API-körningen lyckades och verifieringsutfallet är det resultat som ska inspekteras. Providerfel och ogiltiga requests använder däremot 4xx/5xx.

## GroundTruth utan överdrift

`GroundTruth` är dolt för modellen under `COLLECT` och `SYNTHESIZE`. Verifieraren öppnar det först efter sista modellanropet.

Det är däremot inte en hemlighet för en människa: de två nuvarande demofaciten finns i det publika repot. De fungerar som transparenta referensfall. Vecka 3 behöver separata held-out evalfall för trovärdig kvalitetsmätning.

## Felsvar som går att förstå

Felsvar använder `application/problem+json` och en stabil `code`.

| HTTP | Exempel på kod | Betydelse |
|---:|---|---|
| 400 | `LIVE_AI_CONFIRMATION_REQUIRED` | Liveanropet bekräftades inte. |
| 400 | `INVALID_REQUEST_BODY` | JSON saknas, är trasig eller innehåller oväntade fält. |
| 404 | `SCENARIO_NOT_FOUND` | Scenario-ID finns inte. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Live-body skickades inte som `application/json`. |
| 429 | `LIVE_AI_RATE_LIMITED` | En annan körning pågår eller den lokala startgränsen är nådd. |
| 502 | `MODEL_PROVIDER_ERROR` | Leverantören eller modellkontraktet misslyckades. Rå leverantörsdata returneras inte. |
| 503 | `LIVE_AI_DISABLED` | Live AI är avstängt på servern. |
| 503 | `LIVE_AI_NOT_CONFIGURED` | Servern saknar modellkonfiguration. |
| 504 | `MODEL_PROVIDER_TIMEOUT` | Modellleverantören svarade inte inom sitt timeoutfönster. |
| 504 | `LIVE_INVESTIGATION_TIMEOUT` | Hela den begränsade utredningen nådde 45 sekunder. |

Ett fel triggar aldrig en dold automatisk live-retry och märks aldrig om till en lyckad Live AI-körning.

## Vad de automatiska API-testerna bevisar

- Båda recorded-scenarierna returnerar rätt verktygsordning, klickbara evidence IDs, diagnos och verifiering.
- Scenario-listan läcker inte facit eller evidens.
- Ett komplett stubbat liveflöde returnerar fyra tool events, fyra evidenstyper, tre model calls, tokens och verifieringsresultat. Ett verkligt liveflöde får välja färre tools och testas därför mot bounds och kontrakt i stället för en påhittad fast sekvens.
- `insufficient_evidence` fungerar som ett giltigt avstående.
- Ett påhittat evidence-ID blir `verification_failed`.
- Modellnyckel, råa providersvar, GroundTruth och evidens som modellen inte såg hålls borta från publika svar.
- Trasig JSON, fel content-type, okänt scenario, rate limit, providerfel och timeout mappas till avsedda felkontrakt.
- OpenAPI innehåller exakt de tre produkt-endpointsen och skiljer på replay- och live-svar.

De viktigaste testerna finns i:

- [`ScenarioCatalogApiTest`](../src/test/java/dev/shirwac/incidentdetective/scenario/ScenarioCatalogApiTest.java)
- [`RecordedReplayApiTest`](../src/test/java/dev/shirwac/incidentdetective/replay/RecordedReplayApiTest.java)
- [`LiveInvestigationApiTest`](../src/test/java/dev/shirwac/incidentdetective/live/LiveInvestigationApiTest.java)
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

“Den senaste v5-smoken gav rätt inventory-diagnos och 5/5 stödda evidenslänkar efter att verifieraren hittat en 3/5-regression i v4. Det är inte ett accuracyresultat. Nästa bevis är riktig pgvector-retrieval med Hit@4, därefter fulla development- och held-out-evals. Observability, container och Cloud Run återstår.”

## Om publiken är icke-teknisk

Fokusera på tre frågor:

1. Vad gick sönder för kunden?
2. Vilka bevis använde systemet?
3. Varför är nästa steg säkert?

Hoppa över JSON Schema och klassnamn om ingen frågar.

## Om publiken är teknisk

Var beredd att förklara:

- varför telemetri går genom typade tools men endast runbooks senare ska använda pgvector,
- varför state machine är begränsad till två collection-rundor och tre model calls,
- hur trace-ID måste upptäckas i tidigare tool-evidens innan `get_trace` får köras,
- varför synthesis saknar tools,
- varför verifieraren är deterministisk,
- skillnaden mellan demofacit i publikt repo och framtida held-out evalfall,
- varför den nuvarande rate limiten är per backendinstans och ännu inte ett komplett publikt missbruksskydd.

## Bra kodordning i IntelliJ

Följ ett liveanrop i denna ordning:

1. [`LiveInvestigationController`](../src/main/java/dev/shirwac/incidentdetective/live/LiveInvestigationController.java) – HTTP-kontraktet.
2. [`LiveInvestigationService`](../src/main/java/dev/shirwac/incidentdetective/live/LiveInvestigationService.java) – den begränsade state machinen.
3. [`InvestigationToolExecutor`](../src/main/java/dev/shirwac/incidentdetective/investigation/tools/InvestigationToolExecutor.java) – typad routing till read-only tools.
4. [`GeminiInvestigationModelGateway`](../src/main/java/dev/shirwac/incidentdetective/ai/GeminiInvestigationModelGateway.java) – function calling och structured output.
5. [`DeterministicVerifier`](../src/main/java/dev/shirwac/incidentdetective/domain/verification/DeterministicVerifier.java) – kontrollen efter modellen.

För officiellt läsmaterial och korta övningar, använd [lärspåret](./LEARNING-PATH.md).

## Ännu inte färdigt

- Kvalitet över 18 evalfall och held-out data är inte mätt.
- Runbook-retrieval använder ännu scenarioförvald lokal keyword matching, inte en fristående PostgreSQL/pgvector-korpus.
- Git SHA sparas ännu inte i körresultatet.
- Strukturerade JSON-loggar och OpenTelemetry återstår.
- Rate limit är minnesbaserad per applikationsinstans.
- Swagger ska omprövas före publik Cloud Run-deploy.
- Container, Cloud Run, budgetlarm och smoke-serien 20/20 återstår.

Detta är medvetet en funktionell sprintprodukt under utveckling, inte ett påstående om ett färdigt produktionssystem.
