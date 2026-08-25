# Lärspår och verktyg

- **Status:** Java/Spring Boot-grunden är verifierad; dag‑1‑granskning pågår
- **Senast verifierad:** 25 augusti 2026

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
| Vecka 2 | Officiella OpenAI Java SDK och Responses API | Verklig function calling och structured output. |
| Vecka 2 | PostgreSQL och pgvector | Retrieval endast över 10–15 runbooks. |
| Vecka 2–3 | Strukturerade JSON-loggar och OpenTelemetry | Förklara körningar, fel och latency. |
| När backendkontraktet är stabilt | React, TypeScript och Vite | Story View och Engineering View. |
| Vecka 4 | Docker och Google Cloud CLI | Containerkontroll och separat godkänd Cloud Run-deploy. |

Vi använder inte LangChain/LangGraph, multi-agent, MCP eller Assistants API i sprintens kärna. Flödet är den egna, begränsade processen `COLLECT → SYNTHESIZE → VERIFY`.

## Verifierat på datorn

- Java 21.0.10 LTS, `javac` 21.0.10 och Maven 3.9.12 finns.
- Projektet använder Spring Boot 4.1.1 och Maven Wrapper 3.3.4 med Maven 3.9.16.
- IntelliJ IDEA 2025.3.3, Docker och Google Cloud CLI finns.
- Det första Spring Boot-starttestet är grönt.
- Ingen `OPENAI_API_KEY` finns i den aktuella terminalmiljön och ingen lokal `.env` har skapats. API-åtkomst, billing och exakt modell är därför fortfarande **inte verifierade**.
- PostgreSQL-klienten finns inte globalt. Det blockerar inte dag 1 och installeras inte innan retrievalsteget behöver den.

En framtida API-nyckel ska bara finnas lokalt eller som en server-side secret. Värdet får aldrig skrivas i repo, dokumentation, frontendkod eller loggar.

## Det jag läser först

### Pass 1 – förstå Spring Boot-grunden

Läs i denna ordning:

1. [Spring Boot: Developing your first Spring Boot application](https://docs.spring.io/spring-boot/tutorial/first-application/index.html)
2. [Spring: Building a RESTful Web Service](https://spring.io/guides/gs/rest-service)
3. [Java: Records](https://dev.java/learn/records/)
4. [Spring Framework: Jakarta Bean Validation](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html)
5. [Spring: Testing the Web Layer](https://spring.io/guides/gs/testing-web)

Övning: öppna startklassen, `pom.xml` och starttestet i IntelliJ. Kör testet därifrån och förklara vad `@SpringBootApplication` och `@SpringBootTest` gör. Ingen incidentfunktion behöver byggas i detta pass.

### Pass 2 – modellera kontrakten

Efter att dag‑1‑kontraktet är godkänt bygger vi `Scenario`, `Evidence`, `Diagnosis` och `GroundTruth` som små Java-typer. Vi använder records där datan är oföränderlig, enums för kanoniska koder och vanliga domänmetoder för regler som beror på flera fält. Jakarta Validation används vid API-gränsen, men ersätter inte domänreglerna.

Byggresultat: tester bevisar bland annat att facit inte serialiseras till en publik respons, att okända evidence IDs underkänns och att `insufficient_evidence` inte kan innehålla en påhittad rotorsak.

### Pass 3 – Responses API och function calling i Java

Läs:

1. [OpenAI Java: Responses API reference](https://developers.openai.com/api/reference/java/resources/responses)
2. [OpenAI: Function calling](https://developers.openai.com/api/docs/guides/function-calling)
3. [OpenAI: Migrate to the Responses API](https://developers.openai.com/api/docs/guides/migrate-to-responses)
4. [OpenAI: Structured model outputs](https://developers.openai.com/api/docs/guides/structured-outputs)

Byggresultat: ett read-only tool i taget, först mot deterministiska fixtures. Exakt SDK-version och modell väljs först när API-åtkomst och billing är verifierade. Modellsvaret valideras alltid igen på serversidan.

### Pass 4 – evals och verifiering

Läs [OpenAI: Evaluation best practices](https://developers.openai.com/api/docs/guides/evaluation-best-practices).

Byggresultat: en portabel JUnit-/kommandoradsbaserad evalharness över versionshanterade fixtures och egna deterministiska scorers. Citation validity, evidence precision och diagnosis correctness hålls som tre olika mått.

### Pass 5 – runbook-retrieval

Läs:

1. [pgvector: officiellt projekt](https://github.com/pgvector/pgvector)
2. [pgvector-java](https://github.com/pgvector/pgvector-java)
3. [OpenAI: Vector embeddings](https://developers.openai.com/api/docs/guides/embeddings)

Byggresultat: sökning i kuraterade runbooks med dokument-, chunk- och versionsmetadata. Metrics, logs och traces läggs inte i vector store.

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

## Nuvarande minsta kodgrund

```text
pom.xml
mvnw
mvnw.cmd
.mvn/wrapper/maven-wrapper.properties
src/main/java/dev/shirwac/incidentdetective/
  IncidentDetectiveApplication.java
src/main/resources/
  application.properties
src/test/java/dev/shirwac/incidentdetective/
  IncidentDetectiveApplicationTests.java
```

Detta är bara en verifierad verktygsgrund. Ingen controller, domänmodell, databas, React-app eller modellintegration finns ännu. Efter kontraktsgodkännande är första produktslicen Java-typer och deterministiska verifierartester, följt av en liten recorded-replay-endpoint.

## Två föreslagna startscenarier

Båda ligger i samma syntetiska webbshop och använder tjänstekoderna `STOREFRONT`, `CHECKOUT_API`, `PAYMENT_ADAPTER`, `INVENTORY_SERVICE` och `ORDER_SERVICE`.

| Scenario | Det besökaren ser först | Dolt facit | Evidensmix |
|---|---|---|---|
| A: `checkout-payment-timeout-v1` | Checkoutfel ökar direkt efter en release och betalningssteget blir långsamt. | `root_cause_code = PAYMENT_TIMEOUT_CONFIG`, `affected_service = PAYMENT_ADAPTER` | Metric, log, trace och runbook om timeoutkonfiguration. |
| B: `checkout-inventory-contract-v1` | En del varukorgar börjar nekas efter en release av lagertjänsten. | `root_cause_code = INVENTORY_SCHEMA_MISMATCH`, `affected_service = INVENTORY_SERVICE` | Metric, valideringslogg, trace och runbook om API-kontrakt. |

`affected_service` betyder den primära tjänst där rotorsaken behöver rättas, inte alla tjänster där symptom syns. Systemet får rekommendera att jämföra eller återställa en ändring, men åtgärden kräver alltid mänskligt godkännande.

## Dag‑1‑gate

- [ ] Jag kan förklara skillnaden mellan `Scenario`, `Evidence`, `Diagnosis` och `GroundTruth`.
- [ ] Jag kan förklara varför endast sedd evidens får citeras.
- [ ] Jag har godkänt preciseringarna i dag‑1‑kontraktet.
- [ ] Två första scenariofacit och deras kanoniska koder är låsta.
- [ ] Först därefter skapas incident- och AI-kod.
