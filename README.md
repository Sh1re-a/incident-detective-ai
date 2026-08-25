# Incident Detective

Incident Detective är mitt individuella Passion Project under SALT Sprint 3, 25 augusti–18 september 2026.

> **Slutmål:** “Simulated incident — real AI investigation.”
>
> **Nuvarande läge:** Två deterministiska incidenter fungerar som recorded replay. Ett separat, uttryckligen aktiverat liveflöde använder Gemini, fyra typade read-only tools, structured output och Java-verifiering mot dolt facit. En live smoke-körning är korrekt genomförd; ett andra scenario har hittills träffat provider-timeout. Frontend, pgvector, evalharness och deployment är ännu inte byggda.

## Idén i korthet

En besökare möter en syntetisk incident där fel i en webbshops checkout hotar beställningar. Besökaren kan starta en utredning, följa vilka skrivskyddade verktyg AI-systemet använder, öppna evidensen bakom slutsatsen och till sist jämföra diagnosen med ett dolt syntetiskt facit.

Upplevelsen får två nivåer:

- **Story View** ska på 30–60 sekunder förklara vad som gick sönder, kund- och affärspåverkan, vad AI:n gjorde och varför svaret går att kontrollera.
- **Engineering View** ska visa verktygsanrop, evidence IDs, validering, körmetadata, latens, tokenanvändning, uppskattad kostnad och evalresultat.

Det här är inte en chatbot och inte ett autonomt driftssystem. Systemet får rekommendera ett säkert nästa steg, men utför aldrig rollback eller annan åtgärd.

## Sanningskontrakt

| Kategori | Vad det betyder i projektet |
|---|---|
| **Simulerat** | Alla logs, metrics, traces, runbooks, releasehändelser, incidenter och uppskattningar av affärspåverkan är syntetiska. |
| **Verkligt just nu** | Java-domänkontrakt, två scenario-paket, fyra typade read-only tools, separata replay/live-endpoints, riktiga Gemini-anrop, function calling, structured diagnosis, evidence IDs, dold `GroundTruth`, deterministisk verifiering och uppmätt körmetadata. |
| **Delvis verkligt** | Runbooks hämtas genom ett riktigt typat tool, men retrieval är ännu lokal och deterministisk i stället för PostgreSQL/pgvector. En smoke-körning har lyckats, men det är inte en evalrapport eller ett stabilitetsbevis. |
| **Planerat, inte verifierat** | Frontend, 18 evalfall, accuracy/p95, pgvector, OpenTelemetry, Cloud Run, publik fallback och användartester. Kontots framtida billingläge är inte verifierat. Inga mål får anges som uppnådda före faktisk mätning. |

När en sparad körning visas ska den märkas **“Simulated incident — recorded deterministic replay.”** Den får aldrig ha en “Live AI”-badge. Slutmålets live-märkning används först när ett verkligt modellanrop kör utredningen.

## Status 25 augusti 2026

- Sprintplan, prioriterad backlog, tekniska beslut och dag‑1‑kontrakt finns.
- `Scenario`, `Evidence`, `Diagnosis`, separat `GroundTruth` och tre oberoende verifieringsresultat är implementerade i Java.
- Två seedade scenarier finns: ett payment-timeoutfall och ett inventory-kontraktsfall. Båda innehåller metrics, logs, trace, runbook och avsiktligt brus.
- Ett lokalt recorded-replay-API returnerar ordnade tool events, endast faktiskt sedd evidens, diagnos, verifieringsrapport och en begränsad facitjämförelse efter avslutad körning.
- Liveflödet kör `COLLECT → SYNTHESIZE → VERIFY` med `get_metrics`, `search_logs`, `get_trace` och `retrieve_runbooks`. Modellen ser bara scenario och tool-returnerad evidens; verifieraren öppnar facit först efter sista modellanropet.
- Standardprofilen är `gemini-3.5-flash-lite` med `MINIMAL` thinking och det versionsmärkta kontraktet `gemini-live-v2`. Live måste både vara aktiverat på servern och bekräftas i varje request.
- En lyckad live smoke-körning för payment-timeoutscenariot gav korrekt rotorsak och tjänst, giltiga citation IDs, 3 modellanrop, 5 tool calls, 3 967 input tokens, 808 output tokens och 5 209 ms. Betalt standardlistpris för dessa tokens uppskattas till cirka 0,00321 USD; faktisk free-tier-debitering kan vara 0 USD.
- Detta är **inte** ett accuracy- eller p95-resultat. `gemini-3.7-flash` timeoutade i två försök och inventory-scenariots första collection-anrop timeoutade i två Flash-Lite-försök. De observerade felen ska ingå i kommande evals och fallbackarbete.
- Backendens vanliga testsuite har 131 gröna tester. Det explicita nätverksbaserade smoke-testet ingår inte i den vanliga testsuiten.
- Projektgrunden är publicerad på GitHub utan open-source-licens. Ingen demo är deployad.

## Lokalt API

Starta backend med `./mvnw spring-boot:run`. De två nuvarande körningarna startas med `POST`:

- `/api/v1/scenarios/checkout-orders-at-risk-v1/runs/recorded-replay`
- `/api/v1/scenarios/checkout-cart-segment-failures-v1/runs/recorded-replay`

