# Prioriterad sprintbacklog

Backloggen är ordnad efter de fyra veckogatesen. **MUST** är sprintens kontrakt. **SHOULD** får endast starta när aktuell gate är passerad. **CUT** tas inte in under sprinten.

Statusvärden: `Klar`, `Pågår`, `Ej startad`, `Väntar på extern bekräftelse`.

## Omedelbar byggordning

1. Lås prompt v5 och Swagger med hela testsviten och en enda representativ live-smoke. **Klar 26 augusti.**
2. Bygg en fristående, versionshanterad runbookkorpus och riktig PostgreSQL/pgvector-retrieval. **Klar 26 augusti.**
3. Mät retrieval på 10 positiva frågor och minst två negativa frågor innan fler incidentfamiljer byggs. **Klar 26 augusti; held-out-missen är dokumenterad.**
4. Utöka nu till sex familjer och 18 fulla evalfall, inklusive abstention och ett separat synthesis-test med den adversarial runbook som redan hämtats.
5. Lägg på körpersistens, git SHA, JSON-loggar och OpenTelemetry när AI-flödet är mätbart.
6. Bygg CI och container före separat godkänd Cloud Run-deploy.

Denna ordning stoppar UI-polish, modelljämförelser och fler ramverk från att tränga undan de bevis som fortfarande saknas.

## Grundfas – före produktkod

| ID | Leverans | Acceptanskriterium | Status |
|---|---|---|---|
| M-00 | Projektgrund | README, sprintplan, backlog, beslut och dag‑1‑kontrakt finns; inga hemligheter eller open-source-licens | Klar |
| M-01 | Publik versionshantering | `main` finns i publika `Sh1re-a/incident-detective-ai` och URL/visibility är verifierad | Klar |
| M-02 | Scope- och kontraktsgranskning | Shirwac har granskat planen, MUST/SHOULD/CUT och dag‑1‑kontrakten innan implementation | Pågår |
| M-03 | Publik repo-status | GitHub-repot är publikt och det är dokumenterat att ingen open-source-licens eller deploy har lagts till | Klar |
| M-04 | Veckovisa check-ins | Framsteg, lärande, hinder, beslut och nästa prioriteringar dokumenteras 28 aug, 4 sep, 11 sep och vid finalen 18 sep | Ej startad |
| M-05 | Klartecken för extern deploy | Shirwac har separat och uttryckligen godkänt Cloud Run-deployen innan den genomförs | Väntar på extern bekräftelse |
| M-06 | Java-verktygskedja | Java 21, Spring Boot 4.1.1 och Maven Wrapper bygger projektet och starttestet är grönt | Klar |

## MUST – vecka 1, synlig vertikal slice

| ID | Leverans | Acceptanskriterium | Status |
|---|---|---|---|
| M-10 | Låst kärnberättelse | “Checkout errors threaten orders” har tydlig kundpåverkan, tidslinje och ett säkert nästa steg | Klar |
| M-11 | API- och billingcheck | Faktisk modellåtkomst är verifierad utan att token eller hemlighet hamnar i repo/logg; kontots framtida billingläge dokumenteras separat | Pågår – liveåtkomst verifierad, billingläge ej verifierat |
| M-12 | Domänkontrakt | `Scenario`, `Evidence`, `Diagnosis` och separat `GroundTruth` är implementerade och validerade | Klar |
| M-13 | Två scenariopaket | Två seedade, deterministiska paket har minst två evidenstyper, brus och dolt facit; ett integritetstest stoppar `GroundTruth` från publika scenario- och toolpayloads | Klar |
| M-14 | Backend och verifierare | Riktig frontend–API-kommunikation samt kontroll av schema, citation IDs och facit fungerar; trasiga modellsvar ger valideringsresultat utan HTTP 500 och `seenEvidenceIds` byggs endast från faktiska tool events | Klar |
| M-15 | Recorded Story View | Ett helt fall kan följas på högst 90 sekunder och märks “Simulated incident — recorded deterministic replay.” | Pågår – flödet är byggt och lokalt verifierat; tidsatt test med person återstår |
| M-16 | Första liveutredningen | Efter kontraktslås kör ett verkligt modellanrop och läget märks “Simulated incident — real AI investigation.” | Klar |

**Gate 28 augusti:** ett fall förstås på högst 90 sekunder, minst två evidenstyper visas, resultatet är strukturerat, evidence IDs är klickbara och giltiga och replay/live-märkningen är sann.

