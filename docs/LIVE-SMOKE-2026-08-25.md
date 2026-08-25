# Live smoke – 25 augusti 2026

Detta är en utvecklingslogg från de första riktiga Gemini-anropen. Den är **inte** en evalrapport och får inte användas som ett accuracy- eller p95-påstående.

All incidentdata var syntetisk. API-nyckeln lästes från en Git-ignorerad lokal fil och skrevs inte ut. Testet loggade endast scenario, modellprofil, säkra toolnamn, tokens, latency och deterministiska verifieringsresultat.

## Observerade försök

| Profil | Scenario | Utfall | Lärdom |
|---|---|---|---|
| Gemini 3.7 Flash, low, 14 s callgräns | Checkout orders at risk | Timeout i första collection | 14 sekunder var för snävt för den observerade free-tier-latensen. |
| Gemini 3.7 Flash, low, 22 s callgräns | Checkout orders at risk | Timeout i första collection | Felet reproducerades; 3.7 valdes inte som standard bara för modellnamnets skull. |
| Gemini 3.5 Flash-Lite, low | Checkout orders at risk | Collection klar, synthesis timeout | Thinking-nivån påverkade latency materiellt. |
| Gemini 3.5 Flash-Lite, minimal, schema v1 | Checkout orders at risk | Klar på 5 118 ms, men fel rotorsakskod | Modellen saknade en tillåten root-cause-taxonomi; structured output räcker inte om labelrymden är otydlig. |
| Gemini 3.6 Flash, minimal, schema v1 | Checkout orders at risk | Klar på 12 724 ms, felaktig abstention | Högre modellversion gav inte automatiskt bättre resultat i detta enskilda fall. |
| Gemini 3.5 Flash-Lite, minimal, schema v2 | Checkout orders at risk | Klar och korrekt på 5 209 ms | Taxonomin begränsade koden utan att avslöja vilket scenariofacit som gällde. |
| Gemini 3.5 Flash-Lite, minimal, schema v2 | Cart segment failures | Provider-timeout i första collection, två försök | Livevägen behöver fler evals och en tydligt märkt replay-fallback innan release. |
| Gemini 3.5 Flash-Lite, minimal, prompt v3 | Checkout orders at risk | Rätt diagnos på 5 300 ms, evidence precision 3/5 | Delad claim-taxonomi förbättrade precisionen, men INFO-loggar filtrerades bort och samma runbook hämtades två gånger. |
| Gemini 3.5 Flash-Lite, minimal, prompt v4 | Checkout orders at risk | Rätt diagnos på 5 500 ms, evidence precision 5/5 | Bredare första loggsökning hittade release- och konfigurationshändelser och runbookhämtningen upprepades inte. |
| Gemini 3.5 Flash-Lite, minimal, prompt v4 | Cart segment failures | Rätt diagnos på 4 550 ms, evidence precision 5/5 | Samma kontrakt fungerade i en opt-in UI-smoke för det andra scenariot. |

## Den lyckade v2-körningen

- status: `diagnosed`
- root cause: `PAYMENT_TIMEOUT_CONFIG` – korrekt mot dolt facit
- affected service: `PAYMENT_ADAPTER` – korrekt mot dolt facit
- citation ID validity: godkänd
- model calls: 3
- tool calls: 5
- säker toolsekvens: `get_metrics`, `search_logs`, `retrieve_runbooks`, `search_logs`, `retrieve_runbooks`
- input tokens: 3 967
- output tokens inklusive thinking: 808
- total latency: 5 209 ms
- uppskattat betalt standardlistpris: 0,00321010 USD
- faktisk debitering: inte avläst; kan vara 0 USD på free tier

Att just denna körning var korrekt betyder inte att modellen har 100 procent accuracy. De misslyckade försöken behålls som failure cases och nästa kvalitetssteg är en portabel evalharness över flera variationer, inte fler handplockade smoke-anrop.

## Livekörning genom Story/Engineering View

Efter att frontend kopplats till API:t kördes payment-timeoutscenariot genom den riktiga bekräftelsedialogen och live-endpointen. Run ID var `8711d460-dd18-4478-b64b-938dd9254e8e`.

- truth label: `Simulated incident — real AI investigation.`
- root cause: `PAYMENT_TIMEOUT_CONFIG` – korrekt mot dolt facit
- affected service: `PAYMENT_ADAPTER` – korrekt mot dolt facit
- diagnosis schema: godkänt
- citation ID validity: 100 procent
- evidence precision: 2 av 5 claim-evidence-länkar, alltså 40 procent för denna körning
- model calls: 3
- tool calls: 5
- tokens: 4 834
- latency: 5 140 ms
- uppskattat betalt standardlistpris: cirka 0,0034 USD
- faktisk debitering: inte avläst; kan vara 0 USD på free tier

Detta är ett användbart failure signal trots korrekt rotorsak: modellen hittade rätt diagnos men motiverade flera claims med evidens som inte matchade facitstödet tillräckligt precist. UI:t visar därför 40 procent och texten “this run” i stället för att kalla resultatet 100 procent korrekt. Prompt/tool-retrieval ska jämföras i den planerade evalharnessen innan någon kvalitetsclaim publiceras.

## Två UI-körningar med prompt v4

Efter failure-signalen ovan infördes en delad claim-taxonomi för alla fem claimkategorierna. Collection-prompten ändrades också så att den första loggsökningen inte filtrerar bort INFO-händelser för release och konfiguration, och samma runbook får högst hämtas en gång. GroundTruth och tillåtna evidenslänkar lättades inte för att passa modellen.

| Scenario | Run ID | Resultat | Citationer | Evidensstöd | Latency | Tokens | Betalt listprisestimat |
|---|---|---|---:|---:|---:|---:|---:|
| Checkout orders at risk | `c8f86f54-e86b-4ab5-9d56-e91ce8ab3fb3` | Rätt rotorsak och tjänst | 100 % giltiga | 5/5 direkta | 5 500 ms | 5 653 | 0,0039 USD |
| Cart segment failures | `7ad37410-e6c8-4e0e-add8-5477edd2f839` | Rätt rotorsak och tjänst | 100 % giltiga | 5/5 direkta | 4 550 ms | 5 363 | 0,0037 USD |

Båda körningarna använde tre modellanrop och fem read-only tool calls. Den faktiska debiteringen lästes inte av och kan vara 0 USD på free tier. De är två utvecklingssmokes över två fasta scenariofixtures, inte 100 procent accuracy, warm p95 eller ett stabilitetsbevis. Det återstår dessutom att minska upprepade metric-anrop och att köra en versionshanterad evalmängd med variationer och abstentionfall.

## Reproducerbart opt-in-kommando

Den vanliga testsuiten gör inga nätverksanrop. Ett live-smoketest måste aktiveras uttryckligen:

```bash
./mvnw -q -Dtest=GeminiLiveSmokeIT -Drun.gemini.smoke=true test
```

Ett annat scenario kan väljas med exempelvis:

```bash
./mvnw -q -Dtest=GeminiLiveSmokeIT -Drun.gemini.smoke=true \
  -Dgemini.smoke.scenario=checkout-cart-segment-failures-v1 test
```
