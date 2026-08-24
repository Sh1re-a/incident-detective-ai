# Prioriterad sprintbacklog

Backloggen är ordnad efter de fyra veckogatesen. **MUST** är sprintens kontrakt. **SHOULD** får endast starta när aktuell gate är passerad. **CUT** tas inte in under sprinten.

Statusvärden: `Klar`, `Pågår`, `Ej startad`, `Väntar på extern bekräftelse`.

## Grundfas – före produktkod

| ID | Leverans | Acceptanskriterium | Status |
|---|---|---|---|
| M-00 | Projektgrund | README, sprintplan, backlog, beslut och dag‑1‑kontrakt finns; inga hemligheter, licens eller produktkod | Klar |
| M-01 | Privat versionshantering | `main` finns i privat `Sh1re-a/incident-detective-ai` och URL/visibility är verifierad | Klar |
| M-02 | Scope- och kontraktsgranskning | Shirwac har granskat planen, MUST/SHOULD/CUT och dag‑1‑kontrakten innan implementation | Ej startad |
| M-03 | Ägarskap för publik release | Julia/addendumfrågan är bekräftad innan repot blir publikt eller får en open-source-licens | Väntar på extern bekräftelse |
| M-04 | Veckovisa check-ins | Framsteg, lärande, hinder, beslut och nästa prioriteringar dokumenteras 28 aug, 4 sep, 11 sep och vid finalen 18 sep | Ej startad |
| M-05 | Klartecken för extern deploy | Shirwac har separat och uttryckligen godkänt Cloud Run-deployen innan den genomförs | Väntar på extern bekräftelse |

## MUST – vecka 1, synlig vertikal slice

| ID | Leverans | Acceptanskriterium | Status |
|---|---|---|---|
| M-10 | Låst kärnberättelse | “Checkout errors threaten orders” har tydlig kundpåverkan, tidslinje och ett säkert nästa steg | Ej startad |
| M-11 | API- och billingcheck | Faktisk modellåtkomst är verifierad utan att token eller hemlighet hamnar i repo/logg | Ej startad |
| M-12 | Domänkontrakt | `Scenario`, `Evidence`, `Diagnosis` och separat `GroundTruth` är implementerade och validerade | Ej startad |
| M-13 | Två scenariopaket | Två seedade, deterministiska paket har minst två evidenstyper, brus och dolt facit | Ej startad |
| M-14 | Backend och verifierare | Riktig frontend–API-kommunikation samt kontroll av schema, citation IDs och facit fungerar | Ej startad |
| M-15 | Recorded Story View | Ett helt fall kan följas på högst 90 sekunder och märks “Simulated incident — recorded deterministic replay.” | Ej startad |
| M-16 | Första liveutredningen | Efter kontraktslås kör ett verkligt modellanrop och läget märks “Simulated incident — real AI investigation.” | Ej startad |

**Gate 28 augusti:** ett fall förstås på högst 90 sekunder, minst två evidenstyper visas, resultatet är strukturerat, evidence IDs är klickbara och giltiga och replay/live-märkningen är sann.

## MUST – vecka 2, AI-systemet feature complete

| ID | Leverans | Acceptanskriterium | Status |
|---|---|---|---|
| M-20 | Fyra typade read-only tools | `get_metrics`, `search_logs`, `get_trace` och `retrieve_runbooks` har validerade in-/utdata och kan inte skriva | Ej startad |
| M-21 | Begränsad state machine | `COLLECT → SYNTHESIZE → VERIFY` följer hela dag‑1‑budgeten: tre collection-rundor, fyra model/åtta tool calls, två per tooltyp, tre parallella reads, tool-free synthesis och 45 s timeout | Ej startad |
| M-22 | Structured diagnosis | Pydantic v2 validerar `diagnosed` eller `insufficient_evidence`, claims och evidence IDs | Ej startad |
| M-23 | Runbook retrieval | PostgreSQL/pgvector söker endast i 10–15 runbooks och returnerar citerbar dokument-/chunkmetadata | Ej startad |
| M-24 | Sex incidentfamiljer | Sex rotorsaksfamiljer återanvänder samma syntetiska webbshop, tjänstekarta, checkout-berättelse och UI; minst tre är valbara i demon | Ej startad |
| M-25 | Story + Engineering View | Berättelse och tekniska detaljer kan växlas utan att privat chain-of-thought visas | Ej startad |
| M-26 | Körmetadata | Run ID, mode, model ID, promptversion, git SHA, latency, tokens, calls och kostnadsestimat sparas | Ej startad |
| M-27 | Grundobservability | Strukturerade JSON-loggar och OpenTelemetry täcker API, verktyg och verifiering utan hemligheter | Ej startad |