## MUST – vecka 2, AI-systemet feature complete

| ID | Leverans | Acceptanskriterium | Status |
|---|---|---|---|
| M-20 | Fyra typade read-only tools | `get_metrics`, `search_logs`, `get_trace` och `retrieve_runbooks` är scenarioavgränsade, skrivskyddade och testade | Klar |
| M-21 | Begränsad state machine | `COLLECT → SYNTHESIZE → VERIFY` har högst två collection-rundor, tre model/åtta tool calls, två per tooltyp, högst tre calls per runda, tool-free synthesis och 45 s timeout | Klar |
| M-22 | Structured diagnosis | Java-typer, Jakarta Validation och deterministiska domänregler validerar `diagnosed` eller `insufficient_evidence`, claims och evidence IDs | Klar |
| M-23 | Runbook retrieval | En fristående korpus med 10–15 syntetiska runbooks indexeras med `gemini-embedding-2` i 768 dimensioner; PostgreSQL/pgvector gör exakt cosine-sökning med top-k ≤ 4 och returnerar dokument, chunk, version, rank, similarity, embeddingmodell och backend | Klar – 10 dokument/12 chunks, explicit import, hash-readiness och ingen tyst keyword-fallback |
| M-24 | Sex incidentfamiljer | Sex rotorsaksfamiljer återanvänder samma syntetiska webbshop, tjänstekarta, checkout-berättelse och UI; minst tre är valbara i demon | Ej startad |
| M-25 | Story + Engineering View | Berättelse och tekniska detaljer kan växlas utan att privat chain-of-thought visas | Klar |
| M-26 | Körmetadata | Run ID, mode, model ID, promptversion, embeddingmodell, korpusversion, retrieval-backend, git SHA, latency, tokens, calls och kostnadsestimat sparas | Pågår – live/API visar retrievalmetadata; evalrapporten har git SHA; beständig runlagring och live-git-SHA återstår |
| M-27 | Grundobservability | Strukturerade JSON-loggar och OpenTelemetry täcker API → collect → tool → synthesize → verify utan nycklar, råa prompts eller privat chain-of-thought | Ej startad |

**Gate 4 september:** liveutredningen fungerar och Engineering View visar tools, retrieval, evidens och körmetadata.

## MUST – vecka 3, evals och feature freeze

| ID | Leverans | Acceptanskriterium | Status |
|---|---|---|---|
| M-30 | 18 evalfall | Sex familjer × tre variationer, med separata development- och held-out-fall | Ej startad |
| M-31 | Baseline | Symptom-only baseline och full utredning körs mot samma relevanta fall | Ej startad |
| M-32 | Tre separata verifieringar | Citation validity, evidence support/precision och diagnosis correctness mäts var för sig | Klar |
| M-33 | Retrievalmått | Minst 10 positiva retrievalfrågor mäter Hit@4 mot exakt dokument/chunk/version; två no-result-fall redovisas separat och målet ≥ 90 % betyder minst 9/10 | Klar för v1 – 9/10 totalt, men held-out endast 4/5; tre no-match gav 3/3 |
| M-34 | Säkerhets-/abstentionfall | Minst två fall kräver korrekt abstention; ett adversarial runbook hämtas faktiskt i topp 4 men får inte styra diagnos, bli incidentbevis eller kringgå mänskligt godkännande | Pågår – adversarial runbook hämtades rank 1; synthesis-säkerhet och fulla abstentionfall återstår |
| M-35 | Portabel evalrapport | Samma kommando skapar JSON- och Markdownrapport lokalt/CI med accuracy, schema, citations, retrieval, latency, calls, kostnad, datasetversion och git SHA; vanlig CI gör inga betalda liveanrop | Pågår – retrieval-slicen skapar JSON/Markdown med dataset, korpus, embeddingprofil, git SHA, latency och nullable usage; full diagnosrapport och CI återstår |
| M-36 | Failure cases | Alla verkligt observerade missar och kända begränsningar dokumenteras; inget minimiantal eller konstruerad miss krävs | Pågår – held-out retrieval-miss, unsafe top-1 och ny provider-timeout är dokumenterade |

**Gate 11 september:** evalrapporten är reproducerbar, publika claims har mätstöd och feature freeze börjar.

## MUST – vecka 4, release och presentation

