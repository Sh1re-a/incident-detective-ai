# Sprintplan – Incident Detective

- **Period:** tisdag 25 augusti–fredag 18 september 2026
- **Projekt:** SALT Sprint 3 – The Passion Project
- **Omfattning:** cirka 95–115 fokuserade timmar
- **Dokumentstatus:** version 1.2 granskad, 26 augusti 2026

## Problemet jag vill arbeta med

När ett mjukvarusystem får en incident finns svaret sällan på ett ställe. Metrics visar att något har hänt, logs ger detaljer, traces visar var felet rör sig och runbooks beskriver vad teamet brukar göra. Det tar tid att samla ihop detta och det kan vara svårt att se varför en föreslagen diagnos går att lita på.

Jag vill bygga en liten och tydlig demo av hur AI kan hjälpa till med just den första, skrivskyddade utredningen. Berättelsen är: **checkout errors threaten orders**. Besökaren ska snabbt förstå kundpåverkan, följa utredningen och själv kunna öppna evidensen bakom slutsatsen.

## Varför jag vill bygga det

Projektet låter mig kombinera sådant jag redan arbetar med som software developer – gränssnitt, API:er, typer, Git och deployment – med sådant jag vill fördjupa: Java/Spring Boot, tool calling, structured output, RAG, evals och observability.

Det viktiga för mig är inte att visa många AI-funktioner. Jag vill visa att jag kan bygga, utvärdera och driftsätta ett avgränsat AI-system och samtidigt förklara dess fel, tradeoffs och säkerhetsgränser. Resultatet ska vara ett ärligt samtalsunderlag för både Software Engineer- och Applied AI-roller.

## Vad en besökare ska kunna göra

1. Välja eller starta en helt syntetisk incident i en webbshop.
2. Förstå kund- och affärspåverkan utan att först läsa en teknisk rapport.
3. Starta en begränsad AI-utredning.
4. Se vilka skrivskyddade verktyg som används för metrics, logs, traces och runbooks.
5. Öppna evidensposter som stöder diagnosens påståenden.
6. Se rotorsak, drabbad tjänst, ett säkert nästa steg och eventuellt `insufficient_evidence`.
7. Jämföra diagnosen med ett dolt syntetiskt facit.
8. Växla till en Engineering View för att granska körmetadata och evalresultat.

## Huvudmål och förväntat resultat

Senast den 18 september vill jag ha en liten men komplett produkt som är deployad på Cloud Run och som innehåller:

- minst tre publikt valbara syntetiska incidenter från totalt sex incidentfamiljer,
- en riktig live-AI-utredning med typade, skrivskyddade verktyg,
- strikt validerade diagnoser med klickbara evidence IDs,
- retrieval över kuraterade runbooks, men inte över metrics, logs eller traces,
- en deterministisk verifierare och en evalharness med 18 fall,
- Story View för den snabba berättelsen och Engineering View för teknisk granskning,
- faktiskt uppmätt latens, tokenanvändning, modellkostnad och kvalitetsmått,
- en tydligt märkt recorded replay som reserv om live-API:t inte är tillgängligt,
- en reproducerbar README, en ärlig limitations-del och en 60–90 sekunders demovideo,
- ett presentationsflöde som ryms inom tio minuter.

Cloud Run är ett tekniskt MUST, men jag gör ingen extern deploy utan ett separat, uttryckligt klartecken. Projektgrunden är publik på GitHub som ett icke-kommersiellt portfolio- och utbildningsprojekt och har ingen open-source-licens. Om nödvändigt klartecken för deploy saknas i vecka 4 redovisar jag deployment som blockerad; jag kallar inte enbart lokal körning för uppnådd Cloud Run-leverans.

## Vad som är simulerat och vad som ska vara verkligt

All incidentdata är syntetisk: logs, metrics, traces, runbooks, releasehändelser och uppskattad affärspåverkan. Systemet ansluter aldrig till ett riktigt företags driftmiljö och får aldrig utföra rollback eller annan remediation.

Det som ska vara verkligt är modellens verktygsval, tool calling, structured output, retrieval, verifiering, evals, tracing, uppmätt latency, tokenanvändning, kostnadsestimat och deployment.

