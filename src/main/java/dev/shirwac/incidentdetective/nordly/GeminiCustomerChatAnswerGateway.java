package dev.shirwac.incidentdetective.nordly;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiCostEstimator;
import dev.shirwac.incidentdetective.ai.GoogleGenAiClientFactory;
import dev.shirwac.incidentdetective.ai.GoogleGenAiProviderRoute;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import dev.shirwac.incidentdetective.live.LiveAiOperation;
import dev.shirwac.incidentdetective.live.LiveAiRunGuard;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Google Gen AI implementation for bounded, evidence-backed customer replies. */
@Component
@Profile("rag")
public final class GeminiCustomerChatAnswerGateway
        implements CustomerChatAnswerGateway {

    static final String PROMPT_RESOURCE =
            "ai/prompts/answer-customer-chat-v1.txt";
    static final String SCHEMA_RESOURCE =
            "ai/customer-chat-answer-schema-v1.json";
    static final int TIMEOUT_MS = 15_000;
    private static final String APPROVED_CONTACT_EVIDENCE =
            "nordly-evidence-manual-support-contact";
    private static final String DATA_BOUNDARY_EVIDENCE =
            "nordly-evidence-data-minimization";
    private static final String PROTECTED_BOUNDARY_INTENT =
            "protected_boundary";

    private static final Logger LOGGER = LoggerFactory.getLogger(
            GeminiCustomerChatAnswerGateway.class
    );
    private static final Pattern BUSINESS_ACTION_CLAIM = Pattern.compile(
            "(?:(?:jag|vi) (?:har |har nu |)(?:aterbetalat|avbestallt|avbokat|"
                    + "annullerat|andrat|uppdaterat|bestallt|kopt|lagt till)|"
                    + "(?:ordern|bestallningen|aterbetalningen|returen) "
                    + "(?:ar|har blivit|har) (?:avbestalld|avbokad|annullerad|"
                    + "andrad|uppdaterad|genomford|genomforts|skapad|skapats|"
                    + "godkand|godkants)|"
                    + "(?:i|we)(?: have | have now |'ve )(?:refunded|cancelled|canceled|"
                    + "changed|updated|ordered|purchased|added)|"
                    + "(?:the )?(?:order|refund|return) (?:has been|is) "
                    + "(?:cancelled|canceled|refunded|changed|updated|created|"
                    + "approved|processed|issued))",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b"
    );
    private static final Pattern SWEDISH_PERSONAL_NUMBER = Pattern.compile(
            "\\b(?:19|20)?\\d{6}[-+ ]?\\d{4}\\b"
    );
    private static final Pattern CARD_LIKE_NUMBER = Pattern.compile(
            "\\b(?:\\d[ -]*?){13,19}\\b"
    );
    private static final Pattern PHONE_LIKE_NUMBER = Pattern.compile(
            "(?<![A-Z0-9])(?:\\+46|0)7[0236][ -]?(?:\\d[ -]?){6,8}(?!\\d)"
    );
    private static final Pattern CONTACT_ROUTE = Pattern.compile(
            "(?:\\b(?:ring(?:a)?|call)\\s+(?:till\\s+|pa\\s+|at\\s+)?"
                    + "(?:kundtjanst(?:en)?|support(?:en)?|customer service|"
                    + "us|oss|\\+?\\d)|"
                    + "\\b(?:kontakta|contact)\\s+(?:kundtjanst(?:en)?|"
                    + "support(?:en)?|customer service|us|oss)|"
                    + "\\b(?:telefon(?:nummer|numret)?|phone number)\\s*"
                    + "(?:ar|is|:)?\\s*\\+?\\d)"
    );
    private static final Pattern CONTACT_NUMBER = Pattern.compile(
            "(?<![\\p{L}\\d-])\\+?\\d(?:[\\d ()-]*\\d)?"
                    + "(?![\\p{L}\\d])"
    );
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)(?:\\bAIza[0-9a-z_-]{20,}\\b|\\bsk-[0-9a-z_-]{16,}\\b|"
                    + "\\b(?:api[-_ ]?key|password|secret|token)\\s*[:=]\\s*\\S+)"
    );
    private static final Pattern SWEDISH_BOUNDARY_LANGUAGE = Pattern.compile(
            "(?:(?:kan|far).{0,24}inte|lamnar.{0,16}inte.{0,16}ut|"
                    + "delar.{0,16}inte|har inte behorighet|"
                    + "hall(?:s|er).{0,24}utanfor|skyddad information|"
                    + "privat information|utanfor min befogenhet)"
    );
    private static final Pattern ENGLISH_BOUNDARY_LANGUAGE = Pattern.compile(
            "(?:cannot|can't|do not disclose|does not disclose|"
                    + "do not share|does not share|won't share|cannot access|"
                    + "kept.{0,24}outside|protected information|"
                    + "private information|outside my authority|not authorized)"
    );
    private static final Pattern NAMED_PROTECTED_DISCLOSURE = Pattern.compile(
            "\\b\\p{Lu}[\\p{L}'-]{1,30}\\s+"
                    + "(?:tjanar|tjänar|har lon|har lön|bor pa|bor på|"
                    + "earns?|is paid|lives at)\\b"
    );
    private static final Pattern DECIMAL_DIGIT = Pattern.compile("\\d");

    private final GeminiAiProperties properties;
    private final GoogleGenAiClientFactory clientFactory;
    private final GeminiCostEstimator costEstimator;
    private final LiveAiRunGuard liveAiRunGuard;
    private final JsonMapper jsonMapper;
    private final String instructions;
    private final Map<String, Object> schema;
    private volatile Client client;

    public GeminiCustomerChatAnswerGateway(
            GeminiAiProperties properties,
            GoogleGenAiClientFactory clientFactory,
            GeminiCostEstimator costEstimator,
            LiveAiRunGuard liveAiRunGuard,
            JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.clientFactory = clientFactory;
        this.costEstimator = costEstimator;
        this.liveAiRunGuard = liveAiRunGuard;
        this.jsonMapper = jsonMapper;
        instructions = loadText(PROMPT_RESOURCE);
        schema = loadSchema(SCHEMA_RESOURCE);
    }

    @Override
    public Result generate(boolean confirmLiveAi, Input input) {
        return liveAiRunGuard.runConfirmed(
                confirmLiveAi,
                LiveAiOperation.CUSTOMER_CHAT_ANSWER,
                () -> generateAdmitted(input)
        );
    }

    private Result generateAdmitted(Input input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        String prompt = "bounded_input_json:\n" + serialize(input);
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(instructions)))
                .responseMimeType("application/json")
                .responseJsonSchema(schema)
                .maxOutputTokens(900)
                .temperature(0.0F)
                .thinkingConfig(ThinkingConfig.builder()
                        .thinkingLevel(ThinkingLevel.Known.valueOf(
                                properties.thinkingLevel().name()
                        ))
                        .includeThoughts(false)
                        .build())
                .httpOptions(httpOptions())
                .build();

        Instant startedAt = Instant.now();
        try {
            GenerateContentResponse response = client().models.generateContent(
                    properties.modelId(),
                    prompt,
                    config
            );
            long latencyMs = Math.max(
                    0,
                    Duration.between(startedAt, Instant.now()).toMillis()
            );
            return decodeResponse(response, input, latencyMs);
        } catch (ModelProviderException exception) {
            throw exception;
        } catch (GenAiIOException exception) {
            LOGGER.warn(
                    "Customer chat generation transport failed: type={}",
                    exception.getClass().getSimpleName()
            );
            if (isTimeout(exception)) {
                throw failure(
                        ModelProviderFailure.TIMEOUT,
                        "Gemini customer chat generation timed out",
                        exception
                );
            }
            throw failure(
                    ModelProviderFailure.UPSTREAM,
                    "Gemini customer chat generation failed",
                    exception
            );
        } catch (ApiException exception) {
            LOGGER.warn(
                    "Customer chat provider rejected request: status={}, type={}",
                    exception.code(),
                    exception.getClass().getSimpleName()
            );
            ModelProviderFailure providerFailure = exception.code() == 429
                    ? ModelProviderFailure.RATE_LIMITED
                    : ModelProviderFailure.UPSTREAM;
            throw failure(
                    providerFailure,
                    "Gemini customer chat generation failed",
                    exception
            );
        } catch (RuntimeException exception) {
            throw failure(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini customer chat response was invalid",
                    exception
            );
        } catch (Exception exception) {
            throw failure(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini customer chat response was invalid",
                    exception
            );
        }
    }

    Result decodeResponse(
            GenerateContentResponse response,
            Input input,
            long latencyMs
    ) {
        rejectMissingOrTruncated(response);
        try {
            Answer answer = jsonMapper.readValue(response.text(), Answer.class);
            verifyAnswer(answer, input);
            String modelVersion = response.modelVersion()
                    .filter(value -> !value.isBlank())
                    .orElse(properties.modelId());
            ModelTokenUsage usage = decodeUsage(
                    response.usageMetadata().orElse(null)
            );
            return new Result(
                    answer,
                    new ProviderMetadata(
                            GoogleGenAiProviderRoute.from(properties),
                            response.responseId().orElse(null),
                            modelVersion,
                            usage,
                            latencyMs
                    ),
                    costEstimator.estimate(properties.modelId(), usage)
            );
        } catch (ModelProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini customer chat response was invalid",
                    exception
            );
        } catch (Exception exception) {
            throw failure(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini customer chat response was invalid",
                    exception
            );
        }
    }

    private void verifyAnswer(Answer answer, Input input) {
        Set<String> allowedEvidence = input.evidence().stream()
                .map(Evidence::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean citationsAllowed = answer.claims().stream()
                .flatMap(claim -> claim.citationIds().stream())
                .allMatch(allowedEvidence::contains);
        if (!citationsAllowed) {
            throw failure(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini customer chat answer cited evidence outside the bounded input",
                    null
            );
        }
        Set<String> cited = new HashSet<>();
        answer.claims().stream()
                .flatMap(claim -> claim.citationIds().stream())
                .forEach(cited::add);
        if (requiresEvidenceClaim(
                input.routedIntent(),
                input.routedOutcome()
        ) && cited.isEmpty()) {
            throw failure(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini customer chat answer omitted required evidence citations",
                    null
            );
        }
        String combined = answer.textSv() + '\n' + answer.textEn() + '\n'
                + answer.claims().stream()
                .map(claim -> claim.textSv() + '\n' + claim.textEn())
                .collect(java.util.stream.Collectors.joining("\n"));
        if (BUSINESS_ACTION_CLAIM.matcher(normalize(combined)).find()) {
            throw failure(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini customer chat answer claimed a business action",
                    null
            );
        }
        if (PROTECTED_BOUNDARY_INTENT.equals(input.routedIntent())) {
            verifyProtectedBoundary(answer, input);
        }
        verifyContactOutput(answer, input);
        if (EMAIL.matcher(combined).find()
                || SWEDISH_PERSONAL_NUMBER.matcher(combined).find()
                || CARD_LIKE_NUMBER.matcher(combined).find()
                || SECRET_VALUE.matcher(combined).find()) {
            throw failure(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini customer chat answer contained private or secret output",
                    null
            );
        }
    }

    private void verifyProtectedBoundary(Answer answer, Input input) {
        boolean exactBoundaryEvidence = input.evidence().size() == 1
                && DATA_BOUNDARY_EVIDENCE.equals(
                input.evidence().getFirst().id()
        );
        if (!exactBoundaryEvidence) {
            throw protectedBoundaryFailure();
        }
        if (!boundaryText(answer.textSv(), SWEDISH_BOUNDARY_LANGUAGE)
                || !boundaryText(answer.textEn(), ENGLISH_BOUNDARY_LANGUAGE)
                || answer.claims().stream().anyMatch(claim ->
                !claim.citationIds().equals(List.of(DATA_BOUNDARY_EVIDENCE))
                        || unsafeProtectedText(claim.textSv())
                        || unsafeProtectedText(claim.textEn()))) {
            throw protectedBoundaryFailure();
        }
    }

    private boolean boundaryText(String value, Pattern boundaryLanguage) {
        String normalized = normalize(value);
        return boundaryLanguage.matcher(normalized).find()
                && !unsafeProtectedText(value);
    }

    private boolean unsafeProtectedText(String value) {
        return DECIMAL_DIGIT.matcher(value).find()
                || NAMED_PROTECTED_DISCLOSURE.matcher(value).find();
    }

    private ModelProviderException protectedBoundaryFailure() {
        return failure(
                ModelProviderFailure.MALFORMED_RESPONSE,
                "Gemini customer chat answer violated the protected boundary",
                null
        );
    }

    private void verifyContactOutput(Answer answer, Input input) {
        boolean approvedContactEvidenceSupplied = input.evidence().stream()
                .anyMatch(evidence -> APPROVED_CONTACT_EVIDENCE.equals(
                        evidence.id()
                ));
        for (Claim claim : answer.claims()) {
            boolean contactClaim = containsContactRoute(claim.textSv())
                    || containsContactRoute(claim.textEn());
            if (contactClaim && (!approvedContactEvidenceSupplied
                    || !claim.citationIds().contains(
                    APPROVED_CONTACT_EVIDENCE
            ))) {
                throw unsupportedContactOutput();
            }
        }
        if (containsContactRoute(answer.textSv())
                && (!approvedContactEvidenceSupplied
                || !contactClaimSupports(
                answer.textSv(),
                true,
                answer.claims()
        ))) {
            throw unsupportedContactOutput();
        }
        if (containsContactRoute(answer.textEn())
                && (!approvedContactEvidenceSupplied
                || !contactClaimSupports(
                answer.textEn(),
                false,
                answer.claims()
        ))) {
            throw unsupportedContactOutput();
        }
    }

    private boolean contactClaimSupports(
            String answerText,
            boolean swedish,
            List<Claim> claims
    ) {
        Set<String> answerNumbers = contactNumbers(answerText);
        return claims.stream()
                .filter(claim -> claim.citationIds().contains(
                        APPROVED_CONTACT_EVIDENCE
                ))
                .map(claim -> swedish ? claim.textSv() : claim.textEn())
                .filter(this::containsContactRoute)
                .anyMatch(claimText -> contactNumbers(claimText)
                        .containsAll(answerNumbers));
    }

    private boolean containsContactRoute(String text) {
        return CONTACT_ROUTE.matcher(normalize(text)).find()
                || PHONE_LIKE_NUMBER.matcher(text).find();
    }

    private Set<String> contactNumbers(String text) {
        Set<String> numbers = new HashSet<>();
        Pattern.compile("[.!?\\n]+").splitAsStream(text)
                .filter(this::containsContactRoute)
                .flatMap(segment -> CONTACT_NUMBER.matcher(segment).results())
                .map(result -> result.group().replaceAll("\\D", ""))
                .filter(value -> value.length() >= 3)
                .forEach(numbers::add);
        return Set.copyOf(numbers);
    }

    private ModelProviderException unsupportedContactOutput() {
        return failure(
                ModelProviderFailure.MALFORMED_RESPONSE,
                "Gemini customer chat answer contained an unsupported contact route",
                null
        );
    }

    private boolean requiresEvidenceClaim(
            String routedIntent,
            String routedOutcome
    ) {
        return !Set.of("conversation", PROTECTED_BOUNDARY_INTENT)
                .contains(routedIntent)
                && Set.of("answered", "outside_authority")
                .contains(routedOutcome);
    }

    private synchronized Client client() {
        if (client == null) {
            if (!properties.hasProviderConfiguration()) {
                throw failure(
                        ModelProviderFailure.UPSTREAM,
                        "Google Gen AI provider is not configured",
                        null
                );
            }
            client = clientFactory.create(httpOptions());
        }
        return client;
    }

    private HttpOptions httpOptions() {
        return HttpOptions.builder()
                .timeout(TIMEOUT_MS)
                .retryOptions(HttpRetryOptions.builder()
                        .attempts(1)
                        .build())
                .build();
    }

    private void rejectMissingOrTruncated(GenerateContentResponse response) {
        if (response == null || response.text() == null
                || response.text().isBlank()) {
            throw failure(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini returned no customer chat answer",
                    null
            );
        }
        if (response.finishReason().knownEnum()
                == FinishReason.Known.MAX_TOKENS) {
            throw failure(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini customer chat answer was truncated",
                    null
            );
        }
    }

    private ModelTokenUsage decodeUsage(
            GenerateContentResponseUsageMetadata usage
    ) {
        if (usage == null) {
            return null;
        }
        Integer inputTokens = usage.promptTokenCount().orElse(null);
        Integer cachedInputTokens = usage.cachedContentTokenCount().orElse(null);
        Integer uncachedInputTokens = inputTokens == null
                || cachedInputTokens == null
                ? null
                : Math.max(0, inputTokens - cachedInputTokens);
        Integer candidateOutputTokens = usage.candidatesTokenCount().orElse(null);
        Integer thinkingOutputTokens = usage.thoughtsTokenCount().orElse(null);
        Integer outputTokens = candidateOutputTokens == null
                ? null
                : candidateOutputTokens
                + (thinkingOutputTokens == null ? 0 : thinkingOutputTokens);
        return new ModelTokenUsage(
                inputTokens,
                cachedInputTokens,
                uncachedInputTokens,
                candidateOutputTokens,
                thinkingOutputTokens,
                outputTokens,
                usage.toolUsePromptTokenCount().orElse(null),
                usage.totalTokenCount().orElse(null)
        );
    }

    private Map<String, Object> loadSchema(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream input = resource.getInputStream()) {
            Map<String, Object> value = jsonMapper.readValue(
                    input,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
            return Map.copyOf(value);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not load customer chat answer schema",
                    exception
            );
        }
    }

    private String loadText(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not load customer chat answer prompt",
                    exception
            );
        }
    }

    private String serialize(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not serialize bounded customer chat input",
                    exception
            );
        }
    }

    private String normalize(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('’', '\'')
                .replace('‘', '\'')
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedIOException
                    || current instanceof HttpTimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ModelProviderException failure(
            ModelProviderFailure providerFailure,
            String message,
            Throwable cause
    ) {
        return cause == null
                ? new ModelProviderException(providerFailure, message)
                : new ModelProviderException(
                        providerFailure,
                        message,
                        cause
                );
    }

    @PreDestroy
    void closeClient() {
        Client current = client;
        if (current != null) {
            current.close();
        }
    }
}