| ID | Leverans | Acceptanskriterium | Status |
|---|---|---|---|
| M-40 | Deploybar container | En multi-stage-container bygger frontend/backend, kör som non-root, lyssnar på `$PORT`, har healthcheck och innehåller inga hemligheter | Ej startad |
| M-41 | Cloud Run | Godkänd deployment använder Secret Manager, låg max-instances/concurrency, persistent global livegräns och budgetlarm; larm beskrivs ärligt som varning och inte hårt kostnadstak | Ej startad – appens per-instansgräns är byggd; Cloud Run, globala gränser och budgetlarm återstår |
| M-42 | Ärlig replay-fallback | Livefel/timeout leder till tydligt märkt recorded replay, aldrig falsk Live AI-status | Pågår – lokalt UI och beteendetest är klara; deployad fallback återstår |
| M-43 | Tillgänglig kärnresa | Mobil, tangentbord, kontrast, textstatus och reduced motion är kontrollerade | Pågår – responsiv layout, tangentbord, textstatus och reduced motion är lokalt kontrollerade; extern test återstår |
| M-44 | Användartest | Om deltagare finns: tre tekniska och tre icke-tekniska tester dokumenteras utan fake users | Ej startad |
| M-45 | Slutdokumentation | README, arkitekturbild, limitations, evalrapport och faktisk mätmetod är reproducerbara | Ej startad |
| M-46 | Demoartefakter | 60–90 s video och max 10 min presentation visar värde, bevis, begränsningar och lärdomar | Ej startad |
| M-47 | Releasecheck | 20 smoke runs är genomförda, materiella fel är åtgärdade en gång och release tag är skapad | Ej startad |

**Gate 18 september:** godkänd Cloud Run-version, fungerande fallback, sanningsenliga resultat och presentation på högst tio minuter.

## Release targets – får bara publiceras efter mätning

| Mätetal | Mål | Nuläge |
|---|---:|---|
| Diagnosis accuracy | ≥ 15/18 | Ingen eval; senaste v5-smoken var korrekt men är bara ett fall |
| Schema pass | 100 % | Ingen eval; senaste v5-smoken passerade |
| Citation ID validity | 100 % | Ingen eval; senaste v5-smoken hade 100 % giltiga IDs |
| Evidence precision | ≥ 90 % | Ingen eval; senaste v5-smoken gav 5/5 efter att en v4-regression gav 3/5 |
| Retrieval Hit@4 | ≥ 90 % | v1 totalt 9/10 (90 %), men held-out 4/5 (80 %); behöver förbättras utan held-out-tuning |
| Korrekta abstentions | ≥ 2 | Ej mätt |
| Warm p95 | < 30 s | Ej mätt; senaste v5-smoken tog 4,96 s, men historiken innehåller långsammare körningar och timeouts |
| Hard timeout | 45 s | Implementerad och enhetstestad; provider-timeouts observerade |
| Model/tool calls | ≤ 4 / ≤ 8 | Senaste v5-smoken: 3 / 4 |
| Smoke runs | 20 stabila | 0/20 release-smokes; flera utvecklingssmokes behålls som historik |

## SHOULD – endast efter passerad gate

| ID | Leverans | När den får tas in |
|---|---|---|
| S-01 | Challenge mode | Efter feature freeze om eval- och releasearbete är i fas |
| S-02 | Svenska/engelska | Efter att hela kärnresan fungerar på ett språk |
| S-03 | Delbar resultatsida | Efter godkänd publiceringsmodell och stabil run metadata |
| S-04 | Modelljämförelse | Endast om evals visar ett konkret beslut som jämförelsen kan lösa |
| S-05 | Publik aggregerad evalvy | Efter att metod, datadelning och claims har granskats |
| S-06 | Finare animation | Efter mobil, tangentbord, kontrast och reduced motion |

## CUT – tas inte in under sprinten

- riktiga företagsloggar, kunddata eller produktionsintegrationer,
- riktig microservice-fleet eller fault injection,
- Kubernetes,
- användarkonton eller autentisering,
- Slack, PagerDuty, Datadog eller Grafana,
- automatisk remediation eller skrivande tools,
- multi-agent eller MCP,
- fine-tuning,
- Terraform om deployen inte redan är stabil,
- egen observabilityplattform,
- chatbot som huvudgränssnitt,
- certifikatjakt under byggtiden,
- fake users, fake customers, fake savings eller fake accuracy.