En livekörning märks **“Simulated incident — real AI investigation.”** En sparad reservkörning märks **“Simulated incident — recorded deterministic replay.”** Jag visar verktygshändelser och säkra sammanfattningar, aldrig privat chain-of-thought.

## Hur jag vet om jag lyckats

För en icke-teknisk person ska värdet gå att förstå inom 30–60 sekunder och ett helt incidentfall inom högst 90 sekunder. För en teknisk granskare ska diagnosen gå att följa tillbaka till de exakta evidensposter modellen fick se.

Följande är **release targets, inte uppnådda resultat**:

| Mål till release | Nuläge vid sprintstart |
|---|---|
| Minst 15 av 18 korrekta diagnoser | Ej mätt |
| 100 % schema pass | Ej mätt |
| 100 % citation ID validity | Ej mätt |
| Minst 90 % evidence precision | Ej mätt |
| Minst 90 % retrieval Hit@4 | Ej mätt |
| Minst två korrekta abstentions | Ej mätt |
| Warm p95 under 30 sekunder; hard timeout 45 sekunder | Ej mätt |
| Högst fyra model calls och åtta tool calls per körning | Ej mätt |
| 20 stabila smoke runs inför release | Ej körda |

Jag publicerar bara de siffror som faktiskt har mätts och beskriver testmängd och metod bredvid resultatet.

## Vad jag själv ska lära mig

När sprinten är klar ska jag själv kunna:

- modellera och validera samma domänkontrakt i Java/Spring Boot och TypeScript,
- bygga och felsöka ett begränsat tool-calling-flöde,
- förklara skillnaden mellan retrievalkvalitet, evidensstöd och korrekt diagnos,
- designa evals som även visar abstentioner och failure cases,
- läsa tracing, latency, token- och kostnadsdata för en verklig körning,
- containerisera och driftsätta lösningen på Cloud Run,
- försvara varför jag valde bort funktioner som inte hjälper kärnproblemet.

## Plan, vecka för vecka

### Vecka 1 – synlig vertikal slice, 25–28 augusti (24–28 timmar)

Jag börjar med berättelsen och kontrakten, inte med en snygg webbplats.

- Låsa kärnberättelsen “checkout errors threaten orders”.
- Granska sprintplan, scope och dag‑1‑kontrakt innan produktkod startar.
- Kontrollera faktisk API-åtkomst och billing utan att lägga hemligheter i repot.
- Definiera `Scenario`, `Evidence`, `Diagnosis` och separat `GroundTruth`.
- Skapa två deterministiska syntetiska scenariopaket med minst två evidenstyper vardera.
- Bygga riktig API-kommunikation och backendverifieraren.
- Använda en statisk/recorded Story View för att låsa upplevelsen.
- Koppla in den första live-AI-utredningen först när kontraktet är stabilt.

**Gate fredag 28 augusti:** ett incidentfall går att förstå på högst 90 sekunder, minst två evidenstyper visas, resultatet är strukturerat, evidence IDs är klickbara och giltiga, och replay/live-läget är sanningsenligt märkt.

### Vecka 2 – AI-systemet feature complete, 31 augusti–4 september (28–34 timmar)

- Implementera `get_metrics`, `search_logs`, `get_trace` och `retrieve_runbooks` som typade read-only tools.
- Lägga 10–15 kuraterade runbooks i PostgreSQL/pgvector med citerbar källmetadata.
- Bygga den begränsade `COLLECT → SYNTHESIZE → VERIFY`-processen.
- Hålla körningen under planens yttersta tak: högst tre collection-rundor, fyra modellanrop, åtta tool calls, två anrop per verktygstyp, tre parallella read-only-anrop och 45 sekunder. Den nuvarande implementationen är striktare med högst två collection-rundor och tre modellanrop. Sista synthesis-rundan saknar tools och följs bara av deterministisk verifiering.
- Utöka till sex rotorsaksfamiljer inom samma syntetiska webbshop, tjänstekarta, checkout-berättelse och UI. Minst tre kan visas i den publika demon.
- Spara model ID, promptversion, git SHA, latens, tokens och kostnadsestimat per körning.

**Gate fredag 4 september:** liveutredningen fungerar och Engineering View visar verktyg, retrieval, evidens och körmetadata utan att visa privat tankekedja.

### Vecka 3 – bevis i stället för fler features, 7–11 september (25–30 timmar)

