# Dag‑1‑kontrakt för granskning

- **Status:** granskning pågår – Java-kontrakten är implementerade lokalt men ännu inte godkända eller låsta
- **Senast uppdaterad:** 25 augusti 2026

Det här dokumentet låser begreppen och säkerhetsgränserna före incident- och AI-kod. Fältnamn kan få mindre tekniska justeringar när Java- och TypeScript-kontrakten skapas, men ansvarsfördelningen får inte ändras tyst.

## 1. Scenario

Ett icke-hemligt, syntetiskt incidentpaket som får skickas till frontend och utredningsverktyg.

Minsta innehåll:

- `scenario_id` – stabilt och unikt,
- titel och kort beskrivning,
- starttid och avgränsat tidsfönster,
- berörda syntetiska tjänster,
- uppskattad syntetisk kund-/affärspåverkan,
- en begränsad initial symptombild utan evidence-inventarium,
- versionsnummer.

Scenario innehåller aldrig `root_cause_code`, facitclaims, en lista över tillgängliga evidence IDs eller andra fält som avslöjar `GroundTruth`. Evidensindexet stannar på serversidan; frontend får endast IDs som redan har returnerats i den aktuella körningen.

## 2. Evidence

En evidenspost är exakt den data modellen kan ha sett genom ett read-only tool.

Minsta innehåll:

- `evidence_id` – stabilt, unikt och klickbart,
- `scenario_id`,
- `evidence_type` – `metric`, `log`, `trace` eller `runbook`,
- tidsstämpel eller dokumentversion,
- maskinläsbart innehåll och en säker visningssammanfattning,
- källreferens inom det syntetiska paketet,
- för runbooks: `document_id`, `chunk_id` och versionsmetadata.

Ett evidence ID räknas som synligt för modellen endast om det faktiskt har returnerats av ett tool i den aktuella körningen.

## 3. Diagnosis

Det enda godkända slutresultatet från synthesis.

Minsta innehåll:

- `status` – `diagnosed` eller `insufficient_evidence`,
- `root_cause_code` – kanonisk kod i formatet `^[A-Z][A-Z0-9_]{1,63}$`; krävs för `diagnosed`, annars `null`,
- `affected_service` – kanonisk kod för den primära tjänst där rotorsaken behöver rättas, inte alla tjänster där symptom syns; krävs för `diagnosed`, annars `null`,
- kort `business_summary`,
- kort `technical_summary`,
- `claims`, där varje påstående har en stabil `claim_code`, en maskinläsbar `claim_value_code`, visningstext och en lista `evidence_ids`,
- `safe_next_step` som kräver mänskligt godkännande.

`claim_code` är exakt en av `root_cause`, `affected_service`, `trigger`, `customer_impact`, `observed_symptom` och `missing_evidence`. `claim_value_code` gör claimens betydelse jämförbar med facit utan att poängsätta fri visningstext och följer formatet `^[A-Z][A-Z0-9_]{1,63}$`. Den versionshanterade root-cause-taxonomin och tjänstekartan är tillgängliga för modellen; vilket värde som är rätt i ett scenario stannar i `GroundTruth`.

Ett `diagnosed`-svar måste uppfylla alla följande invarianter:

- `root_cause_code` och `affected_service` är icke-null,
- exakt en `root_cause`-claim har `claim_value_code = root_cause_code`,
- exakt en `affected_service`-claim har `claim_value_code = affected_service`,
- varje claim har minst ett evidence ID,
- samma kombination av `claim_code` och `claim_value_code` förekommer högst en gång.

Ett `insufficient_evidence`-svar kräver `root_cause_code = null` och `affected_service = null`, förbjuder claims med koderna `root_cause`, `affected_service` och `trigger`, och får i övrigt ha tomma claims eller claims om observerade symptom/saknad evidens.

Ett svar får inte innehålla automatisk remediation, dold tankekedja eller evidence IDs utanför den evidens som modellen såg.

## 4. GroundTruth

Det dolda syntetiska facit som bara används av verifierare och evalharness.

Minsta innehåll:

- `scenario_id`,
- korrekt `root_cause_code`,
- korrekt `affected_service`,
- förväntade nyckelclaims med `claim_code` och `claim_value_code`,
- `allowed_evidence_ids_by_claim_key`, där claimnyckeln kombinerar dessa två koder,
- relevanta runbookdokument/chunks för retrievalmått,
- om fallet förväntar diagnos eller abstention.

`GroundTruth` får aldrig skickas till frontend före facitsteget, modellen, prompten, vector store eller något tool.

Efter att verifieringen är avslutad får frontend bara en reducerad `ReplayComparison` med förväntad status, rotorsak och primär tjänst samt matchningsresultaten. Det är den avsiktliga facitvisningen som låter besökaren se om diagnosen stämde. Rå `GroundTruth`, `expected_claims`, `claim_support`, tillåtna evidence IDs, relevanta runbooks och evidens som utredningen inte såg får fortfarande inte lämna backend eller användas av modellen.

## 5. Tool contracts

