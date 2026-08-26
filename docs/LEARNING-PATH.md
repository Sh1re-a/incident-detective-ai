# Lärspår och verktyg

- **Status:** Story/Engineering View, replay/live-API, fyra typade read-only tools och riktiga Gemini-körningar är verifierade
- **Senast verifierad:** 26 augusti 2026

Målet är inte att läsa allt innan jag bygger. Jag följer samma korta loop:

1. läs ett avgränsat officiellt avsnitt,
2. bygg en liten del,
3. skriv ett test som visar regeln,
4. förklara med egna ord vad som händer och varför.

## Vald grund

Backend byggs med **Java 21, Spring Boot 4.1.1 och Maven**. Det passar projektet eftersom jag vill bli bättre på Spring Boot och redan kan Java. Jag behöver därför inte lära mig ett nytt backend-språk samtidigt som jag lär mig tool calling, structured output och evals.

Maven används i stället för Gradle i sprinten. Det finns redan på datorn, fungerar direkt i IntelliJ och räcker för ett litet monorepo. Byggkommandot går genom Maven Wrapper så att samma Maven-version kan användas lokalt och i CI.

| När | Verktyg | Varför |
|---|---|---|
| Nu | Java 21 | Backend, domänlogik, verifiering och evalharness. |
| Nu | Spring Boot 4.1.1 och Spring MVC | Ett tydligt API-lager utan att dela upp projektet i flera backendtjänster. |
| Nu | Maven Wrapper | Reproducerbara bygg- och testkommandon. |
| Nu | Java records, Jakarta Validation och Jackson | Typade kontrakt, JSON och tydliga valideringsfel. |
| Nu | JUnit och Spring Boot Test | Deterministiska tester av regler och applikationsstart. |
| Nu | Google Gen AI SDK för Java 1.67.0 | Verklig function calling och structured output bakom en liten intern gateway. |
| Nu | PostgreSQL och pgvector | Retrieval endast över den fristående syntetiska runbookkorpusen. |
| Vecka 2–3 | Strukturerade JSON-loggar och OpenTelemetry | Förklara körningar, fel och latency. |
| Nu | React 19, TypeScript och Vite | Story View, Engineering View och beteendetester mot API-kontraktet. |
| Vecka 4 | Docker och Google Cloud CLI | Containerkontroll och separat godkänd Cloud Run-deploy. |

Vi använder inte LangChain/LangGraph, multi-agent, MCP eller Assistants API i sprintens kärna. Flödet är den egna, begränsade processen `COLLECT → SYNTHESIZE → VERIFY`.

## Verifierat på datorn

- Java 21.0.10 LTS, `javac` 21.0.10 och Maven 3.9.12 finns.
- Projektet använder Spring Boot 4.1.1 och Maven Wrapper 3.3.4 med Maven 3.9.16.
- IntelliJ IDEA 2025.3.3, Google Cloud CLI och Docker finns. Den lokala PostgreSQL/pgvector-containern var healthy vid kontrollen 26 augusti.
- Hela den nätverksfria Maven-testsuiten har 198 gröna tester. Tre pgvector-integrationstester passerar separat. Frontend har 15 gröna beteendetester och en godkänd produktionsbuild.
- Gemini API-åtkomst är verifierad genom riktiga opt-in-anrop. Standardprofilen är `gemini-3.1-flash-lite` med `MINIMAL` thinking och `gemini-live-v6`.
- De senaste två v6-smokesen i RAG-profilen gav rätt diagnos och giltiga citationer på 6,06 respektive 5,51 sekunder. Payment-körningen valde riktig pgvector-retrieval; inventory-körningen valde en trace. Historiken innehåller sämre evidensprecision och provider-timeouts; accuracy, p95 och stabilitet är därför fortfarande **inte verifierade**.
- Livevägen har ett första kostnadsskydd: en samtidig körning och fem starter per rullande tio minuter per backendinstans. Cloud Run-instansgräns och budgetlarm är ännu inte konfigurerade.
- Korpusen innehåller 10 dokument/12 chunks. Retrieval v1 gav development 5/5 och held-out 4/5 Hit@4; den missade frågan behålls som failure case.

API-nyckeln finns endast i en Git-ignorerad lokal fil och ska senare ligga som server-side secret. Värdet får aldrig skrivas i repo, dokumentation, frontendkod eller loggar.

## Lärordning och nästa fokus

### Pass 1 – förstå Spring Boot-grunden (genomfört, använd som referens)

Läs i denna ordning:

