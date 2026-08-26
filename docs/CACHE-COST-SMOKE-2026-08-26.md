# Cache- och kostnadssmoke – 26 augusti 2026

Detta dokument visar vad Incident Detective faktiskt kan mäta från riktiga Gemini-anrop. Det är en utvecklingssmoke, inte en faktura, benchmark eller generell kvalitetsmätning.

All incidentdata var syntetisk. API-nyckeln lästes från en Git-ignorerad lokal fil och skrevs inte ut.

## Vad som testades

Tre liveutredningar kördes med `gemini-3.1-flash-lite` och prompt `gemini-live-v6` över samma payment-timeout-scenario:

| Klient | Run ID | Resultat | Latency | Modellanrop | Tool calls | Input | Output + thinking | Totalt | Betalt listprisestimat |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| API | `a50e9cce-f2b7-48ad-918a-f3722384ebe5` | Rätt rotorsak och tjänst | 5 770 ms | 3 | 4 | 4 978 | 1 008 | 5 986 | 0,00275650 USD |
| API | `9e9cf9ea-e999-4ba7-bbdb-b1136bb5c46e` | Rätt rotorsak och tjänst | 5 941 ms | 3 | 4 | 4 978 | 1 008 | 5 986 | 0,00275650 USD |
| React UI | `46286678-0930-46fe-923a-4971494c36df` | Rätt rotorsak och tjänst | cirka 5 250 ms | 3 | 4 | 4 978 | 1 010 | 5 988 | cirka 0,00276 USD |

UI-körningen klarade schema, 100 procent citation ID validity och 5/5 claim coverage. Evidence precision var 5/6 för just den körningen. UI:t visade därför **“Diagnosis matched · verification incomplete”** i stället för att beskriva allt som godkänt.

## Observerat cacheutfall

Gemini rapporterade inget `cachedContentTokenCount` för något av de nio modellanropen. Incident Detective visar därför:

- `No provider-reported hit`,
- cachekostnad som `Not reported`,
- observerad cachebesparing som `Not reported`,
- alla inputtokens prissatta konservativt till vanlig inputtaxa.

Saknad metadata omvandlas inte till noll. Resultatet betyder inte att implicit caching aldrig kan ske; det betyder endast att leverantören inte rapporterade någon cacheträff i dessa körningar.

## Varför explicit cache inte är aktiverad

Gemini har både implicit och explicit context caching. Implicit caching hanteras av leverantören, medan explicit caching skapar en cache med TTL och lagringskostnad. Projektets collection-prompt är för närvarande liten och ändras mellan rundorna när återstående toolbudget och tillåtna funktioner ändras. De här mätningarna visar ingen observerad cachevinst som motiverar extra livscykel, lagringskostnad och felvägar ännu.

Rimligt nästa steg är ett avgränsat A/B-test först när samma stora stabila kontext återanvänds över många körningar. Explicit cache ska endast läggas till om testet visar lägre total kostnad eller latency utan sämre kvalitet.

## Hur kostnaden räknas

Backend delar upp leverantörens usage metadata i input, provider-rapporterad cached input, output inklusive thinking, tool-use prompt och total tokens. Ett listprisestimat beräknas sedan från Googles publicerade betalda Standard-priser för modellen.

Estimatet är inte en providerfaktura. API-svaret berättar inte om körningen faktiskt debiterades via free tier, och embeddingkostnad tas inte med när leverantören inte lämnar tillräcklig usage metadata.

## Officiella källor

- [Gemini context caching](https://ai.google.dev/gemini-api/docs/generate-content/caching)
- [Gemini 3.1 Flash-Lite](https://ai.google.dev/gemini-api/docs/models/gemini-3.1-flash-lite)
- [Gemini API pricing](https://ai.google.dev/gemini-api/docs/pricing)
- [Java SDK usage metadata](https://googleapis.github.io/java-genai/javadoc/com/google/genai/types/GenerateContentResponseUsageMetadata.html)