Svaret märks alltid **“Simulated incident — recorded deterministic replay.”** Modell-ID, promptversion, tokenanvändning och kostnad är `null`, eftersom ingen modell körs i replay-läget.

Liveutredningen använder samma scenario-ID men endpointen:

`POST /api/v1/scenarios/{scenarioId}/runs/live-ai`

Request body:

```json
{
  "confirm_live_ai": true
}
```

Live kräver både `INCIDENT_DETECTIVE_LIVE_AI_ENABLED=true` på servern och `confirm_live_ai: true` i requesten. Ett lyckat svar märks **“Simulated incident — real AI investigation.”** Endpointen utför aldrig remediation.

### Swagger och OpenAPI lokalt

När backend kör på port 8080 kan API:t läsas och provas här:

- [Swagger UI](http://localhost:8080/swagger-ui.html)
- [OpenAPI JSON](http://localhost:8080/v3/api-docs)

Dokumentationen visar både `recorded_replay` och `live_ai`, deras olika truth labels, explicita livebekräftelse och manuellt godkända nästa steg. Inför en framtida Cloud Run-deploy ska det beslutas uttryckligen om Swagger ska vara publik eller avstängd.

### Lokal Gemini-nyckel

Kopiera `local-secrets.properties.example` till `local-secrets.properties`, lägg nyckeln efter `GEMINI_API_KEY=` och sätt `INCIDENT_DETECTIVE_LIVE_AI_ENABLED=true` när du medvetet vill tillåta liveanrop. Den lokala filen ignoreras av Git, ligger utanför `src` och paketeras inte i JAR-filen. Starta om backend efter en ändring. Recorded replay fungerar utan nyckel.

Ett live-smoketest körs aldrig av den vanliga testsuiten. Det måste aktiveras uttryckligen:

```bash
./mvnw -q -Dtest=GeminiLiveSmokeIT -Drun.gemini.smoke=true test
```

## Projektdokument

- [GitHub-repo](https://github.com/Sh1re-a/incident-detective-ai)
- [Sprintplan](./SPRINTPLAN.md)
- [Prioriterad backlog](./BACKLOG.md)
- [Tekniska beslut](./docs/DECISIONS.md)
- [Första live-smoke och observerade failure cases](./docs/LIVE-SMOKE-2026-08-25.md)
- [Dag‑1‑kontrakt för granskning](./docs/DAY-1-CONTRACTS.md)
- [Lärspår och verktyg](./docs/LEARNING-PATH.md)

## Teknisk riktning

Den planerade lösningen är ett monorepo med en deploybar container:

- React, TypeScript och Vite för gränssnittet.
- Java 21, Spring Boot 4.1.1, Spring MVC, Jakarta Validation, Jackson och springdoc OpenAPI för API, validering och lokal Swagger-dokumentation.
- En modellleverantör med custom function tools och structured output. Nuvarande mätta standardprofil är `gemini-3.5-flash-lite` + `MINIMAL` thinking genom Googles officiella Java SDK. Modellen stöder [function calling och structured outputs](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite); prisestimat utgår från [Googles betalda standardlistpris](https://ai.google.dev/gemini-api/docs/pricing), medan faktisk free-tier-kostnad kan vara noll.
- En explicit och begränsad `COLLECT → SYNTHESIZE → VERIFY`-process.
- PostgreSQL och pgvector endast för ostrukturerade runbooks. Metrics, logs och traces nås genom typade domänverktyg.
- Strukturerade JSON-loggar och OpenTelemetry.
- En portabel evalharness som fungerar lokalt och i CI.
- Cloud Run med hemligheter på serversidan. GitHub Actions/OIDC läggs till först när den manuella deployvägen är stabil.

Den implementerade körgränsen är högst två collection-rundor, tre modellanrop, åtta tool calls totalt, två anrop per verktygstyp, högst tre tool calls per runda och 45 sekunders hard timeout. Första collection får högst 28 sekunder, en eventuell andra runda högst 8 sekunder och synthesis reserveras tid. Den sista synthesis-rundan saknar tools och följs endast av deterministisk verifiering. Modellen kan svara `insufficient_evidence`.

## Medvetna avgränsningar

Följande ingår inte i sprinten:

- riktiga företagsloggar eller kunddata,
- riktiga driftintegrationer eller automatisk remediation,
- användarkonton och autentisering,
- Kubernetes, Terraform som förutsättning eller en egen observabilityplattform,
- multi-agent, MCP eller fine-tuning,
- Slack-, PagerDuty-, Datadog- eller Grafana-integration,
- en generell chatbot eller generell vector search över telemetri,
- fake users, fake customers, fake savings eller påhittade kvalitetsmått.

## Ägarskap och publicering

Projektgrunden är publicerad i [Sh1re-a/incident-detective-ai](https://github.com/Sh1re-a/incident-detective-ai) som ett icke-kommersiellt portfolio- och utbildningsprojekt. Ingen open-source-licens har lagts till. Att koden är synlig på GitHub betyder inte att en live-demo är deployad; en framtida Cloud Run-deploy är ett separat steg.