1. [Spring Boot: Developing your first Spring Boot application](https://docs.spring.io/spring-boot/tutorial/first-application/index.html)
2. [Spring: Building a RESTful Web Service](https://spring.io/guides/gs/rest-service)
3. [Java: Records](https://dev.java/learn/records/)
4. [Spring Framework: Jakarta Bean Validation](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html)
5. [Spring: Testing the Web Layer](https://spring.io/guides/gs/testing-web)

Övning: öppna startklassen, `pom.xml` och starttestet i IntelliJ. Kör testet därifrån och förklara vad `@SpringBootApplication` och `@SpringBootTest` gör. Ingen incidentfunktion behöver byggas i detta pass.

### Pass 2 – modellera kontrakten (genomfört)

`Scenario`, `Evidence`, `Diagnosis` och `GroundTruth` är implementerade som små Java-typer med validering och tester. Records används där datan är oföränderlig, enums för kanoniska koder och domänmetoder för regler som beror på flera fält. Nästa övning är att kunna förklara gränserna och följa dem i live-runnern.

Byggresultat: tester bevisar bland annat att facit inte serialiseras till en publik respons, att okända evidence IDs underkänns och att `insufficient_evidence` inte kan innehålla en påhittad rotorsak.

### Pass 3 – function calling och structured output i Java (genomfört, förklara nu med egna ord)

Läs den officiella dokumentation som implementationen bygger på:

1. [Gemini API: Function calling](https://ai.google.dev/gemini-api/docs/function-calling)
2. [Gemini API: Structured outputs](https://ai.google.dev/gemini-api/docs/structured-output)
3. [Google Gen AI SDK för Java](https://github.com/googleapis/java-genai)

Byggresultat: alla fyra tools är typade och skrivskyddade. Gemini väljer tools i högst två collection-rundor, synthesis saknar tools och svaret valideras mot schema och Java-regler innan det jämförs med dolt facit. Nästa övning är att kunna följa en request från Swagger genom gateway, tool executor och verifierare.

### Pass 4 – runbook-retrieval

Läs:

1. [Gemini API: Embeddings](https://ai.google.dev/gemini-api/docs/embeddings)
2. [pgvector: officiellt projekt](https://github.com/pgvector/pgvector)
3. [pgvector-java](https://github.com/pgvector/pgvector-java)

Byggresultat: en fristående, versionshanterad korpus med 10–15 syntetiska runbooks. Med `gemini-embedding-2` formateras dokument som `title: … | text: …` och frågor som `task: search result | query: …`, utan separat `taskType`, och båda bäddas in i 768 dimensioner. PostgreSQL/pgvector gör exakt cosine-sökning och returnerar top-k ≤ 4 med dokument-, chunk-, versions-, rank- och similaritymetadata. Metrics, logs och traces läggs inte i vector store.

Övning: jämför recorded-fixturens deterministiska runbookresultat med `rag`-profilens provider-backed retrieval. Förklara varför exakt sökning räcker för en så liten korpus och varför embeddingmodell och korpusversion måste sparas.

### Pass 5 – evals och verifiering

Läs [OpenAI: Evaluation best practices](https://developers.openai.com/api/docs/guides/evaluation-best-practices). Guiden används för allmän evalmetod; själva harnessen är leverantörsoberoende och körs lokalt/CI.

Byggresultat: först 10 positiva retrievalfall och två no-result-fall för Hit@4, därefter en portabel JUnit-/kommandoradsbaserad evalharness över 18 versionshanterade incidentfall och egna deterministiska scorers. Citation validity, evidence precision, claim coverage, diagnosis correctness, abstention och retrieval hålls som separata mått. Ett adversarial runbook måste faktiskt hamna i topp 4 för att säkerhetstestet ska räknas.

### Pass 6 – logging och tracing

Läs:

1. [Cloud Run: Logging](https://docs.cloud.google.com/run/docs/logging)
2. [OpenTelemetry: Spring Boot starter](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/)

Byggresultat: strukturerade körhändelser för API, tools och verifiering utan hemligheter eller privat tankekedja.

### Pass 7 – container och Cloud Run

Läs:

1. [Cloud Run: Container runtime contract](https://docs.cloud.google.com/run/docs/container-contract)
2. [Cloud Run: Deploy container images](https://docs.cloud.google.com/run/docs/deploying)

Byggresultat: en lokalt testad container och därefter en separat godkänd deploy. Ett publikt GitHub-repo betyder inte att Cloud Run redan är klar.

## Nuvarande kodgrund

Nu finns React Story/Engineering View, säker scenario-lista, klickbar evidens, ärlig replay/live-fallback, Spring Boot replay/live-API, separat dold `GroundTruth`, deterministisk verifierare, två fixturepaket, Swagger/OpenAPI, fyra typade tools, Gemini-gateway, strict structured output och kostnadsestimat. En fristående runbookkorpus, PostgreSQL/pgvector, idempotent import och retrieval-eval är byggda. Full diagnos-eval, observability, applikationscontainer och deploy saknas fortfarande.

## Två implementerade startscenarier

Båda ligger i samma syntetiska webbshop och använder tjänstekoderna `STOREFRONT`, `CHECKOUT_API`, `PAYMENT_ADAPTER`, `INVENTORY_SERVICE` och `ORDER_SERVICE`.

| Scenario | Det besökaren ser först | Dolt facit | Evidensmix |
|---|---|---|---|
| A: `checkout-orders-at-risk-v1` | Checkoutfel ökar direkt efter en release och betalningssteget blir långsamt. | `root_cause_code = PAYMENT_TIMEOUT_CONFIG`, `affected_service = PAYMENT_ADAPTER` | Metric, log, trace och runbook om timeoutkonfiguration. |
| B: `checkout-cart-segment-failures-v1` | En del varukorgar börjar nekas efter en release av lagertjänsten. | `root_cause_code = INVENTORY_SCHEMA_MISMATCH`, `affected_service = INVENTORY_SERVICE` | Metric, valideringslogg, trace och runbook om API-kontrakt. |

`affected_service` betyder den primära tjänst där rotorsaken behöver rättas, inte alla tjänster där symptom syns. Systemet får rekommendera att jämföra eller återställa en ändring, men åtgärden kräver alltid mänskligt godkännande.

## Dag‑1‑gate

- [ ] Jag kan förklara skillnaden mellan `Scenario`, `Evidence`, `Diagnosis` och `GroundTruth`.
- [ ] Jag kan förklara varför endast sedd evidens får citeras.
- [ ] Jag har godkänt preciseringarna i dag‑1‑kontraktet.
- [ ] Två första scenariofacit och deras kanoniska koder är låsta.
- [ ] Jag kan förklara varför live kräver både serverflagga och explicit requestbekräftelse och hur nyckeln hålls utanför Git.