| Tool | Tillåten uppgift | Returnerar |
|---|---|---|
| `get_metrics` | Läsa namngivna metrics inom valt scenario/tidsfönster | Typade metric-evidensposter |
| `search_logs` | Söka i syntetiska logs med avgränsad fråga och tidsfönster | Typade log-evidensposter |
| `get_trace` | Hämta en namngiven syntetisk trace som redan är relevant för scenariot | Typade trace/spans med evidence IDs |
| `retrieve_runbooks` | Semantiskt söka i kuraterade runbooks, högst `top_k=4` | Chunks med dokument-, chunk- och versionsmetadata |

Gemensamma regler:

- alla tools är read-only och scenarioavgränsade,
- in- och utdata valideras,
- tomma resultat är giltiga resultat,
- samma fixture och input ger samma domändata,
- tool output innehåller aldrig facit,
- högst två anrop per verktygstyp och åtta totalt per körning.

## 6. Run och mode

Varje körning ska minst registrera:

- `run_id`, `scenario_id` och `mode`,
- `mode = recorded_replay` eller `live_ai`,
- start/sluttid, latency och slutstatus,
- model ID endast när ett verkligt modellanrop har körts,
- promptversion och git SHA,
- tool events och evidence IDs modellen fick se,
- tokenanvändning och uppskattad kostnad när leverantören returnerar underlaget,
- verifieringsresultat och eventuellt fel/timeout.

UI-regel:

- `recorded_replay` → **“Simulated incident — recorded deterministic replay.”**
- `live_ai` → **“Simulated incident — real AI investigation.”**
- inget läge får kallas live enbart för att en animation spelas upp.

## 7. Körgränser

- högst tre collection-rundor,
- högst åtta tool calls totalt,
- högst två anrop per verktygstyp,
- högst tre parallella read-only-anrop,
- högst fyra modellanrop inklusive slutlig synthesis,
- hard timeout 45 sekunder,
- sista synthesis-rundan har inga tools,
- efter synthesis körs endast deterministisk verifiering.

## 8. Verifieringskontrakt

Verifieraren svarar på tre separata frågor:

1. **Citation validity:** existerar varje citerat ID i exakt den evidens som modellen såg?
2. **Evidence support/precision:** hör evidensen ihop med claimen enligt facitets tillåtna stöd?
3. **Diagnosis correctness:** matchar `root_cause_code` det dolda facit i diagnosbara fall? I avsedda abstentionfall krävs `status = insufficient_evidence` och `root_cause_code = null`.

Evidence precision beräknas per diagnosbart evalfall över unika citerade tripplar av `(claim_code, claim_value_code, evidence_id)`: täljaren är tripplar vars evidence ID är tillåtet för motsvarande claimnyckel i `GroundTruth.allowed_evidence_ids_by_claim_key`, och nämnaren är alla citerade tripplar. Ett schemafel eller diagnostiserat svar utan citat får precision 0 för fallet. Releasevärdet är makromedelvärdet av fallens precision, så varje diagnosbart fall väger lika. Abstentionfall redovisas separat och ingår inte i detta medelvärde. `affected_service` mäts som ett separat diagnosfält och blandas inte in i root-cause-måttet.

Schemafel, okända evidence IDs och facitläckage är hårda fel. En korrekt `insufficient_evidence` räknas som lyckad abstention i avsedda fall.

## 9. Granskningscheck före kod

- [ ] Är Scenario begripligt utan att avslöja facit?
- [ ] Är Evidence tillräckligt stabilt för klickbara citat och deterministic replay?
- [ ] Kan Diagnosis uttrycka både diagnos och ärligt otillräckligt underlag?
- [ ] Är GroundTruth tekniskt isolerat från modell, tools och tidig frontend?
- [ ] Är varje tool nödvändigt, skrivskyddat och mätbart?
- [ ] Är loopens tid, calls och parallellitet entydigt begränsade?
- [ ] Är live/replay-märkningen omöjlig att misstolka?
- [ ] Kan verifieringens tre resultat visas och testas var för sig?
- [ ] Har Shirwac granskat och kan förklara kontrakten innan implementation börjar?

## 10. Preciseringar från granskningen – väntar på godkännande

Följande gör kontraktet entydigt utan att ändra säkerhetsgränserna:

- `GroundTruth` får `expected_status`. `root_cause_code` och `affected_service` är obligatoriska för diagnosbara fall och `null` för avsedda abstentionfall.
- Tillåtet evidensstöd lagras som typade `claim_support`-poster med `claim_code`, `claim_value_code` och `allowed_evidence_ids`, inte som en odefinierad strängnyckel i JSON.
- Ett `insufficient_evidence`-svar i ett fall som ska kunna diagnostiseras får evidence precision 0. Korrekt abstention redovisas separat.
- En handbyggd recorded replay har `model_id`, tokenanvändning och kostnad som `null`. `prompt_version` är också `null` om replayen inte kommer från ett versionshanterat modellpromptflöde.
- Ett hårt verifieringsfel ger ett tydligt underkänt verifieringsresultat. Det ska inte i sig göra att API:t kraschar med HTTP 500.
- Facit får visas för besökaren först efter att körningen är avslutad; det skickas aldrig till modellen eller något tool.
