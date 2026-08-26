# Incident Detective

Incident Detective är mitt individuella Passion Project under SALT Sprint 3, 25 augusti–18 september 2026.

> **Slutmål:** “Simulated incident — real AI investigation.”
>
> **Nuvarande läge:** En lokal, funktionell React-demo har Story View och Engineering View för två syntetiska incidenter. Recorded replay är gratis standardläge. Ett separat, uttryckligen bekräftat liveflöde använder Gemini, fyra typade read-only tools, structured output och Java-verifiering mot dolt facit. PostgreSQL/pgvector, Gemini-embeddings och en versionshanterad retrieval-eval är nu byggda. Development Hit@4 blev 5/5 och held-out 4/5; misslyckandet är publicerat. Container, full 18-falls-eval, OpenTelemetry och deployment är ännu inte byggda.

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
| **Verkligt just nu** | React Story/Engineering View, riktig frontend–API-kommunikation, klickbar tool-returnerad evidens, Java-domänkontrakt, två scenario-paket, fyra typade read-only tools, separata replay/live-endpoints, riktiga Gemini-anrop, function calling, structured diagnosis, dold `GroundTruth`, deterministisk verifiering och uppmätt körmetadata. |
| **Delvis verkligt** | RAG-profilen använder en global syntetisk runbookkorpus, `gemini-embedding-2`, 768 dimensioner och exakt pgvector cosine-sökning. En riktig retrieval-eval finns, men held-out-resultatet är 4/5 och en osäker legacy-runbook blev top-1 i det missade fallet. Full diagnos-eval och prompt-injection-säkerhet är inte verifierade. |
| **Planerat, inte verifierat** | 18 fulla evalfall, diagnosis accuracy/p95, OpenTelemetry, en deploybar container, Cloud Run och användartester. Kontots framtida billingläge är inte verifierat. Inga mål får anges som uppnådda före faktisk mätning. |

När en sparad körning visas ska den märkas **“Simulated incident — recorded deterministic replay.”** Den får aldrig ha en “Live AI”-badge. Slutmålets live-märkning används först när ett verkligt modellanrop kör utredningen.

## AI-engineering-beviset

| Del | Nuläge | Vad som ska bli verifierbart |
|---|---|---|
| Tool calling och orchestrering | Verkligt och testat | Gemini väljer bland fyra strikt typade read-only tools i en begränsad state machine. |
| Structured output och verifiering | Verkligt och testat | Schema, Java-regler, citation validity, evidence support och dolt facit kontrolleras separat. |
| RAG | Byggt och mätt, målet är inte stabilt uppnått | 10 dokument/12 chunks, explicit idempotent import, Gemini embeddings, exakt pgvector cosine, dev-kalibrerad tröskel och rank/similarity/hash i Engineering View. Positiv Hit@4 blev totalt 9/10 men held-out 4/5. |
| Evals och säkerhetsfall | Retrieval-slicen är byggd | 14 retrievalfall ger JSON/Markdown, development/held-out, no-match och ett faktiskt hämtat adversarial runbookfall. 18 fulla diagnosfall, baseline och synthesis-säkerhet återstår. |
| Observability | Inte byggt | Sanerade JSON-loggar och OpenTelemetry-spans från API till tool, synthesis och verifiering. |
| Leverans | GitHub och lokal demo verifierade | Reproducerbar container och CI först, sedan separat godkänd Cloud Run-deploy med server-side secrets och kostnadsgränser. |

Det som saknas är alltså inte fler visuella features. Nästa tekniska slice är fulla diagnos-evals och det separata synthesis-testet för den adversarial runbook som retrieval-evalen faktiskt hämtade.

## Status 26 augusti 2026