- Bygga 18 evalfall: sex incidentfamiljer gånger tre variationer.
- Separera utvecklingsfall från held-out-fall.
- Jämföra en symptom-only baseline med det fulla systemet.
- Mäta diagnosis accuracy, citation validity, evidence precision, retrieval Hit@4, latency, calls och kostnad.
- Ha minst två abstentionfall och ett negativt prompt-injection-liknande runbookfall.
- Dokumentera riktiga failure cases och vad jag ändrade eller valde att inte ändra.

**Gate fredag 11 september:** evalrapporten kan reproduceras lokalt/CI och alla publika kvalitetsclaims har faktisk evidens. Därefter gäller feature freeze.

### Vecka 4 – deployment, polish och presentation, 14–18 september (18–23 timmar)

- Deploya containern till Cloud Run med server-side secrets när extern deploy är godkänd.
- Lägga budget/rate limits och en tydligt märkt replay-fallback.
- Kontrollera mobil, tangentbord, kontrast och reduced motion.
- Genomföra tre tekniska och tre icke-tekniska tester om deltagare finns tillgängliga.
- Slutföra README, arkitekturbild, evalrapport och 60–90 sekunders demovideo.
- Köra 20 smoke runs, åtgärda endast materiella releasefel och skapa release tag.

**Gate fredag 18 september:** den godkända Cloud Run-versionen klarar releasechecklistan, fallbacken fungerar, resultaten är sanningsenligt redovisade och slutpresentationen håller högst tio minuter.

## Veckovisa team-check-ins

Jag föreslår korta avstämningar fredag 28 augusti, 4 september och 11 september. Den 18 september används slutpresentationen som sprintens sista gemensamma avstämning.

Vid varje check-in dokumenterar jag:

1. vad som faktiskt fungerar eller finns som artefakt,
2. vad jag har lärt mig,
3. vad som blockerar eller är osäkert,
4. vilket beslut jag tog,
5. nästa veckas högst tre prioriteringar.

## Viktiga risker och mina motdrag

| Risk | Motdrag |
|---|---|
| Jag bygger för mycket | MUST-listan och veckogates styr; SHOULD startar först när aktuell gate är passerad. |
| AI:n hittar på evidens | Okända evidence IDs underkänns deterministiskt. Påståenden och evidensstöd verifieras separat. |
| Facit råkar läcka till AI:n | `GroundTruth` hålls i eval/verifieringslagret och skickas aldrig till modell eller tools. |
| Live-demo eller API blir långsamt eller otillgängligt | Hård timeout, budgetgränser och en sanningsenligt märkt recorded replay. |
| RAG blir ett eget projekt | Endast 10–15 runbooks indexeras; telemetri ligger kvar bakom domänverktyg. |
| Syntetisk data uppfattas som verklig | Truth label visas i Story View, Engineering View, README och video. |
| Extern deploy är inte klar | GitHub-repot kan vara publikt, men Cloud Run kräver ett separat klartecken och teknisk verifiering. |
| Jag kan inte försvara lösningen i intervju | Jag dokumenterar beslut, tradeoffs, fel och evalmetod och kan återskapa körningen själv. |

## Presentation fredag 18 september, högst 10 minuter

| Tid | Innehåll |
|---|---|
| 0:00–1:00 | Problemet, användaren och vad som är syntetiskt/verkligt |
| 1:00–2:00 | Hur Story View och Engineering View hjälper två målgrupper |
| 2:00–6:00 | Demo: påverkan → utredning → evidens → diagnos → facit |
| 6:00–7:30 | Arkitektur och säkerhetsgränser |
| 7:30–8:45 | Evals, faktiska resultat och failure cases |
| 8:45–9:30 | Lärdomar, begränsningar och nästa steg |
| 9:30–10:00 | Frågor eller demobuffert |

## Startvillkor

Den här planen, MUST/SHOULD/CUT-scope och [dag‑1‑kontrakten](./docs/DAY-1-CONTRACTS.md) ska granskas innan incident- och AI-kod börjar. Projektgrunden lagras i ett publikt GitHub-repo utan open-source-licens. En minimal Java/Spring Boot-grund får finnas för att verifiera verktygskedjan; ingen incidentfunktion eller deploy ingår i grundfasen.
