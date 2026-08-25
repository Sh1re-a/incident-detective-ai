# Incident Detective

Incident Detective är mitt individuella Passion Project under SALT Sprint 3, 25 augusti–18 september 2026.

> **Slutmål:** “Simulated incident — real AI investigation.”
>
> **Nuvarande läge:** Planering och kontraktsgranskning. En minimal Java/Spring Boot-grund är skapad och testad, men ingen incidentfunktion, replay eller live-AI-utredning är byggd ännu.

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
| **Verkligt just nu** | Projekt-, scope-, beslut- och kontraktsdokumentationen samt en körbar Java 21/Spring Boot-grund med ett godkänt starttest. |
| **Planerat, inte verifierat** | API-åtkomst och billing, exakt modell, live-AI-flöde, mätvärden, evalutfall, Cloud Run och användartester. Inga resultat får anges som uppnådda före faktisk mätning. |

När en sparad körning visas ska den märkas **“Simulated incident — recorded deterministic replay.”** Den får aldrig ha en “Live AI”-badge. Slutmålets live-märkning används först när ett verkligt modellanrop kör utredningen.

## Status 25 augusti 2026

- Sprintplan, prioriterad backlog, tekniska beslut och dag‑1‑kontrakt är framtagna för granskning.
- En minimal Java 21/Spring Boot 4.1.1-grund med Maven Wrapper och ett starttest finns. Ingen domänmodell, endpoint, frontend, databas, AI-integration, evalharness eller deploy har byggts.
- Ingen API-åtkomst, billing, modellprestanda, latency, kostnad eller accuracy har verifierats.
- Projektgrunden är publicerad på GitHub utan open-source-licens. Ingen demo är deployad.
- Incident- och AI-kod startar först efter att sprintplan, scope och dag‑1‑kontrakt har granskats.

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
- Java 21, Spring Boot 4.1.1, Spring MVC, Jakarta Validation och Jackson för API och validering.
- OpenAI Responses API med custom function tools och strict structured output. Aktuell officiell dokumentation ska kontrolleras före implementation; exakta modell- och SDK-versioner är ännu inte valda.
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