- Sprintplan, prioriterad backlog, tekniska beslut och dag‑1‑kontrakt finns.
- `Scenario`, `Evidence`, `Diagnosis`, separat `GroundTruth` och tre oberoende verifieringsresultat är implementerade i Java.
- Två seedade scenarier finns: ett payment-timeoutfall och ett inventory-kontraktsfall. Båda innehåller metrics, logs, trace, runbook och avsiktligt brus.
- Frontendens scenario-väljare läser säkra sammanfattningar från `GET /api/v1/scenarios`. Den får inte facit, recorded diagnosis eller ett dolt evidence inventory.
- Story View visar affärspåverkan, en ärligt märkt replay/live-körning, säker tool-tidslinje, klickbar evidens, diagnos och manuellt nästa steg. Engineering View visar state machine, tool events, verifiering, modell/prompt, latency, tokens, calls, kostnadsgrund och limitations.
- Livefel ersätts aldrig tyst av replay. Besökaren får själv välja den kostnadsfria recorded-körningen och får då ett nytt replay-resultat med korrekt truth label.
- RAG-profilen använder en Docker-baserad PostgreSQL 17/pgvector 0.8.6-databas. Den versionshanterade korpusen innehåller 10 syntetiska dokument och 12 chunks. Importen är explicit och idempotent; vanlig start gör inga embedding-anrop.
- Retrieval-eval v1 kör 10 positiva frågor, tre no-match-frågor och ett adversarial fall. Tröskeln `0.6620781500197453` valdes endast på development och frystes före held-out. Development gav 5/5 Hit@4 och 2/2 no-match. Held-out gav 4/5 Hit@4 och 1/1 no-match. Den missade kontraktsfrågan rankade den osäkra legacy-runbooken först, vilket är en dokumenterad failure case och inte bortfiltrerad i efterhand.
- Gemini-SDK:n rapporterade varken provider-tokens eller billable characters för embedding-anropen. Rapporten visar därför 2 051 lokalt uppmätta input-tecken och 4 681 ms summerad provider-latency, men lämnar provider-usage och kostnad som `null` i stället för att visa falska nollor.
- Ett lokalt recorded-replay-API returnerar ordnade tool events, endast faktiskt sedd evidens, diagnos, verifieringsrapport och en begränsad facitjämförelse efter avslutad körning.
- Liveflödet kör `COLLECT → SYNTHESIZE → VERIFY` med `get_metrics`, `search_logs`, `get_trace` och `retrieve_runbooks`. Modellen ser bara scenario och tool-returnerad evidens; verifieraren öppnar facit först efter sista modellanropet.
- Standardprofilen är `gemini-3.5-flash-lite` med `MINIMAL` thinking och det versionsmärkta kontraktet `gemini-live-v5`. Live måste både vara aktiverat på servern och bekräftas i varje request.
- Backend tillåter högst en pågående liveutredning och fem starter per rullande tio minuter per applikationsinstans. Över gränsen returneras ett sanerat `429`-svar med `Retry-After`; recorded replay påverkas inte. En framtida Cloud Run-konfiguration måste begränsa antalet instanser för att göra detta till en meningsfull global kostnadsgräns.
- Prompt v5 infördes efter att en ny v4-regressionskörning hittade rätt inventory-rotorsak men bara gav 3/5 direkt stödda claim-evidence-länkar. Verifieraren lättades inte; prompten skärptes så att trigger, tjänst och felmekanism kräver direkt stöd.
- En efterföljande v5-körning genom det riktiga UI:t gav rätt inventory-rotorsak och tjänst, 100 procent giltiga citation IDs och 5/5 direkt stödda länkar. Den tog 4,96 sekunder, använde 3 modellanrop, 4 tool calls och 6 502 tokens och hade cirka 0,0040 USD i betalt listprisestimat. Faktisk free-tier-debitering kan vara 0 USD.
- Dessa enskilda live-smokes är **inte** ett accuracy- eller p95-resultat. Historiken innehåller 40–100 procents evidence precision och provider-timeouts. En livekörning som inte väljer `retrieve_runbooks` säger inte heller något om retrievalkvaliteten.
- En lokal live-smoke den 26 augusti för payment-timeoutscenariot gav rätt rotorsak/tjänst, 100 procent evidence precision och giltiga citationer på 5,48 sekunder med 3 modellanrop, 4 tool calls och 6 682 tokens. Modellen valde inte runbookverktyget i den körningen. Ett efterföljande inventory-scenario returnerade kontrollerat `504 MODEL_PROVIDER_TIMEOUT` och är därför inte verifierat live i denna omgång.
- Retrieval är separat verifierad genom evalharnessen; en lyckad retrieval-eval är inte samma sak som en lyckad full AI-utredning.
- Backendens vanliga testsuite har 184 gröna tester. Dessutom passerar tre databas-/pgvector-integrationstester; ett separat nätverksbaserat Gemini-smoketest är medvetet avstängt i den automatiska sviten. Frontend har 10 gröna beteendetester och en verifierad produktionsbuild.
- Projektgrunden är publicerad på GitHub utan open-source-licens. Ingen demo är deployad.

## Kör den interaktiva demon lokalt

Starta backend i projektroten:

```bash
./mvnw spring-boot:run
```

Starta sedan frontend i en andra terminal:

```bash
cd frontend
npm install
npm run dev
```