**Gate 4 september:** liveutredningen fungerar och Engineering View visar tools, retrieval, evidens och körmetadata.

## MUST – vecka 3, evals och feature freeze

| ID | Leverans | Acceptanskriterium | Status |
|---|---|---|---|
| M-30 | 18 evalfall | Sex familjer × tre variationer, med separata development- och held-out-fall | Ej startad |
| M-31 | Baseline | Symptom-only baseline och full utredning körs mot samma relevanta fall | Ej startad |
| M-32 | Tre separata verifieringar | Citation validity, evidence support/precision och diagnosis correctness mäts var för sig | Ej startad |
| M-33 | Retrievalmått | Hit@4 mäts mot förväntad runbookkälla/chunk för relevanta fall | Ej startad |
| M-34 | Säkerhets-/abstentionfall | Minst två fall kräver korrekt abstention och ett runbookfall innehåller prompt-injection-liknande text | Ej startad |
| M-35 | Portabel evalrapport | Samma kommando fungerar lokalt/CI och redovisar accuracy, citations, retrieval, latency, calls och kostnad | Ej startad |
| M-36 | Failure cases | Alla verkligt observerade missar och kända begränsningar dokumenteras; inget minimiantal eller konstruerad miss krävs | Ej startad |

**Gate 11 september:** evalrapporten är reproducerbar, publika claims har mätstöd och feature freeze börjar.

## MUST – vecka 4, release och presentation

| ID | Leverans | Acceptanskriterium | Status |
|---|---|---|---|
| M-40 | Deploybar container | Frontend/backend körs som avsedd container och kan deployas till Cloud Run | Ej startad |
| M-41 | Cloud Run | Godkänd deployment använder server-side secrets, budget/rate limits och inga hemligheter i klienten | Ej startad |
| M-42 | Ärlig replay-fallback | Livefel/timeout leder till tydligt märkt recorded replay, aldrig falsk Live AI-status | Ej startad |
| M-43 | Tillgänglig kärnresa | Mobil, tangentbord, kontrast, textstatus och reduced motion är kontrollerade | Ej startad |
| M-44 | Användartest | Om deltagare finns: tre tekniska och tre icke-tekniska tester dokumenteras utan fake users | Ej startad |
| M-45 | Slutdokumentation | README, arkitekturbild, limitations, evalrapport och faktisk mätmetod är reproducerbara | Ej startad |
| M-46 | Demoartefakter | 60–90 s video och max 10 min presentation visar värde, bevis, begränsningar och lärdomar | Ej startad |
| M-47 | Releasecheck | 20 smoke runs är genomförda, materiella fel är åtgärdade en gång och release tag är skapad | Ej startad |

**Gate 18 september:** godkänd Cloud Run-version, fungerande fallback, sanningsenliga resultat och presentation på högst tio minuter.

## Release targets – får bara publiceras efter mätning

| Mätetal | Mål | Nuläge |
|---|---:|---|
| Diagnosis accuracy | ≥ 15/18 | Ej mätt |
| Schema pass | 100 % | Ej mätt |
| Citation ID validity | 100 % | Ej mätt |
| Evidence precision | ≥ 90 % | Ej mätt |
| Retrieval Hit@4 | ≥ 90 % | Ej mätt |
| Korrekta abstentions | ≥ 2 | Ej mätt |
| Warm p95 | < 30 s | Ej mätt |
| Hard timeout | 45 s | Planerat, ej verifierat |
| Model/tool calls | ≤ 4 / ≤ 8 | Planerat, ej verifierat |
| Smoke runs | 20 stabila | Ej körda |

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
