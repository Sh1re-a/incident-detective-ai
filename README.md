# Incident Detective

Incident Detective är mitt individuella Passion Project under SALT Sprint 3, 25 augusti–18 september 2026.

> **Slutmål:** “Simulated incident — real AI investigation.”
>
> **Nuvarande läge:** Två deterministiska syntetiska incidenter kan köras genom ett lokalt Java/Spring Boot-API som recorded replay. Verifiering, evidence IDs, dolt facit, lokal Swagger-dokumentation och det första typade read-only-verktyget `get_metrics` fungerar. Frontend och live-AI är ännu inte byggda.

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
| **Verkligt i slutprodukten** | Modellens verktygsval, tool calling, structured output, runbook-retrieval, deterministisk verifiering, evals, tracing, uppmätt latens, tokenanvändning, kostnadsestimat och Cloud Run-deploy. |
| **Verkligt just nu** | Java-domänkontrakt, två syntetiska scenario-paket, recorded tool events, separat dolt `GroundTruth`, deterministisk verifiering, ett lokalt Spring Boot replay-API och ett typat, scenarioavgränsat `get_metrics` med versionshanterat argumentschema. |
| **Planerat, inte verifierat** | Leverantörsval, API-åtkomst och billing, exakt modell, live-AI-flöde, mätvärden, evalutfall, Cloud Run och användartester. Inga resultat får anges som uppnådda före faktisk mätning. |

När en sparad körning visas ska den märkas **“Simulated incident — recorded deterministic replay.”** Den får aldrig ha en “Live AI”-badge. Slutmålets live-märkning används först när ett verkligt modellanrop kör utredningen.

## Status 25 augusti 2026

- Sprintplan, prioriterad backlog, tekniska beslut och dag‑1‑kontrakt finns. Kontraktsgranskningen pågår fortfarande.
- `Scenario`, `Evidence`, `Diagnosis`, separat `GroundTruth` och tre oberoende verifieringsresultat är implementerade i Java.
- Två seedade scenarier finns: ett payment-timeoutfall och ett inventory-kontraktsfall. Båda innehåller metrics, logs, trace, runbook och avsiktligt brus.
- Ett lokalt recorded-replay-API returnerar ordnade tool events, endast faktiskt sedd evidens, diagnos, verifieringsrapport och en begränsad facitjämförelse efter avslutad körning.
- En separat projektion ger framtida live-tools tillgång till scenario och evidens utan recorded diagnosis eller `GroundTruth`. Det första verktyget, `get_metrics`, validerar argument, visar okända mätvärden och begränsar svarsstorleken deterministiskt.
- Maven-paketet och 78 tester är gröna. Den paketerade JAR-filen har startats lokalt; ingen frontend, databas, AI-integration, evalharness eller deploy är byggd.
- Ingen API-åtkomst, billing, modellprestanda, latency, kostnad eller accuracy har verifierats.
- Projektgrunden är publicerad på GitHub utan open-source-licens. Ingen demo är deployad.

## Lokalt recorded-replay-API

Starta backend med `./mvnw spring-boot:run`. De två nuvarande körningarna startas med `POST`:

- `/api/v1/scenarios/checkout-orders-at-risk-v1/runs/recorded-replay`
- `/api/v1/scenarios/checkout-cart-segment-failures-v1/runs/recorded-replay`

Svaret märks alltid **“Simulated incident — recorded deterministic replay.”** Modell-ID, promptversion, tokenanvändning och kostnad är `null`, eftersom ingen modell körs i replay-läget.

### Swagger och OpenAPI lokalt

När backend kör på port 8080 kan API:t läsas och provas här:

- [Swagger UI](http://localhost:8080/swagger-ui.html)
- [OpenAPI JSON](http://localhost:8080/v3/api-docs)

Dokumentationen gäller bara det nuvarande recorded-replay-API:t. Den visar därför endast `recorded_replay`, syntetisk incidentdata och manuellt godkända nästa steg. Det finns ingen live-AI-endpoint och ingen automatisk remediation. Inför en framtida Cloud Run-deploy ska det beslutas uttryckligen om Swagger ska vara publik eller avstängd.

### Lokal Gemini-nyckel

Kopiera `local-secrets.properties.example` till `local-secrets.properties` och lägg nyckeln efter `GEMINI_API_KEY=`. Den lokala filen ignoreras av Git, ligger utanför `src` och paketeras inte i applikationens JAR. En miljövariabel med samma namn fungerar också och används senare för deployment. Recorded replay fungerar utan nyckel; riktiga modellanrop ska vara uttryckligen aktiverade.

## Projektdokument

- [GitHub-repo](https://github.com/Sh1re-a/incident-detective-ai)
- [Sprintplan](./SPRINTPLAN.md)
- [Prioriterad backlog](./BACKLOG.md)
- [Tekniska beslut](./docs/DECISIONS.md)
- [Dag‑1‑kontrakt för granskning](./docs/DAY-1-CONTRACTS.md)
- [Lärspår och verktyg](./docs/LEARNING-PATH.md)

## Teknisk riktning

Den planerade lösningen är ett monorepo med en deploybar container:

- React, TypeScript och Vite för gränssnittet.
- Java 21, Spring Boot 4.1.1, Spring MVC, Jakarta Validation, Jackson och springdoc OpenAPI för API, validering och lokal Swagger-dokumentation.
- En modellleverantör med custom function tools och structured output. `gemini-3.7-flash` är nuvarande kandidat för en kostnadsfri lokal smoke-körning; åtkomst och kontrakt är ännu inte verifierade. En publik Gemini-demo i Sverige/EES kräver billing enligt Googles villkor. OpenAI Responses API behålls bara som ett möjligt alternativ tills leverantörsvalet är låst.
- En explicit och begränsad `COLLECT → SYNTHESIZE → VERIFY`-process.
- PostgreSQL och pgvector endast för ostrukturerade runbooks. Metrics, logs och traces nås genom typade domänverktyg.
- Strukturerade JSON-loggar och OpenTelemetry.
- En portabel evalharness som fungerar lokalt och i CI.
- Cloud Run med hemligheter på serversidan. GitHub Actions/OIDC läggs till först när den manuella deployvägen är stabil.

Den planerade körgränsen är högst tre collection-rundor, fyra modellanrop, åtta tool calls totalt, två anrop per verktygstyp, tre parallella read-only-anrop och 45 sekunders hard timeout. Den sista synthesis-rundan saknar tools och följs endast av deterministisk verifiering. Modellen ska kunna svara `insufficient_evidence`.

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