Öppna [Incident Detective](http://127.0.0.1:5173/). Vite skickar lokala `/api`-anrop vidare till Spring Boot på port 8080, så projektet behöver ingen öppen wildcard-CORS-policy.

Recorded replay fungerar utan modellnyckel. Live AI kräver den lokala serverkonfigurationen som beskrivs längre ned och en ny bekräftelse i gränssnittet för varje körning.

Vanliga lokala kontroller:

```bash
./mvnw test
npm --prefix frontend test
npm --prefix frontend run build
```

### Lokal RAG och retrieval-eval

Starta pgvector och importera korpusen uttryckligen:

```bash
docker compose up -d
./mvnw -q spring-boot:run -Dspring-boot.run.arguments=--import-runbooks
```

Starta sedan backend med RAG-profilen:

```bash
SPRING_PROFILES_ACTIVE=rag INCIDENT_DETECTIVE_LIVE_AI_ENABLED=true ./mvnw spring-boot:run
```

Retrieval-evalen är också explicit eftersom den gör riktiga embedding-anrop. Ange den commit som ska mätas i `INCIDENT_DETECTIVE_GIT_SHA` och kör:

```bash
INCIDENT_DETECTIVE_GIT_SHA=<git-sha> ./mvnw -q spring-boot:run -Dspring-boot.run.arguments=--evaluate-runbook-retrieval
```

Kommandot skapar rå JSON och en kort Markdownrapport i `target/evals/`. Den publicerade mätningen finns under `docs/evals/`. Vanliga `./mvnw test` är fortfarande databas- och providerfria; pgvector-integrationstester körs uttryckligen med `./mvnw -Pdatabase-it verify`.

## Lokalt API

Den säkra scenario-listan finns på `GET /api/v1/scenarios`. De två nuvarande replay-körningarna startas med `POST`:

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

Live-anrop begränsas lokalt till en samtidig körning och fem starter per rullande tio minuter per backendinstans. Klienten gör inga automatiska live-retries. Det är ett första kostnads- och överbelastningsskydd, inte ett komplett publikt missbruksskydd; Cloud Run-gränser och budgetlarm återstår före deployment.

### Swagger och OpenAPI lokalt

När backend kör på port 8080 kan API:t läsas och provas här:

- [Swagger UI](http://localhost:8080/swagger-ui.html)
- [OpenAPI JSON](http://localhost:8080/v3/api-docs)

Dokumentationen visar både `recorded_replay` och `live_ai`, deras olika truth labels, explicita livebekräftelse och manuellt godkända nästa steg. Inför en framtida Cloud Run-deploy ska det beslutas uttryckligen om Swagger ska vara publik eller avstängd.

För en enkel genomgång av vad endpointsen returnerar, varför de finns och hur flödet kan presenteras, se [API- och presentationsguiden](./docs/API-WALKTHROUGH.md).

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
- [AI-systemkort och riskregister](./docs/AI-SYSTEM-CARD.md)
- [Runbook retrieval-eval – läsbar rapport](./docs/evals/runbook-retrieval-eval-v1.md)
- [Runbook retrieval-eval – rå JSON](./docs/evals/runbook-retrieval-eval-v1.json)
- [API- och presentationsguide](./docs/API-WALKTHROUGH.md)
- [Första live-smoke och observerade failure cases](./docs/LIVE-SMOKE-2026-08-25.md)
- [Dag‑1‑kontrakt för granskning](./docs/DAY-1-CONTRACTS.md)
- [Lärspår och verktyg](./docs/LEARNING-PATH.md)

## Teknisk riktning

Den planerade lösningen är ett monorepo med en deploybar container:

- React 19, TypeScript och Vite för det implementerade gränssnittet.
- Java 21, Spring Boot 4.1.1, Spring MVC, Jakarta Validation, Jackson och springdoc OpenAPI för API, validering och lokal Swagger-dokumentation.
- En modellleverantör med custom function tools och structured output. Nuvarande mätta standardprofil är `gemini-3.5-flash-lite` + `MINIMAL` thinking genom Googles officiella Java SDK. Modellen stöder [function calling och structured outputs](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite); prisestimat utgår från [Googles betalda standardlistpris](https://ai.google.dev/gemini-api/docs/pricing), medan faktisk free-tier-kostnad kan vara noll.
- En explicit och begränsad `COLLECT → SYNTHESIZE → VERIFY`-process.
- PostgreSQL och pgvector endast för ostrukturerade runbooks. Metrics, logs och traces nås genom typade domänverktyg.
- Strukturerade JSON-loggar och OpenTelemetry är planerade, inte implementerade.
- En portabel retrieval-evalharness fungerar lokalt; den fulla 18-falls-evalen och CI-körningen återstår.
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
