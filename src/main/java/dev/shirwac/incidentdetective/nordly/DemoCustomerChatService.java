package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.ModelCostEstimate;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import dev.shirwac.incidentdetective.live.LiveAiBudgetStoreUnavailableException;
import dev.shirwac.incidentdetective.live.LiveInvestigationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Profile("rag")
public final class DemoCustomerChatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            DemoCustomerChatService.class
    );

    private static final String CANCELLATION_EVIDENCE =
            "nordly-evidence-cancellation-window";
    private static final String RETURN_EVIDENCE =
            "nordly-evidence-return-eligibility";
    private static final String REFUND_TIMING_EVIDENCE =
            "nordly-evidence-refund-timing-card";
    private static final String CONTACT_EVIDENCE =
            "nordly-evidence-manual-support-contact";
    private static final String AUTHORITY_EVIDENCE =
            "nordly-evidence-assistant-operating-role";
    private static final String DATA_BOUNDARY_EVIDENCE =
            "nordly-evidence-data-minimization";
    private static final String REDACTED_SV =
            "[STOPPAD OCH MASKERAD AV SÄKERHETSGRINDEN]";
    private static final String REDACTED_EN =
            "[BLOCKED AND REDACTED BY SAFETY GATE]";
    private static final String NO_AI_TRUTH_SV =
            "SYNTETISK KUND · FAST BACKENDKONTEXT · INGET PROVIDERANROP · "
                    + "AFFÄRSDATA READ-ONLY · INGET PERMANENT MINNE";
    private static final String NO_AI_TRUTH_EN =
            "SYNTHETIC CUSTOMER · FIXED BACKEND CONTEXT · NO PROVIDER CALL · "
                    + "BUSINESS DATA READ-ONLY · NO PERSISTENT MEMORY";
    private static final String LIVE_AI_TRUTH_SV =
            "SYNTETISK KUND · FAST BACKENDKONTEXT · KONTROLLERAD LIVE AI · "
                    + "AFFÄRSDATA READ-ONLY · INGET PERMANENT MINNE";
    private static final String LIVE_AI_TRUTH_EN =
            "SYNTHETIC CUSTOMER · FIXED BACKEND CONTEXT · CONTROLLED LIVE AI · "
                    + "BUSINESS DATA READ-ONLY · NO PERSISTENT MEMORY";
    private static final List<String> LIMITATIONS = List.of(
            "The customer, order, company documents and phone number are synthetic.",
            "The browser may send up to six recent turns for continuity; the backend stores no persistent conversation memory and never treats that transcript as factual evidence.",
            "Every turn resolves the same fixed synthetic customer and one backend-owned current order.",
            "The assistant can read and explain but has no create, cancel, refund, contact or update tool.",
            "Zero business writes covers customer, order and refund state; live quota accounting and observability may write technical records.",
            "Gemini may route and phrase a live reply, but order facts and authority boundaries come only from Java's canonical evidence projection.",
            "Refund eligibility is never decided because the demo order has no product-condition or authenticated return data.",
            "Semantic similarity ranks approved text; it is not factual confidence or complete semantic verification."
    );

    private final DemoCustomerContextCatalog contextCatalog;
    private final DemoCustomerIntentClassifier classifier;
    private final KnowledgeRagSafetyGate safetyGate;
    private final KnowledgeRagService knowledgeRagService;
    private final NordlyKnowledgeCorpus corpus;
    private final CustomerChatModelRouter modelRouter;
    private final CustomerChatAnswerGateway answerGateway;
    private final GeminiAiProperties aiProperties;
    private final NordlyKnowledgeCorpus.EntryMetadata cancellationPolicy;
    private final NordlyKnowledgeCorpus.EntryMetadata returnPolicy;
    private final NordlyKnowledgeCorpus.EntryMetadata refundTimingPolicy;
    private final NordlyKnowledgeCorpus.EntryMetadata authorityPolicy;
    private final NordlyKnowledgeCorpus.EntryMetadata contactPolicy;
    private final NordlyKnowledgeCorpus.EntryMetadata dataBoundaryPolicy;

    public DemoCustomerChatService(
            DemoCustomerContextCatalog contextCatalog,
            DemoCustomerIntentClassifier classifier,
            KnowledgeRagSafetyGate safetyGate,
            KnowledgeRagService knowledgeRagService,
            NordlyKnowledgeCorpus corpus
    ) {
        this(
                contextCatalog,
                classifier,
                safetyGate,
                knowledgeRagService,
                corpus,
                null,
                null,
                null
        );
    }

    @Autowired
    public DemoCustomerChatService(
            DemoCustomerContextCatalog contextCatalog,
            DemoCustomerIntentClassifier classifier,
            KnowledgeRagSafetyGate safetyGate,
            KnowledgeRagService knowledgeRagService,
            NordlyKnowledgeCorpus corpus,
            CustomerChatModelRouter modelRouter,
            CustomerChatAnswerGateway answerGateway,
            GeminiAiProperties aiProperties
    ) {
        this.contextCatalog = contextCatalog;
        this.classifier = classifier;
        this.safetyGate = safetyGate;
        this.knowledgeRagService = knowledgeRagService;
        this.corpus = corpus;
        this.modelRouter = modelRouter;
        this.answerGateway = answerGateway;
        this.aiProperties = aiProperties;
        cancellationPolicy = approved(
                corpus.metadata(CANCELLATION_EVIDENCE),
                CANCELLATION_EVIDENCE
        );
        returnPolicy = approved(
                corpus.metadata(RETURN_EVIDENCE),
                RETURN_EVIDENCE
        );
        refundTimingPolicy = approved(
                corpus.metadata(REFUND_TIMING_EVIDENCE),
                REFUND_TIMING_EVIDENCE
        );
        authorityPolicy = approved(
                corpus.metadata(AUTHORITY_EVIDENCE),
                AUTHORITY_EVIDENCE
        );
        contactPolicy = approved(
                corpus.metadata(CONTACT_EVIDENCE),
                CONTACT_EVIDENCE
        );
        dataBoundaryPolicy = approved(
                corpus.metadata(DATA_BOUNDARY_EVIDENCE),
                DATA_BOUNDARY_EVIDENCE
        );
    }

    public DemoCustomerChatTurnResponse run(
            DemoCustomerChatTurnRequest request
    ) {
        long started = System.nanoTime();
        String turnId = "nordly-customer-turn-" + UUID.randomUUID();
        KnowledgeRagSafetyGate.Decision safety = safetyGate.evaluate(
                request.message()
        );
        List<DemoCustomerChatTurnRequest.ConversationTurn> recentConversation =
                safeRecentConversation(request.recentConversation());
        if (isHardSafetyStop(safety)) {
            DemoCustomerIntentClassifier.Decision safeFallback =
                    classifier.classify(request.message());
            if (!request.confirmLiveAi()
                    || answerGateway == null
                    || safetyGate.containsRawSensitiveValue(
                    request.message()
            )) {
                return refused(
                        turnId,
                        started,
                        request,
                        safeFallback,
                        safety
                );
            }
            DemoCustomerChatTurnResponse response = boundedSafetyResponse(
                    turnId,
                    started,
                    request,
                    safety
            );
            try {
                return composeCustomerAnswer(
                        response,
                        request,
                        List.of(),
                        safeBoundaryPrompt(safety, request.locale()),
                        "protected_boundary"
                );
            } catch (RuntimeException exception) {
                if (!recoverableRoutingFailure(exception)) {
                    throw exception;
                }
                return compositionUnavailable(response, exception);
            }
        }

        if (!request.confirmLiveAi() || modelRouter == null) {
            DemoCustomerIntentClassifier.Decision deterministic =
                    classifier.classify(request.message());
            if (!safety.allowed()
                    && !isBoundedAction(deterministic, safety)) {
                return refused(
                        turnId,
                        started,
                        request,
                        deterministic,
                        safety
                );
            }
            return dispatch(
                    turnId,
                    started,
                    request,
                    deterministic,
                    safety
            );
        }

        CustomerChatModelRouter.RoutingResult route;
        try {
            route = modelRouter.route(new CustomerChatModelRouter.RoutingRequest(
                    request.message(),
                    request.locale(),
                    true,
                    safety.allowed()
                            ? CustomerChatModelRouter.RoutingScope.STANDARD
                            : CustomerChatModelRouter.RoutingScope.DENY_ONLY,
                    recentConversation
            ));
        } catch (RuntimeException exception) {
            if (!recoverableRoutingFailure(exception)) {
                throw exception;
            }
            return routingUnavailable(
                    turnId,
                    started,
                    request,
                    safety,
                    exception
            );
        }

        DemoCustomerIntentClassifier.Decision intent = decision(route);
        DemoCustomerChatTurnResponse response;
        try {
            response = dispatch(
                    turnId,
                    started,
                    request,
                    intent,
                    safety,
                    route.conversationKind()
            );
        } catch (RuntimeException exception) {
            if (!recoverableRoutingFailure(exception)) {
                throw exception;
            }
            response = downstreamUnavailable(
                    turnId,
                    started,
                    request,
                    intent,
                    safety,
                    exception
            );
            return attachRoute(response, route);
        }
        if (shouldCompose(response)) {
            try {
                response = composeCustomerAnswer(
                        response,
                        request,
                        recentConversation,
                        request.message(),
                        response.intent().name()
                );
            } catch (RuntimeException exception) {
                if (!recoverableRoutingFailure(exception)) {
                    throw exception;
                }
                response = compositionUnavailable(response, exception);
            }
        }
        return attachRoute(response, route);
    }

    private DemoCustomerChatTurnResponse dispatch(
            String turnId,
            long started,
            DemoCustomerChatTurnRequest request,
            DemoCustomerIntentClassifier.Decision intent,
            KnowledgeRagSafetyGate.Decision safety
    ) {
        return dispatch(
                turnId,
                started,
                request,
                intent,
                safety,
                null
        );
    }

    private DemoCustomerChatTurnResponse dispatch(
            String turnId,
            long started,
            DemoCustomerChatTurnRequest request,
            DemoCustomerIntentClassifier.Decision intent,
            KnowledgeRagSafetyGate.Decision safety,
            CustomerChatModelRouter.ConversationKind conversationKind
    ) {
        return switch (intent.intent()) {
            case ORDER_STATUS -> orderStatus(
                    turnId,
                    started,
                    request,
                    intent,
                    safety
            );
            case CANCELLATION_POLICY, RETURN_POLICY, REFUND_POLICY -> policyAnswer(
                    turnId,
                    started,
                    request,
                    intent,
                    safety
            );
            case CANCEL_ORDER, RETURN_ORDER, REFUND_ORDER, PURCHASE_ITEM,
                    CHANGE_DELIVERY_ADDRESS -> outsideAuthority(
                    turnId,
                    started,
                    request,
                    intent,
                    safety
            );
            case COMPANY_KNOWLEDGE -> companyKnowledge(
                    turnId,
                    started,
                    request,
                    intent,
                    safety
            );
            case CONVERSATION -> conversation(
                    turnId,
                    started,
                    request,
                    intent,
                    safety,
                    conversationKind
            );
            case CLARIFICATION_REQUIRED -> clarificationRequired(
                    turnId,
                    started,
                    request,
                    intent,
                    safety
            );
            case UNSUPPORTED -> unsupported(
                    turnId,
                    started,
                    request,
                    intent,
                    safety
            );
        };
    }

    private boolean isHardSafetyStop(
            KnowledgeRagSafetyGate.Decision safety
    ) {
        if (safety.allowed()) {
            return false;
        }
        return safety.reasonCode()
                != KnowledgeRagSafetyGate.ReasonCode.WRITE_ACTION
                && safety.reasonCode()
                != KnowledgeRagSafetyGate.ReasonCode.FINANCIAL_ACTION;
    }

    private DemoCustomerIntentClassifier.Decision decision(
            CustomerChatModelRouter.RoutingResult route
    ) {
        DemoCustomerIntentClassifier.Intent intent = switch (route.tool()) {
            case GET_CURRENT_ORDER ->
                    DemoCustomerIntentClassifier.Intent.ORDER_STATUS;
            case SEARCH_APPROVED_COMPANY_KNOWLEDGE ->
                    DemoCustomerIntentClassifier.Intent.COMPANY_KNOWLEDGE;
            case DENY_BUSINESS_ACTION -> switch (route.deniedAction()) {
                case CANCEL_ORDER ->
                        DemoCustomerIntentClassifier.Intent.CANCEL_ORDER;
                case RETURN_ORDER ->
                        DemoCustomerIntentClassifier.Intent.RETURN_ORDER;
                case REFUND_ORDER ->
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER;
                case PURCHASE_ITEM ->
                        DemoCustomerIntentClassifier.Intent.PURCHASE_ITEM;
                case CHANGE_DELIVERY_ADDRESS ->
                        DemoCustomerIntentClassifier.Intent.CHANGE_DELIVERY_ADDRESS;
            };
            case CONTINUE_CUSTOMER_CONVERSATION ->
                    switch (route.conversationKind()) {
                        case GREETING, THANKS ->
                                DemoCustomerIntentClassifier.Intent.CONVERSATION;
                        case CLARIFICATION ->
                                DemoCustomerIntentClassifier.Intent.CLARIFICATION_REQUIRED;
                        case OUT_OF_SCOPE ->
                                DemoCustomerIntentClassifier.Intent.UNSUPPORTED;
                    };
        };
        return new DemoCustomerIntentClassifier.Decision(
                intent,
                route.classifier(),
                route.tool()
                        == CustomerChatModelRouter.Tool.DENY_BUSINESS_ACTION
        );
    }

    private boolean recoverableRoutingFailure(RuntimeException exception) {
        if (exception instanceof ModelProviderException
                || exception instanceof LiveInvestigationException
                || exception instanceof LiveAiBudgetStoreUnavailableException) {
            return true;
        }
        return Set.of(
                "LiveDailyQuotaExceededException",
                "LiveAdmissionRejectedException"
        ).contains(exception.getClass().getSimpleName());
    }

    private String routingFailureCode(RuntimeException exception) {
        if (exception instanceof ModelProviderException provider) {
            return switch (provider.failure()) {
                case TIMEOUT -> "MODEL_TIMEOUT";
                case RATE_LIMITED -> "MODEL_RATE_LIMITED";
                case UPSTREAM -> "MODEL_UPSTREAM";
                case MALFORMED_RESPONSE -> "MODEL_RESPONSE_REJECTED";
            };
        }
        return switch (exception.getClass().getSimpleName()) {
            case "LiveDailyQuotaExceededException" -> "DAILY_LIMIT";
            case "LiveAdmissionRejectedException" -> "LIVE_BUSY";
            case "LiveAiBudgetStoreUnavailableException" -> "BUDGET_GUARD";
            case "LiveInvestigationException" -> "LIVE_CONFIGURATION";
            default -> "ROUTER_UNAVAILABLE";
        };
    }

    private DemoCustomerChatTurnResponse attachRoute(
            DemoCustomerChatTurnResponse response,
            CustomerChatModelRouter.RoutingResult route
    ) {
        List<DemoCustomerChatTurnResponse.ToolEvent> events =
                insertAfterSafety(
                        response.toolEvents(),
                        new DemoCustomerChatTurnResponse.ToolEvent(
                                0,
                                "model_routing",
                                "gemini_customer_router",
                                true,
                                route.tool().wireValue(),
                                "completed",
                                true,
                                "Gemini tolkade den fria texten och valde ett tillåtet läs- eller stoppverktyg.",
                                "Gemini interpreted the free text and selected one allowed read or boundary tool.",
                                null,
                                List.of(),
                                route.latencyMs()
                        )
                );
        DemoCustomerChatTurnResponse.Receipt receipt = addModelCall(
                response.receipt(),
                route.costEstimate(),
                route.configuredModelId()
        );
        return copyResponse(
                response,
                LIVE_AI_TRUTH_SV,
                LIVE_AI_TRUTH_EN,
                response.assistantMessage(),
                events,
                response.verifiedClaims(),
                response.verification(),
                receipt,
                response.error()
        );
    }

    private List<DemoCustomerChatTurnRequest.ConversationTurn>
    safeRecentConversation(
            List<DemoCustomerChatTurnRequest.ConversationTurn> history
    ) {
        return history.stream()
                .filter(turn -> !isHardSafetyStop(
                        safetyGate.evaluate(turn.customerMessage())
                ))
                .filter(turn -> !isHardSafetyStop(
                        safetyGate.evaluate(turn.assistantMessage())
                ))
                .toList();
    }

    private boolean shouldCompose(DemoCustomerChatTurnResponse response) {
        return Set.of("answered", "outside_authority")
                .contains(response.outcome())
                && !response.sources().isEmpty()
                && !response.verifiedClaims().isEmpty();
    }

    private DemoCustomerChatTurnResponse composeCustomerAnswer(
            DemoCustomerChatTurnResponse response,
            DemoCustomerChatTurnRequest request,
            List<DemoCustomerChatTurnRequest.ConversationTurn> recentConversation,
            String modelMessage,
            String routedIntent
    ) {
        if (answerGateway == null) {
            throw new LiveInvestigationException(
                    dev.shirwac.incidentdetective.live.LiveInvestigationFailure
                            .LIVE_AI_DISABLED,
                    "Customer answer gateway is unavailable"
            );
        }
        List<CustomerChatAnswerGateway.Evidence> evidence = response.sources()
                .stream()
                .limit(CustomerChatAnswerGateway.MAX_EVIDENCE_ITEMS)
                .map(source -> answerEvidence(response, source))
                .toList();
        List<CustomerChatAnswerGateway.Claim> verifiedClaims =
                "protected_boundary".equals(routedIntent)
                        ? List.of()
                        : response.verifiedClaims().stream()
                        .map(claim -> new CustomerChatAnswerGateway.Claim(
                                claim.textSv(),
                                claim.textEn(),
                                claim.citationIds()
                        ))
                        .toList();
        CustomerChatAnswerGateway.Result generated = answerGateway.generate(
                request.confirmLiveAi(),
                new CustomerChatAnswerGateway.Input(
                        modelMessage,
                        request.locale(),
                        routedIntent,
                        response.outcome(),
                        "conversation".equals(routedIntent)
                                ? recentConversation
                                : List.of(),
                        evidence,
                        verifiedClaims
                )
        );
        boolean protectedBoundary = "protected_boundary".equals(routedIntent);
        boolean safeProtectedBoundary = protectedBoundary
                && CustomerChatAnswerClaimCoverage.protectedBoundarySafe(
                generated.answer()
        );
        boolean claimsRequired = !"conversation".equals(routedIntent)
                && !verifiedClaims.isEmpty();
        boolean groundedStandardAnswer = !protectedBoundary
                && (!claimsRequired || !generated.answer().claims().isEmpty())
                && CustomerChatAnswerClaimCoverage.covers(generated.answer())
                && CustomerChatAnswerClaimCoverage.claimsWithinVerifiedSet(
                generated.answer(),
                verifiedClaims
        );
        if (!safeProtectedBoundary && !groundedStandardAnswer) {
            throw new ModelProviderException(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Customer chat answer contained factual text outside its cited claims"
            );
        }
        List<DemoCustomerChatTurnResponse.VerifiedClaim> generatedClaims =
                generated.answer().claims().stream()
                        .map(claim -> new DemoCustomerChatTurnResponse.VerifiedClaim(
                                claim.textSv(),
                                claim.textEn(),
                                claim.citationIds()
                        ))
                        .toList();
        List<DemoCustomerChatTurnResponse.VerifiedClaim> claims =
                "protected_boundary".equals(routedIntent)
                        && generatedClaims.isEmpty()
                        ? response.verifiedClaims()
                        : generatedClaims;
        List<String> citedEvidence = claims.stream()
                .flatMap(claim -> claim.citationIds().stream())
                .distinct()
                .toList();
        List<DemoCustomerChatTurnResponse.ToolEvent> events =
                new ArrayList<>(response.toolEvents());
        events.add(new DemoCustomerChatTurnResponse.ToolEvent(
                0,
                "generation",
                "gemini_customer_answer",
                false,
                "compose_customer_answer",
                "completed",
                true,
                "Gemini formulerade kundsvaret från exakt den verifierade evidens som Java tillät.",
                "Gemini composed the customer reply from exactly the verified evidence Java allowed.",
                null,
                citedEvidence,
                generated.provider().latencyMs()
        ));
        DemoCustomerChatTurnResponse.Verification prior =
                response.verification();
        DemoCustomerChatTurnResponse.Verification verification =
                new DemoCustomerChatTurnResponse.Verification(
                        "completed",
                        prior.fixedCustomerScope(),
                        prior.orderSourceVerified(),
                        prior.approvedPoliciesOnly(),
                        true,
                        prior.semanticClaimSupportEvaluated(),
                        true,
                        false,
                        "released_model_composed_from_bounded_evidence"
                );
        DemoCustomerChatTurnResponse.Receipt receipt = addModelCall(
                response.receipt(),
                generated.costEstimate(),
                aiProperties == null
                        ? generated.provider().modelVersion()
                        : aiProperties.modelId(),
                generated.provider().latencyMs()
        );
        return copyResponse(
                response,
                LIVE_AI_TRUTH_SV,
                LIVE_AI_TRUTH_EN,
                new DemoCustomerChatTurnResponse.AssistantMessage(
                        generated.answer().textSv(),
                        generated.answer().textEn()
                ),
                resequence(events),
                claims,
                verification,
                receipt,
                response.error()
        );
    }

    private DemoCustomerChatTurnResponse boundedSafetyResponse(
            String turnId,
            long started,
            DemoCustomerChatTurnRequest request,
            KnowledgeRagSafetyGate.Decision safety
    ) {
        DemoCustomerIntentClassifier.Decision intent =
                new DemoCustomerIntentClassifier.Decision(
                        DemoCustomerIntentClassifier.Intent.UNSUPPORTED,
                        "java_safety_boundary_v1",
                        false
                );
        List<DemoCustomerChatTurnResponse.ToolEvent> events = List.of(
                event(
                        1,
                        "safety",
                        "classify_protected_request",
                        "bounded",
                        true,
                        "Skyddsgränsen stoppade dataåtkomst och verktyg. Endast en maskerad riskklass fick gå vidare.",
                        "The safety boundary stopped data access and tools. Only a masked risk class could continue.",
                        null,
                        List.of(),
                        null
                ),
                event(
                        2,
                        "backend_read",
                        "read_public_data_boundary_policy",
                        "completed",
                        true,
                        "Läste endast den godkända offentliga policyn för ett naturligt gränssvar.",
                        "Read only the approved public policy for a natural boundary reply.",
                        dataBoundaryPolicy.sourceRef(),
                        List.of(dataBoundaryPolicy.evidenceId()),
                        null
                ),
                event(
                        3,
                        "verification",
                        "verify_no_protected_context_or_write_tools",
                        "completed",
                        true,
                        "Verifierade att inga privata källor, kundposter eller skrivverktyg var tillgängliga.",
                        "Verified that no private sources, customer records, or write tools were available.",
                        dataBoundaryPolicy.sourceRef(),
                        List.of(dataBoundaryPolicy.evidenceId()),
                        null
                )
        );
        List<DemoCustomerChatTurnResponse.VerifiedClaim> claims = List.of(
                new DemoCustomerChatTurnResponse.VerifiedClaim(
                        dataBoundaryPolicy.displaySummarySv(),
                        dataBoundaryPolicy.displaySummaryEn(),
                        List.of(dataBoundaryPolicy.evidenceId())
                )
        );
        return response(
                turnId,
                request,
                intent,
                safety,
                "outside_authority",
                refusalMessage(safety),
                null,
                events,
                List.of(policySource(dataBoundaryPolicy, null)),
                claims,
                noRag(),
                new DemoCustomerChatTurnResponse.Verification(
                        "completed",
                        true,
                        false,
                        true,
                        true,
                        false,
                        true,
                        false,
                        "released_bounded_policy_projection"
                ),
                receipt(1, 0, 0, 0, 0, started, null),
                null
        );
    }

    private String safeBoundaryPrompt(
            KnowledgeRagSafetyGate.Decision safety,
            String locale
    ) {
        String reason = switch (safety.reasonCode()) {
            case PII_REQUEST -> "protected_personal_information_request";
            case EMPLOYEE_COMPENSATION_REQUEST ->
                    "employee_compensation_request";
            case SECRET_REQUEST -> "secret_or_credential_request";
            case PROMPT_INJECTION -> "instruction_override_request";
            default -> "protected_request";
        };
        return "sv".equals(locale)
                ? "Svara vänligt och naturligt på en begäran med den säkra riskklassen "
                + reason
                + ". Förklara bara gränsen och erbjud dig att svara på en annan vanlig orderfråga. "
                + "Hänvisa inte till någon kontaktväg. Upprepa inte det efterfrågade personfältet, namn eller värden."
                : "Reply naturally and politely to a request with the safe risk class "
                + reason
                + ". Explain only the boundary and offer to answer another ordinary order question. "
                + "Do not give a contact route. Do not repeat the requested personal field, names or values.";
    }

    private CustomerChatAnswerGateway.Evidence answerEvidence(
            DemoCustomerChatTurnResponse response,
            DemoCustomerChatTurnResponse.Source source
    ) {
        String text;
        if ("customer_context".equals(source.kind())) {
            text = "Customer display name: "
                    + response.context().customerDisplayName()
                    + ". Preferred name: "
                    + response.context().customerPreferredName()
                    + ". Current order ID: "
                    + response.context().currentOrderId()
                    + ". Swedish source summary: "
                    + source.displaySummarySv()
                    + " English source summary: "
                    + source.displaySummaryEn();
        } else if ("order_snapshot".equals(source.kind())
                && response.order() != null) {
            DemoOrderSnapshot order = response.order();
            text = "Order ID: " + order.orderId()
                    + ". Market: " + order.market()
                    + ". Item count: " + order.itemCount()
                    + ". Items (sv): " + order.itemSummarySv()
                    + ". Items (en): " + order.itemSummaryEn()
                    + ". Status code: " + order.statusCode()
                    + ". Status (sv): " + order.statusSv()
                    + ". Status (en): " + order.statusEn()
                    + ". Delivery from: " + order.estimatedDeliveryFrom()
                    + ". Delivery through: "
                    + order.estimatedDeliveryThrough()
                    + ". Payment state: " + order.paymentState()
                    + ". Fulfilment state: " + order.fulfilmentState()
                    + ". Summary (sv): " + order.summarySv()
                    + ". Summary (en): " + order.summaryEn()
                    + ". Next step (sv): " + order.nextStepSv()
                    + ". Next step (en): " + order.nextStepEn();
        } else if ("company_policy".equals(source.kind())) {
            NordlyKnowledgeCorpus.EntryMetadata metadata = approved(
                    corpus.metadata(source.evidenceId()),
                    source.evidenceId()
            );
            text = "Approved source text: " + metadata.text()
                    + " Swedish source summary: "
                    + source.displaySummarySv()
                    + " English source summary: "
                    + source.displaySummaryEn();
        } else {
            text = "Swedish source summary: " + source.displaySummarySv()
                    + " English source summary: "
                    + source.displaySummaryEn();
        }
        return new CustomerChatAnswerGateway.Evidence(
                source.evidenceId(),
                source.title(),
                text
        );
    }

    private DemoCustomerChatTurnResponse compositionUnavailable(
            DemoCustomerChatTurnResponse response,
            RuntimeException exception
    ) {
        String failureCode = routingFailureCode(exception);
        LOGGER.warn(
                "Customer chat answer withheld: failureCode={}, type={}, detail={}",
                failureCode,
                exception.getClass().getSimpleName(),
                exception.getMessage()
        );
        List<DemoCustomerChatTurnResponse.ToolEvent> events =
                new ArrayList<>(response.toolEvents());
        events.add(new DemoCustomerChatTurnResponse.ToolEvent(
                0,
                "generation",
                "gemini_customer_answer",
                false,
                "compose_customer_answer",
                "failed",
                exception instanceof ModelProviderException,
                "Det naturliga kundsvaret kunde inte verifieras. Det förskrivna underlaget släpptes inte som ersättning.",
                "The natural customer reply could not be verified. The pre-composed projection was not released as a substitute.",
                null,
                List.of(),
                null
        ));
        DemoCustomerChatTurnResponse.Receipt receipt = response.receipt();
        if (exception instanceof ModelProviderException) {
            receipt = addUnknownModelCall(
                    receipt,
                    aiProperties == null ? null : aiProperties.modelId()
            );
        }
        return copyResponseWithOutcome(
                response,
                "unavailable",
                new DemoCustomerChatTurnResponse.AssistantMessage(
                        "Jag kunde inte skapa ett verifierat svar just nu och vill inte ersätta det med ett standardsvar. Försök gärna igen om en stund.",
                        "I could not create a verified reply right now and will not replace it with a canned answer. Please try again in a moment."
                ),
                resequence(events),
                List.of(),
                new DemoCustomerChatTurnResponse.Verification(
                        "not_run",
                        response.verification().fixedCustomerScope(),
                        response.verification().orderSourceVerified(),
                        response.verification().approvedPoliciesOnly(),
                        false,
                        false,
                        true,
                        false,
                        "not_released_customer_answer_composition_failed"
                ),
                receipt,
                new DemoCustomerChatTurnResponse.ErrorDetail(
                        "CUSTOMER_CHAT_ANSWER_" + failureCode,
                        "Det naturliga live-svaret kunde inte verifieras och inget reservsvar släpptes.",
                        "The natural live reply could not be verified and no fallback answer was released."
                )
        );
    }

    private DemoCustomerChatTurnResponse routingUnavailable(
            String turnId,
            long started,
            DemoCustomerChatTurnRequest request,
            KnowledgeRagSafetyGate.Decision safety,
            RuntimeException exception
    ) {
        String failureCode = routingFailureCode(exception);
        DemoCustomerIntentClassifier.Decision unavailableIntent =
                new DemoCustomerIntentClassifier.Decision(
                        DemoCustomerIntentClassifier.Intent.UNSUPPORTED,
                        CustomerChatModelRouter.CLASSIFIER + "_unavailable",
                        false
                );
        DemoCustomerChatTurnResponse.ToolEvent failureEvent =
                new DemoCustomerChatTurnResponse.ToolEvent(
                        2,
                        "model_routing",
                        "gemini_customer_router",
                        true,
                        "route_customer_message",
                        "failed",
                        exception instanceof ModelProviderException,
                        "AI-routern kunde inte användas. Ingen mall eller gissning visades som svar.",
                        "The AI router was unavailable. No template or guess was shown as an answer.",
                        null,
                        List.of(),
                        null
                );
        DemoCustomerChatTurnResponse.Receipt resultReceipt =
                receipt(0, 0, 0, 0, 0, started, null);
        resultReceipt =
                exception instanceof ModelProviderException
                        ? addUnknownModelCall(
                        resultReceipt,
                        aiProperties == null ? null : aiProperties.modelId()
                )
                        : resultReceipt;
        return response(
                turnId,
                request,
                unavailableIntent,
                safety,
                "unavailable",
                new DemoCustomerChatTurnResponse.AssistantMessage(
                        "Jag kunde inte tolka frågan säkert just nu och vill inte gissa. Försök gärna igen om en stund.",
                        "I could not interpret the question safely right now and will not guess. Please try again in a moment."
                ),
                null,
                List.of(safetyEvent(1, safety), failureEvent),
                List.of(),
                List.of(),
                noRag(),
                new DemoCustomerChatTurnResponse.Verification(
                        "not_run",
                        true,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        "not_released_router_failed"
                ),
                resultReceipt,
                new DemoCustomerChatTurnResponse.ErrorDetail(
                        "CUSTOMER_CHAT_ROUTER_" + failureCode,
                        "Live-AI kunde inte tolka frågan och inget kundsvar släpptes.",
                        "Live AI could not route the question and no customer answer was released."
                )
        );
    }

    private DemoCustomerChatTurnResponse downstreamUnavailable(
            String turnId,
            long started,
            DemoCustomerChatTurnRequest request,
            DemoCustomerIntentClassifier.Decision intent,
            KnowledgeRagSafetyGate.Decision safety,
            RuntimeException exception
    ) {
        String failureCode = routingFailureCode(exception);
        List<DemoCustomerChatTurnResponse.ToolEvent> events = List.of(
                safetyEvent(1, safety),
                contextEvent(2),
                new DemoCustomerChatTurnResponse.ToolEvent(
                        3,
                        "provider_boundary",
                        "spring_orchestrator",
                        false,
                        "run_selected_customer_capability",
                        "failed",
                        false,
                        "AI:n tolkade frågan, men nästa skyddade steg kunde inte starta. Inget svar hittades på och ingen ändring gjordes.",
                        "AI interpreted the question, but the next protected step could not start. No answer was invented and no change was made.",
                        null,
                        List.of(),
                        null
                )
        );
        return response(
                turnId,
                request,
                intent,
                safety,
                "unavailable",
                new DemoCustomerChatTurnResponse.AssistantMessage(
                        "Jag förstod frågan, men kunde inte starta den skyddade informationshämtningen just nu. Jag vill inte gissa. Ingen ändring gjordes.",
                        "I understood the question, but could not start the protected information retrieval right now. I will not guess. No change was made."
                ),
                null,
                events,
                List.of(contextSource()),
                List.of(),
                noRag(),
                new DemoCustomerChatTurnResponse.Verification(
                        "not_run",
                        true,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        "not_released_downstream_live_boundary"
                ),
                receipt(0, 0, 0, 0, 0, started, null),
                new DemoCustomerChatTurnResponse.ErrorDetail(
                        "CUSTOMER_CHAT_DOWNSTREAM_" + failureCode,
                        "Frågan tolkades, men det valda live-steget stoppades av systemets skyddsgräns.",
                        "The question was routed, but the selected live step was stopped by the system boundary."
                )
        );
    }

    private List<DemoCustomerChatTurnResponse.ToolEvent> insertAfterSafety(
            List<DemoCustomerChatTurnResponse.ToolEvent> existing,
            DemoCustomerChatTurnResponse.ToolEvent inserted
    ) {
        List<DemoCustomerChatTurnResponse.ToolEvent> result =
                new ArrayList<>(existing.size() + 1);
        boolean added = false;
        for (DemoCustomerChatTurnResponse.ToolEvent event : existing) {
            result.add(event);
            if (!added && "safety".equals(event.type())) {
                result.add(inserted);
                added = true;
            }
        }
        if (!added) {
            result.addFirst(inserted);
        }
        return resequence(result);
    }

    private List<DemoCustomerChatTurnResponse.ToolEvent> resequence(
            List<DemoCustomerChatTurnResponse.ToolEvent> events
    ) {
        List<DemoCustomerChatTurnResponse.ToolEvent> result =
                new ArrayList<>(events.size());
        int sequence = 1;
        for (DemoCustomerChatTurnResponse.ToolEvent event : events) {
            result.add(new DemoCustomerChatTurnResponse.ToolEvent(
                    sequence++,
                    event.type(),
                    event.initiatedBy(),
                    event.modelSelected(),
                    event.name(),
                    event.status(),
                    event.executed(),
                    event.summarySv(),
                    event.summaryEn(),
                    event.sourceRef(),
                    event.evidenceIds(),
                    event.latencyMs()
            ));
        }
        return List.copyOf(result);
    }

    private DemoCustomerChatTurnResponse.Receipt addModelCall(
            DemoCustomerChatTurnResponse.Receipt receipt,
            ModelCostEstimate estimate,
            String modelId
    ) {
        return addModelCall(receipt, estimate, modelId, 0);
    }

    private DemoCustomerChatTurnResponse.Receipt addModelCall(
            DemoCustomerChatTurnResponse.Receipt receipt,
            ModelCostEstimate estimate,
            String modelId,
            long additionalLatencyMs
    ) {
        BigDecimal combinedCost = combineKnownCosts(
                receipt.providerCalls(),
                receipt.estimatedCostUsd(),
                estimate == null ? null : estimate.estimatedUsd()
        );
        return new DemoCustomerChatTurnResponse.Receipt(
                receipt.readOperations(),
                receipt.providerCalls() + 1,
                receipt.embeddingCalls(),
                receipt.vectorSearches(),
                receipt.generationCalls() + 1,
                0,
                receipt.businessWriteScope(),
                false,
                false,
                false,
                receipt.totalLatencyMs() + Math.max(0, additionalLatencyMs),
                modelId,
                combinedCost,
                combinedCost == null
                        ? "provider_usage_not_reported"
                        : "estimated_model_generation_only",
                joinCostBasis(
                        receipt.providerCalls() == 0
                                ? null
                                : receipt.costBasis(),
                        estimate == null ? null : estimate.basis()
                )
        );
    }

    private DemoCustomerChatTurnResponse.Receipt addUnknownModelCall(
            DemoCustomerChatTurnResponse.Receipt receipt,
            String modelId
    ) {
        return addUnknownModelCall(receipt, modelId, 0);
    }

    private DemoCustomerChatTurnResponse.Receipt addUnknownModelCall(
            DemoCustomerChatTurnResponse.Receipt receipt,
            String modelId,
            long additionalLatencyMs
    ) {
        return new DemoCustomerChatTurnResponse.Receipt(
                receipt.readOperations(),
                receipt.providerCalls() + 1,
                receipt.embeddingCalls(),
                receipt.vectorSearches(),
                receipt.generationCalls() + 1,
                0,
                receipt.businessWriteScope(),
                false,
                false,
                false,
                receipt.totalLatencyMs() + Math.max(0, additionalLatencyMs),
                modelId,
                null,
                "provider_usage_not_reported",
                joinCostBasis(
                        receipt.providerCalls() == 0
                                ? null
                                : receipt.costBasis(),
                        "The attempted model call did not return usable usage metadata."
                )
        );
    }

    private BigDecimal combineKnownCosts(
            int existingProviderCalls,
            BigDecimal existing,
            BigDecimal added
    ) {
        if (added == null || (existingProviderCalls > 0 && existing == null)) {
            return null;
        }
        return (existing == null ? BigDecimal.ZERO : existing).add(added);
    }

    private String joinCostBasis(String first, String second) {
        return java.util.stream.Stream.of(first, second)
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private DemoCustomerChatTurnResponse copyResponse(
            DemoCustomerChatTurnResponse response,
            String truthLabel,
            String truthLabelEn,
            DemoCustomerChatTurnResponse.AssistantMessage assistant,
            List<DemoCustomerChatTurnResponse.ToolEvent> events,
            List<DemoCustomerChatTurnResponse.VerifiedClaim> claims,
            DemoCustomerChatTurnResponse.Verification verification,
            DemoCustomerChatTurnResponse.Receipt receipt,
            DemoCustomerChatTurnResponse.ErrorDetail error
    ) {
        return new DemoCustomerChatTurnResponse(
                response.contractVersion(),
                response.turnId(),
                response.mode(),
                truthLabel,
                truthLabelEn,
                response.outcome(),
                response.submittedMessage(),
                response.context(),
                response.intent(),
                response.safety(),
                assistant,
                response.order(),
                events,
                response.sources(),
                claims,
                response.rag(),
                verification,
                receipt,
                error,
                response.limitations()
        );
    }

    private DemoCustomerChatTurnResponse copyResponseWithOutcome(
            DemoCustomerChatTurnResponse response,
            String outcome,
            DemoCustomerChatTurnResponse.AssistantMessage assistant,
            List<DemoCustomerChatTurnResponse.ToolEvent> events,
            List<DemoCustomerChatTurnResponse.VerifiedClaim> claims,
            DemoCustomerChatTurnResponse.Verification verification,
            DemoCustomerChatTurnResponse.Receipt receipt,
            DemoCustomerChatTurnResponse.ErrorDetail error
    ) {
        return new DemoCustomerChatTurnResponse(
                response.contractVersion(),
                response.turnId(),
                response.mode(),
                response.truthLabel(),
                response.truthLabelEn(),
                outcome,
                response.submittedMessage(),
                response.context(),
                response.intent(),
                response.safety(),
                assistant,
                response.order(),
                events,
                response.sources(),
                claims,
                response.rag(),
                verification,
                receipt,
                error,
                response.limitations()
        );
    }

    private DemoCustomerChatTurnResponse refused(
            String turnId,
            long started,
            DemoCustomerChatTurnRequest request,
            DemoCustomerIntentClassifier.Decision intent,
            KnowledgeRagSafetyGate.Decision safety
    ) {
        List<DemoCustomerChatTurnResponse.ToolEvent> events = List.of(
                event(
                        1,
                        "safety",
                        "screen_customer_message",
                        "blocked",
                        true,
                        safety.summarySv(),
                        safety.summaryEn(),
                        null,
                        List.of(),
                        null
                )
        );
        return response(
                turnId,
                request,
                intent,
                safety,
                "refused",
                refusalMessage(safety),
                null,
                events,
                List.of(),
                List.of(),
                noRag(),
                new DemoCustomerChatTurnResponse.Verification(
                        "not_applicable",
                        true,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        "refused_before_customer_tools"
                ),
                receipt(0, 0, 0, 0, 0, started, null),
                null
        );
    }

    private DemoCustomerChatTurnResponse orderStatus(
            String turnId,
            long started,
            DemoCustomerChatTurnRequest request,
            DemoCustomerIntentClassifier.Decision intent,
            KnowledgeRagSafetyGate.Decision safety
    ) {
        DemoOrderSnapshot order = contextCatalog.currentOrder();
        List<DemoCustomerChatTurnResponse.Source> sources = List.of(
                contextSource(),
                orderSource(order)
        );
        List<DemoCustomerChatTurnResponse.ToolEvent> events = new ArrayList<>();
        events.add(safetyEvent(1, safety));
        events.add(contextEvent(2));
        events.add(event(
                3,
                "backend_read",
                "get_current_order",
                "completed",
                true,
                "Läste demokundens aktuella order från backendkatalogen.",
                "Read the demo customer's current order from the backend catalog.",
                order.sourceRef(),
                List.of(order.evidenceId()),
                null
        ));
        events.add(event(
                4,
                "verification",
                "verify_read_only_boundary",
                "completed",
                true,
                "Verifierade orderkällan och att ingen ändring kunde göras.",
                "Verified the order source and that no change could be made.",
                order.sourceRef(),
                List.of(order.evidenceId()),
                null
        ));
        return response(
                turnId,
                request,
                intent,
                safety,
                "answered",
                orderMessage(order),
                order,
                events,
                sources,
                orderClaims(order),
                noRag(),
                new DemoCustomerChatTurnResponse.Verification(
                        "completed",
                        true,
                        true,
                        false,
                        true,
                        false,
                        true,
                        false,
                        "released_exact_order_snapshot"
                ),
                receipt(1, 0, 0, 0, 0, started, null),
                null
        );
    }

    private DemoCustomerChatTurnResponse outsideAuthority(
            String turnId,
            long started,
            DemoCustomerChatTurnRequest request,
            DemoCustomerIntentClassifier.Decision intent,
            KnowledgeRagSafetyGate.Decision safety
    ) {
        DemoOrderSnapshot order = contextCatalog.currentOrder();
        NordlyKnowledgeCorpus.EntryMetadata actionPolicy = switch (
                intent.intent()
        ) {
            case CANCEL_ORDER -> cancellationPolicy;
            case RETURN_ORDER -> returnPolicy;
            case REFUND_ORDER, PURCHASE_ITEM,
                    CHANGE_DELIVERY_ADDRESS -> authorityPolicy;
            default -> throw new IllegalArgumentException(
                    "No action policy for " + intent.intent()
            );
        };
        boolean hasApprovedManualRoute =
                intent.intent() != DemoCustomerIntentClassifier.Intent.PURCHASE_ITEM;
        List<DemoCustomerChatTurnResponse.Source> sources = new ArrayList<>();
        sources.add(contextSource());
        sources.add(orderSource(order));
        sources.add(policySource(actionPolicy, null));
        if (hasApprovedManualRoute) {
            sources.add(policySource(contactPolicy, null));
        }
        List<DemoCustomerChatTurnResponse.ToolEvent> events = new ArrayList<>();
        events.add(safetyEvent(1, safety));
        events.add(contextEvent(2));
        events.add(event(
                3,
                "backend_read",
                "get_current_order",
                "completed",
                true,
                "Läste orderstatus innan befogenheten bedömdes.",
                "Read the order status before evaluating authority.",
                order.sourceRef(),
                List.of(order.evidenceId()),
                null
        ));
        events.add(event(
                4,
                "backend_read",
                "read_approved_policy",
                "completed",
                true,
                "Läste företagets godkända regel för den begärda åtgärden.",
                "Read the approved company rule for the requested action.",
                actionPolicy.sourceRef(),
                List.of(actionPolicy.evidenceId()),
                null
        ));
        int verificationSequence = 5;
        if (hasApprovedManualRoute) {
            events.add(event(
                    verificationSequence++,
                    "backend_read",
                    "read_manual_support_route",
                    "completed",
                    true,
                    "Läste den fiktiva vägen till mänsklig hjälp.",
                    "Read the approved route to human support.",
                    contactPolicy.sourceRef(),
                    List.of(contactPolicy.evidenceId()),
                    null
            ));
        }
        events.add(event(
                verificationSequence,
                "verification",
                "verify_read_only_boundary",
                "completed",
                true,
                "Ingen skrivfunktion fanns och ingen order ändrades.",
                "No write function was available and no order was changed.",
                null,
                List.of(),
                null
        ));
        return response(
                turnId,
                request,
                intent,
                safety,
                "outside_authority",
                authorityMessage(intent),
                order,
                events,
                sources,
                authorityClaims(
                        intent,
                        order,
                        actionPolicy,
                        hasApprovedManualRoute ? contactPolicy : null
                ),
                noRag(),
                new DemoCustomerChatTurnResponse.Verification(
                        "completed",
                        true,
                        true,
                        true,
                        true,
                        false,
                        true,
                        false,
                        "released_outside_authority"
                ),
                receipt(
                        hasApprovedManualRoute ? 3 : 2,
                        0,
                        0,
                        0,
                        0,
                        started,
                        null
                ),
                null
        );
    }

    private DemoCustomerChatTurnResponse conversation(
            String turnId,
            long started,
            DemoCustomerChatTurnRequest request,
            DemoCustomerIntentClassifier.Decision intent,
            KnowledgeRagSafetyGate.Decision safety,
            CustomerChatModelRouter.ConversationKind conversationKind
    ) {
        DemoCustomerChatTurnResponse.AssistantMessage assistantMessage =
                conversationKind == CustomerChatModelRouter.ConversationKind.THANKS
                        ? new DemoCustomerChatTurnResponse.AssistantMessage(
                                "Varsågod! Om du vill kan jag också kontrollera din order eller förklara Nordlys godkända kundregler.",
                                "You're welcome! If you'd like, I can also check your order or explain Nordly's approved customer policies."
                        )
                        : new DemoCustomerChatTurnResponse.AssistantMessage(
                                "Hej! Jag kan hjälpa dig att kontrollera din order eller förklara Nordlys godkända kundregler.",
                                "Hi! I can help check your order or explain Nordly's approved customer policies."
                        );
        List<DemoCustomerChatTurnResponse.Source> sources = List.of(
                contextSource(),
                policySource(authorityPolicy, null)
        );
        List<DemoCustomerChatTurnResponse.ToolEvent> events = List.of(
                safetyEvent(1, safety),
                contextEvent(2),
                event(
                        3,
                        "backend_read",
                        "read_assistant_scope",
                        "completed",
                        true,
                        "Läste assistentens godkända roll och befogenhet.",
                        "Read the assistant's approved role and authority.",
                        authorityPolicy.sourceRef(),
                        List.of(authorityPolicy.evidenceId()),
                        null
                )
        );
        return response(
                turnId,
                request,
                intent,
                safety,
                "answered",
                assistantMessage,
                null,
                events,
                sources,
                List.of(new DemoCustomerChatTurnResponse.VerifiedClaim(
                        authorityPolicy.displaySummarySv(),
                        authorityPolicy.displaySummaryEn(),
                        List.of(authorityPolicy.evidenceId())
                )),
                noRag(),
                new DemoCustomerChatTurnResponse.Verification(
                        "completed",
                        true,
                        false,
                        true,
                        true,
                        false,
                        true,
                        false,
                        "released_bounded_conversation"
                ),
                receipt(1, 0, 0, 0, 0, started, null),
                null
        );
    }

    private DemoCustomerChatTurnResponse clarificationRequired(
            String turnId,
            long started,
            DemoCustomerChatTurnRequest request,
            DemoCustomerIntentClassifier.Decision intent,
            KnowledgeRagSafetyGate.Decision safety
    ) {
        List<DemoCustomerChatTurnResponse.ToolEvent> events = List.of(
                safetyEvent(1, safety),
                contextEvent(2),
                event(
                        3,
                        "verification",
                        "request_customer_clarification",
                        "clarification_required",
                        true,
                        "Ingen tidigare handling antogs. Kunden ombads välja vad som ska kontrolleras.",
                        "No earlier action was assumed. The customer was asked what should be checked.",
                        null,
                        List.of(),
                        null
                )
        );
        return response(
                turnId,
                request,
                intent,
                safety,
                "clarification_required",
                new DemoCustomerChatTurnResponse.AssistantMessage(
                        "Jag vill inte gissa vad du menar. Vill du att jag kontrollerar leveransen, förklarar returreglerna eller förklarar hur avbeställning fungerar?",
                        "I do not want to guess what you mean. Would you like me to check the delivery, explain the return rules, or explain how cancellation works?"
                ),
                null,
                events,
                List.of(contextSource()),
                List.of(),
                noRag(),
                new DemoCustomerChatTurnResponse.Verification(
                        "not_applicable",
                        true,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        "released_clarification_request"
                ),
                receipt(0, 0, 0, 0, 0, started, null),
                null
        );
    }

    private DemoCustomerChatTurnResponse policyAnswer(
            String turnId,
            long started,
            DemoCustomerChatTurnRequest request,
            DemoCustomerIntentClassifier.Decision intent,
            KnowledgeRagSafetyGate.Decision safety
    ) {
        DemoOrderSnapshot order = contextCatalog.currentOrder();
        KnowledgeRagResponse rag = knowledgeRagService.ask(
                new KnowledgeRagRequest(
                        boundedPolicyQuestion(intent.intent(), request.locale()),
                        request.locale(),
                        request.confirmLiveAi()
                )
        );
        List<DemoCustomerChatTurnResponse.Source> sources = new ArrayList<>();
        sources.add(contextSource());
        sources.add(orderSource(order));
        sources.addAll(ragSources(rag));
        List<DemoCustomerChatTurnResponse.ToolEvent> events = new ArrayList<>();
        events.add(safetyEvent(1, safety));
        events.add(contextEvent(2));
        events.add(event(
                3,
                "backend_read",
                "get_current_order",
                "completed",
                true,
                "Läste den aktuella ordern innan policyn söktes.",
                "Read the current order before searching policy.",
                order.sourceRef(),
                List.of(order.evidenceId()),
                null
        ));
        appendRagEvents(events, rag, 4);

        List<KnowledgeRagResponse.AnswerClaim> policyClaims = requiredPolicyClaims(
                intent.intent(),
                rag
        );
        boolean releasable = answerReleasable(rag)
                && !policyClaims.isEmpty();
        String outcome = customerOutcome(rag, releasable);
        DemoCustomerChatTurnResponse.AssistantMessage assistant =
                policyAssistant(intent.intent(), rag, policyClaims, outcome);
        DemoCustomerChatTurnResponse.ErrorDetail error = ragError(
                rag,
                releasable
        );
        boolean citationsWithinSources = citationsWithinSources(rag, sources);
        boolean approvedOnly = rag.verification().approvedDocumentsOnly()
                && sources.stream()
                .filter(source -> "company_policy".equals(source.kind()))
                .allMatch(source -> "APPROVED".equals(source.lifecycle()));
        int vectorSearches = rag.retrieval().currentVectorSearch() ? 1 : 0;

        return response(
                turnId,
                request,
                intent,
                safety,
                outcome,
                assistant,
                order,
                events,
                sources,
                verifiedClaims(policyClaims, releasable),
                ragExecution(rag),
                new DemoCustomerChatTurnResponse.Verification(
                        releasable
                                ? "completed"
                                : rag.verification().evaluationStatus(),
                        true,
                        true,
                        approvedOnly,
                        citationsWithinSources,
                        rag.verification().semanticClaimSupportEvaluated(),
                        true,
                        false,
                        verificationOutcome(rag, releasable)
                ),
                receipt(
                        1 + vectorSearches,
                        rag.receipt().providerCalls(),
                        rag.receipt().embeddingCalls(),
                        vectorSearches,
                        rag.receipt().generationCalls(),
                        started,
                        rag
                ),
                error
        );
    }

    private DemoCustomerChatTurnResponse companyKnowledge(
            String turnId,
            long started,
            DemoCustomerChatTurnRequest request,
            DemoCustomerIntentClassifier.Decision intent,
            KnowledgeRagSafetyGate.Decision safety
    ) {
        KnowledgeRagResponse rag = knowledgeRagService.ask(
                new KnowledgeRagRequest(
                        request.message().strip(),
                        request.locale(),
                        request.confirmLiveAi()
                )
        );
        List<DemoCustomerChatTurnResponse.Source> sources = new ArrayList<>();
        sources.add(contextSource());
        sources.addAll(ragSources(rag));
        List<DemoCustomerChatTurnResponse.ToolEvent> events = new ArrayList<>();
        events.add(safetyEvent(1, safety));
        events.add(contextEvent(2));
        appendRagEvents(events, rag, 3);

        List<KnowledgeRagResponse.AnswerClaim> claims = rag.answer() == null
                || rag.answer().claims() == null
                ? List.of()
                : rag.answer().claims();
        boolean releasable = answerReleasable(rag) && !claims.isEmpty();
        String outcome = customerOutcome(rag, releasable);
        boolean citationsWithinSources = citationsWithinSources(rag, sources);
        boolean approvedOnly = rag.verification().approvedDocumentsOnly()
                && sources.stream()
                .filter(source -> "company_policy".equals(source.kind()))
                .allMatch(source -> "APPROVED".equals(source.lifecycle()));
        int vectorSearches = rag.retrieval().currentVectorSearch() ? 1 : 0;

        return response(
                turnId,
                request,
                intent,
                safety,
                outcome,
                knowledgeAssistant(rag, outcome),
                null,
                events,
                sources,
                verifiedClaims(claims, releasable),
                ragExecution(rag),
                new DemoCustomerChatTurnResponse.Verification(
                        releasable
                                ? "completed"
                                : rag.verification().evaluationStatus(),
                        true,
                        false,
                        approvedOnly,
                        citationsWithinSources,
                        rag.verification().semanticClaimSupportEvaluated(),
                        true,
                        false,
                        releasable
                                ? "released_grounded_company_answer"
                                : rag.verification().overallOutcome()
                ),
                receipt(
                        vectorSearches,
                        rag.receipt().providerCalls(),
                        rag.receipt().embeddingCalls(),
                        vectorSearches,
                        rag.receipt().generationCalls(),
                        started,
                        rag
                ),
                ragError(rag, releasable)
        );
    }

    private DemoCustomerChatTurnResponse unsupported(
            String turnId,
            long started,
            DemoCustomerChatTurnRequest request,
            DemoCustomerIntentClassifier.Decision intent,
            KnowledgeRagSafetyGate.Decision safety
    ) {
        List<DemoCustomerChatTurnResponse.ToolEvent> events = List.of(
                safetyEvent(1, safety),
                contextEvent(2),
                event(
                        3,
                        "verification",
                        "classify_customer_intent",
                        "unsupported",
                        true,
                        "Frågan låg utanför order, retur och återbetalning.",
                        "The question was outside order, return and refund support.",
                        null,
                        List.of(),
                        null
                )
        );
        return response(
                turnId,
                request,
                intent,
                safety,
                "unsupported",
                new DemoCustomerChatTurnResponse.AssistantMessage(
                        "Jag kan hjälpa dig med din order, leverans, retur eller återbetalningsregler. Jag vill inte gissa utanför det området.",
                        "I can help with your order, delivery, return or refund rules. I will not guess outside that area."
                ),
                null,
                events,
                List.of(contextSource()),
                List.of(),
                noRag(),
                new DemoCustomerChatTurnResponse.Verification(
                        "not_applicable",
                        true,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        "released_unsupported_scope"
                ),
                receipt(0, 0, 0, 0, 0, started, null),
                null
        );
    }

    private DemoCustomerChatTurnResponse response(
            String turnId,
            DemoCustomerChatTurnRequest request,
            DemoCustomerIntentClassifier.Decision intent,
            KnowledgeRagSafetyGate.Decision safety,
            String outcome,
            DemoCustomerChatTurnResponse.AssistantMessage assistant,
            DemoOrderSnapshot order,
            List<DemoCustomerChatTurnResponse.ToolEvent> events,
            List<DemoCustomerChatTurnResponse.Source> sources,
            List<DemoCustomerChatTurnResponse.VerifiedClaim> verifiedClaims,
            DemoCustomerChatTurnResponse.RagExecution rag,
            DemoCustomerChatTurnResponse.Verification verification,
            DemoCustomerChatTurnResponse.Receipt receipt,
            DemoCustomerChatTurnResponse.ErrorDetail error
    ) {
        boolean redacted = shouldRedact(safety);
        String submittedText = redacted
                ? "sv".equals(request.locale()) ? REDACTED_SV : REDACTED_EN
                : request.message().strip();
        boolean liveAi = receipt.providerCalls() > 0;
        return new DemoCustomerChatTurnResponse(
                DemoCustomerChatTurnResponse.CONTRACT_VERSION,
                turnId,
                DemoCustomerChatTurnResponse.MODE,
                liveAi ? LIVE_AI_TRUTH_SV : NO_AI_TRUTH_SV,
                liveAi ? LIVE_AI_TRUTH_EN : NO_AI_TRUTH_EN,
                outcome,
                new DemoCustomerChatTurnResponse.SubmittedMessage(
                        submittedText,
                        request.locale(),
                        redacted
                ),
                contextReceipt(),
                new DemoCustomerChatTurnResponse.IntentDecision(
                        intent.intent().value(),
                        intent.classifier(),
                        intent.actionRequested()
                ),
                safety(safety),
                assistant,
                order,
                events,
                sources,
                verifiedClaims,
                rag,
                verification,
                receipt,
                error,
                LIMITATIONS
        );
    }

    private DemoCustomerChatTurnResponse.ContextReceipt contextReceipt() {
        DemoOrderSnapshot order = contextCatalog.currentOrder();
        return new DemoCustomerChatTurnResponse.ContextReceipt(
                contextCatalog.contextVersion(),
                contextCatalog.contextId(),
                contextCatalog.customerDisplayName(),
                contextCatalog.customerPreferredName(),
                order.orderId(),
                contextCatalog.sourceRef(),
                order.sourceRef(),
                true,
                false,
                "request_scoped_bounded_history"
        );
    }

    private DemoCustomerChatTurnResponse.AssistantMessage orderMessage(
            DemoOrderSnapshot order
    ) {
        String itemsSv = order.itemCount() == 1
                ? "1 vara"
                : order.itemCount() + " varor";
        String itemsEn = order.itemCount() == 1
                ? "1 item"
                : order.itemCount() + " items";
        return new DemoCustomerChatTurnResponse.AssistantMessage(
                "Hej " + contextCatalog.customerPreferredName()
                        + "! Jag har kontrollerat din order " + order.orderId()
                        + ". Orderstatus: " + order.statusSv()
                        + ". Den beräknas komma "
                        + dateRangeSv(
                        order.estimatedDeliveryFrom(),
                        order.estimatedDeliveryThrough()
                ) + ". Ordern innehåller " + itemsSv + ": "
                        + order.itemSummarySv() + ". " + order.nextStepSv(),
                "Hi " + contextCatalog.customerPreferredName()
                        + "! I checked your order " + order.orderId()
                        + ". Order status: " + order.statusEn()
                        + ". It is expected to arrive "
                        + dateRangeEn(
                        order.estimatedDeliveryFrom(),
                        order.estimatedDeliveryThrough()
                ) + ". The order contains " + itemsEn + ": "
                        + order.itemSummaryEn() + ". " + order.nextStepEn()
        );
    }

    private List<DemoCustomerChatTurnResponse.VerifiedClaim> orderClaims(
            DemoOrderSnapshot order
    ) {
        return List.of(
                new DemoCustomerChatTurnResponse.VerifiedClaim(
                        "Du har beställt " + order.itemSummarySv() + ".",
                        "You ordered " + order.itemSummaryEn() + ".",
                        List.of(order.evidenceId())
                ),
                new DemoCustomerChatTurnResponse.VerifiedClaim(
                        "Paketet är skickat och beräknas komma " + dateRangeSv(
                                order.estimatedDeliveryFrom(),
                                order.estimatedDeliveryThrough()
                        ) + ".",
                        "Your parcel has shipped and is expected to arrive "
                                + dateRangeEn(
                                order.estimatedDeliveryFrom(),
                                order.estimatedDeliveryThrough()
                        ) + ".",
                        List.of(order.evidenceId())
                )
        );
    }

    private List<DemoCustomerChatTurnResponse.VerifiedClaim> authorityClaims(
            DemoCustomerIntentClassifier.Decision intent,
            DemoOrderSnapshot order,
            NordlyKnowledgeCorpus.EntryMetadata actionPolicy,
            NordlyKnowledgeCorpus.EntryMetadata supportPolicy
    ) {
        String actionSv = switch (intent.intent()) {
            case CANCEL_ORDER ->
                    "Jag kan inte avbeställa eller ändra ordern här; ingen ändring har gjorts.";
            case RETURN_ORDER ->
                    "Jag kan inte skapa eller godkänna en retur här; ingen ändring har gjorts.";
            case REFUND_ORDER ->
                    "Jag kan inte godkänna eller genomföra en återbetalning här; ingen ändring har gjorts.";
            case PURCHASE_ITEM ->
                    "Jag kan inte skapa köp eller lägga till varor här; ingen ändring har gjorts.";
            case CHANGE_DELIVERY_ADDRESS ->
                    "Jag kan inte ändra leveransadressen här; ingen ändring har gjorts.";
            default -> throw new IllegalArgumentException(
                    "No authority claim for " + intent.intent()
            );
        };
        String actionEn = switch (intent.intent()) {
            case CANCEL_ORDER ->
                    "I cannot cancel or change the order here; no change was made.";
            case RETURN_ORDER ->
                    "I cannot create or approve a return here; no change was made.";
            case REFUND_ORDER ->
                    "I cannot approve or issue a refund here; no change was made.";
            case PURCHASE_ITEM ->
                    "I cannot create purchases or add items here; no change was made.";
            case CHANGE_DELIVERY_ADDRESS ->
                    "I cannot change the delivery address here; no change was made.";
            default -> throw new IllegalArgumentException(
                    "No authority claim for " + intent.intent()
            );
        };
        List<DemoCustomerChatTurnResponse.VerifiedClaim> claims =
                new ArrayList<>();
        claims.add(new DemoCustomerChatTurnResponse.VerifiedClaim(
                "Din order " + order.orderId() + " har statusen "
                        + order.statusSv() + ".",
                "Your order " + order.orderId() + " has status "
                        + order.statusEn() + ".",
                List.of(order.evidenceId())
        ));
        claims.add(new DemoCustomerChatTurnResponse.VerifiedClaim(
                actionSv,
                actionEn,
                List.of(actionPolicy.evidenceId())
        ));
        if (supportPolicy != null) {
            claims.add(new DemoCustomerChatTurnResponse.VerifiedClaim(
                    "Du kan ringa 123 så hjälper kundservice dig vidare.",
                    "You can call 123 and customer service will help you further.",
                    List.of(supportPolicy.evidenceId())
            ));
        }
        return List.copyOf(claims);
    }

    private List<DemoCustomerChatTurnResponse.VerifiedClaim> verifiedClaims(
            List<KnowledgeRagResponse.AnswerClaim> policyClaims,
            boolean releasable
    ) {
        if (!releasable) {
            return List.of();
        }
        return policyClaims.stream()
                .map(claim -> new DemoCustomerChatTurnResponse.VerifiedClaim(
                        claim.textSv(),
                        claim.textEn(),
                        claim.citationIds()
                ))
                .toList();
    }

    private DemoCustomerChatTurnResponse.AssistantMessage authorityMessage(
            DemoCustomerIntentClassifier.Decision intent
    ) {
        return switch (intent.intent()) {
            case CANCEL_ORDER -> new DemoCustomerChatTurnResponse.AssistantMessage(
                    "Jag förstår att du vill avbeställa ordern. Paketet är "
                            + "redan på väg. Det ligger utanför min befogenhet "
                            + "att avbeställa eller ändra det. Ingen ändring "
                            + "gjordes. Ring 123 så hjälper vi dig vidare.",
                    "I understand that you want to cancel the order. The parcel "
                            + "is already on its way, and I am not authorized to "
                            + "cancel or change it. No change was made. Call 123 "
                            + "and our support team can help you further."
            );
            case RETURN_ORDER -> new DemoCustomerChatTurnResponse.AssistantMessage(
                    "Jag förstår att du vill göra en retur. Jag kan förklara "
                            + "returreglerna, men det ligger utanför min befogenhet "
                            + "att skapa eller godkänna returen. Ingen ändring "
                            + "gjordes. Ring 123 så hjälper vi dig vidare.",
                    "I understand that you want to make a return. I can explain "
                            + "the return rules, but I am not authorized to create "
                            + "or approve the return. No change was made. Call 123 "
                            + "and our support team can help you further."
            );
            case REFUND_ORDER -> new DemoCustomerChatTurnResponse.AssistantMessage(
                    "Jag förstår att du vill ha en återbetalning. Jag kan "
                            + "förklara reglerna, men det ligger utanför min "
                            + "befogenhet att godkänna eller genomföra den. Ingen "
                            + "ändring gjordes. Ring 123 så hjälper vi dig vidare.",
                    "I understand that you want a refund. I can explain the "
                            + "rules, but I am not authorized to approve or issue "
                            + "a refund. No change was made. Call 123 and our "
                            + "support team can help you further."
            );
            case PURCHASE_ITEM -> new DemoCustomerChatTurnResponse.AssistantMessage(
                    "Jag förstår att du vill beställa en vara till. Den här "
                            + "chatten kan läsa din befintliga order, men den "
                            + "kan inte skapa köp eller lägga till varor. Ingen "
                            + "ändring gjordes. Jag kan gärna hjälpa dig med "
                            + "statusen på ordern som redan finns.",
                    "I understand that you want to order another item. This "
                            + "chat can read your existing order, but it cannot "
                            + "create purchases or add items. No change was made. "
                            + "I can help with the status of the order you already have."
            );
            case CHANGE_DELIVERY_ADDRESS -> new DemoCustomerChatTurnResponse.AssistantMessage(
                    "Jag förstår att du vill ändra leveransadressen, men det "
                            + "ligger utanför min befogenhet. Ingen ändring gjordes. "
                            + "Ring 123 så hjälper vi dig vidare.",
                    "I understand that you want to change the delivery address, "
                            + "but changing it is outside my authority. I have "
                            + "not changed the order. Call 123 and our support "
                            + "team can help you further."
            );
            default -> throw new IllegalArgumentException(
                    "No authority message for " + intent.intent()
            );
        };
    }

    private DemoCustomerChatTurnResponse.AssistantMessage policyAssistant(
            DemoCustomerIntentClassifier.Intent intent,
            KnowledgeRagResponse rag,
            List<KnowledgeRagResponse.AnswerClaim> policyClaims,
            String outcome
    ) {
        if ("answered".equals(outcome) && !policyClaims.isEmpty()) {
            String claimsSv = joinClaimText(policyClaims, true);
            String claimsEn = joinClaimText(policyClaims, false);
            if (intent == DemoCustomerIntentClassifier.Intent.CANCELLATION_POLICY) {
                return new DemoCustomerChatTurnResponse.AssistantMessage(
                        "Paketet är redan på väg. " + claimsSv
                                + " Jag kan förklara regeln, men jag kan inte avbeställa eller ändra ordern.",
                        "The parcel is already on its way. "
                                + claimsEn
                                + " I can explain the rule but cannot cancel or change the order."
                );
            }
            if (intent == DemoCustomerIntentClassifier.Intent.RETURN_POLICY) {
                return new DemoCustomerChatTurnResponse.AssistantMessage(
                        claimsSv
                                + " Jag kan förklara kriterierna, men jag kan inte skapa eller godkänna en retur.",
                        claimsEn
                                + " I can explain the criteria but cannot create or approve a return."
                );
            }
            return new DemoCustomerChatTurnResponse.AssistantMessage(
                    claimsSv
                            + " Jag kan förklara tiden, men jag kan inte godkänna eller genomföra en återbetalning.",
                    claimsEn
                            + " I can explain the timing but cannot approve or issue a refund."
            );
        }
        if ("confirmation_required".equals(outcome)) {
            return new DemoCustomerChatTurnResponse.AssistantMessage(
                    "Jag behöver söka i Nordlys företagsdokument för att svara säkert. Vill du fortsätta?",
                    "I need to search Nordly's company documents to answer safely. Would you like to continue?"
            );
        }
        if ("insufficient_evidence".equals(outcome)) {
            return new DemoCustomerChatTurnResponse.AssistantMessage(
                    "Jag hittade inget tillräckligt säkert stöd i Nordlys godkända dokument och tänker därför inte gissa.",
                    "I did not find sufficiently safe support in Nordly's approved documents, so I will not guess."
            );
        }
        if (rag.error() != null) {
            return new DemoCustomerChatTurnResponse.AssistantMessage(
                    rag.error().summarySv(),
                    rag.error().summaryEn()
            );
        }
        return new DemoCustomerChatTurnResponse.AssistantMessage(
                "Det verifierade policysvaret kunde inte visas.",
                "The verified policy answer could not be displayed."
        );
    }

    private DemoCustomerChatTurnResponse.AssistantMessage knowledgeAssistant(
            KnowledgeRagResponse rag,
            String outcome
    ) {
        if ("answered".equals(outcome) && rag.answer() != null) {
            return new DemoCustomerChatTurnResponse.AssistantMessage(
                    rag.answer().summarySv(),
                    rag.answer().summaryEn()
            );
        }
        if ("confirmation_required".equals(outcome)) {
            return new DemoCustomerChatTurnResponse.AssistantMessage(
                    "Jag behöver söka i Nordlys godkända dokument för att svara säkert. Vill du fortsätta?",
                    "I need to search Nordly's approved documents to answer safely. Would you like to continue?"
            );
        }
        if ("insufficient_evidence".equals(outcome)) {
            return new DemoCustomerChatTurnResponse.AssistantMessage(
                    "Jag hittar inget tillräckligt säkert stöd i Nordlys godkända dokument och vill därför inte gissa.",
                    "I cannot find sufficiently safe support in Nordly's approved documents, so I will not guess."
            );
        }
        if (rag.error() != null) {
            return new DemoCustomerChatTurnResponse.AssistantMessage(
                    rag.error().summarySv(),
                    rag.error().summaryEn()
            );
        }
        return new DemoCustomerChatTurnResponse.AssistantMessage(
                "Det verifierade svaret kunde inte visas just nu.",
                "The verified answer could not be displayed right now."
        );
    }

    private String joinClaimText(
            List<KnowledgeRagResponse.AnswerClaim> policyClaims,
            boolean swedish
    ) {
        return policyClaims.stream()
                .map(claim -> swedish ? claim.textSv() : claim.textEn())
                .filter(text -> text != null && !text.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private String customerOutcome(
            KnowledgeRagResponse rag,
            boolean releasable
    ) {
        if ("answered".equals(rag.outcome())) {
            return releasable ? "answered" : "insufficient_evidence";
        }
        return switch (rag.outcome()) {
            case "confirmation_required" -> "confirmation_required";
            case "insufficient_evidence" -> "insufficient_evidence";
            case "refused" -> "refused";
            default -> "unavailable";
        };
    }

    private String verificationOutcome(
            KnowledgeRagResponse rag,
            boolean releasable
    ) {
        if ("answered".equals(rag.outcome()) && !releasable) {
            return "withheld_missing_required_policy_evidence";
        }
        return releasable
                ? "released_canonical_policy_projection"
                : rag.verification().overallOutcome();
    }

    private DemoCustomerChatTurnResponse.ErrorDetail ragError(
            KnowledgeRagResponse rag,
            boolean releasable
    ) {
        if ("answered".equals(rag.outcome()) && !releasable) {
            return new DemoCustomerChatTurnResponse.ErrorDetail(
                    "CUSTOMER_CHAT_POLICY_EVIDENCE_MISSING",
                    "Det verifierade svaret saknade den regel som krävdes för frågan.",
                    "The verified answer did not include the policy required for this question."
            );
        }
        if (rag.error() == null) {
            return null;
        }
        return new DemoCustomerChatTurnResponse.ErrorDetail(
                rag.error().code(),
                rag.error().summarySv(),
                rag.error().summaryEn()
        );
    }

    private boolean answerReleasable(KnowledgeRagResponse rag) {
        return "answered".equals(rag.outcome())
                && rag.answer() != null
                && "answered".equals(rag.answer().status())
                && "completed".equals(
                rag.verification().evaluationStatus()
        )
                && rag.verification().schemaPass()
                && rag.verification().citationsWithinRetrievedContext()
                && rag.verification().approvedDocumentsOnly()
                && rag.verification().outputPiiScanPass()
                && rag.verification().outputPolicyScanPass()
                && rag.verification().noWriteCapability()
                && !rag.receipt().writeToolsAvailable()
                && !rag.receipt().actionExecuted();
    }

    private List<KnowledgeRagResponse.AnswerClaim> requiredPolicyClaims(
            DemoCustomerIntentClassifier.Intent intent,
            KnowledgeRagResponse rag
    ) {
        if (rag.answer() == null || rag.answer().claims() == null) {
            return List.of();
        }
        String requiredEvidence = requiredPolicyEvidence(intent);
        boolean selectedByModel = rag.answer().claims().stream()
                .filter(java.util.Objects::nonNull)
                .filter(claim -> claim.citationIds() != null)
                .filter(claim -> claim.citationIds().contains(requiredEvidence))
                .findAny()
                .isPresent();
        boolean returnedBySearch = rag.retrieval() != null
                && rag.retrieval().rankedMatches() != null
                && rag.retrieval().rankedMatches().stream()
                .anyMatch(match -> requiredEvidence.equals(match.evidenceId()));
        if (!selectedByModel || !returnedBySearch) {
            return List.of();
        }
        NordlyKnowledgeCorpus.EntryMetadata metadata = policyMetadata(intent);
        return List.of(new KnowledgeRagResponse.AnswerClaim(
                metadata.displaySummarySv(),
                metadata.displaySummaryEn(),
                List.of(metadata.evidenceId())
        ));
    }

    private NordlyKnowledgeCorpus.EntryMetadata policyMetadata(
            DemoCustomerIntentClassifier.Intent intent
    ) {
        return switch (intent) {
            case CANCELLATION_POLICY -> cancellationPolicy;
            case RETURN_POLICY -> returnPolicy;
            case REFUND_POLICY -> refundTimingPolicy;
            default -> throw new IllegalArgumentException(
                    "No policy metadata for " + intent
            );
        };
    }

    private String requiredPolicyEvidence(
            DemoCustomerIntentClassifier.Intent intent
    ) {
        return switch (intent) {
            case CANCELLATION_POLICY -> CANCELLATION_EVIDENCE;
            case RETURN_POLICY -> RETURN_EVIDENCE;
            case REFUND_POLICY -> REFUND_TIMING_EVIDENCE;
            default -> throw new IllegalArgumentException(
                    "No required policy evidence for " + intent
            );
        };
    }

    private boolean citationsWithinSources(
            KnowledgeRagResponse rag,
            List<DemoCustomerChatTurnResponse.Source> sources
    ) {
        if (rag.answer() == null) {
            return false;
        }
        Set<String> sourceIds = sources.stream()
                .map(DemoCustomerChatTurnResponse.Source::evidenceId)
                .collect(java.util.stream.Collectors.toSet());
        return rag.answer().claims().stream()
                .flatMap(claim -> claim.citationIds().stream())
                .allMatch(sourceIds::contains);
    }

    private List<DemoCustomerChatTurnResponse.Source> ragSources(
            KnowledgeRagResponse rag
    ) {
        if (rag.retrieval() == null
                || rag.retrieval().rankedMatches() == null) {
            return List.of();
        }
        return rag.retrieval().rankedMatches().stream()
                .map(match -> new DemoCustomerChatTurnResponse.Source(
                        "company_policy",
                        match.documentId(),
                        match.chunkId(),
                        match.documentVersion(),
                        match.title(),
                        match.sectionHeading(),
                        match.sourceRef(),
                        match.evidenceId(),
                        match.status(),
                        match.similarity(),
                        match.displaySummarySv(),
                        match.displaySummaryEn()
                ))
                .toList();
    }

    private void appendRagEvents(
            List<DemoCustomerChatTurnResponse.ToolEvent> events,
            KnowledgeRagResponse rag,
            int firstSequence
    ) {
        List<String> evidenceIds = rag.retrieval() == null
                || rag.retrieval().rankedMatches() == null
                ? List.of()
                : rag.retrieval().rankedMatches().stream()
                .map(KnowledgeRagResponse.RankedMatch::evidenceId)
                .toList();
        String sourceRef = rag.retrieval() == null
                || rag.retrieval().rankedMatches() == null
                || rag.retrieval().rankedMatches().isEmpty()
                ? null
                : rag.retrieval().rankedMatches().getFirst().sourceRef();
        int sequence = firstSequence;
        for (KnowledgeRagResponse.PhaseEvent phase : rag.phases()) {
            events.add(event(
                    sequence++,
                    phaseType(phase.id()),
                    phaseName(phase.id()),
                    phase.status(),
                    phase.executed(),
                    phase.summarySv(),
                    phase.summaryEn(),
                    switch (phase.id()) {
                        case "bounded_context", "java_verification" -> sourceRef;
                        default -> null;
                    },
                    switch (phase.id()) {
                        case "bounded_context", "java_verification" -> evidenceIds;
                        default -> List.of();
                    },
                    phase.latencyMs()
            ));
        }
    }

    private String phaseType(String phaseId) {
        return switch (phaseId) {
            case "safety", "eligibility_filter", "java_verification" ->
                    "verification";
            case "query_embedding" -> "embedding";
            case "vector_search" -> "vector_search";
            case "bounded_context" -> "context_build";
            case "generation" -> "generation";
            default -> "backend_step";
        };
    }

    private String phaseName(String phaseId) {
        return switch (phaseId) {
            case "safety" -> "screen_bounded_policy_query";
            case "eligibility_filter" -> "filter_approved_documents";
            case "query_embedding" -> "embed_policy_question";
            case "vector_search" -> "semantic_search_policy";
            case "bounded_context" -> "build_bounded_policy_context";
            case "generation" -> "generate_grounded_answer";
            case "java_verification" -> "verify_grounded_answer";
            default -> throw new IllegalArgumentException(
                    "Unknown Nordly RAG phase " + phaseId
            );
        };
    }

    private DemoCustomerChatTurnResponse.RagExecution ragExecution(
            KnowledgeRagResponse rag
    ) {
        KnowledgeRagResponse.QueryEmbedding embedding =
                rag.retrieval().queryEmbedding();
        return new DemoCustomerChatTurnResponse.RagExecution(
                true,
                rag.outcome(),
                rag.retrieval().backend(),
                rag.retrieval().corpusVersion(),
                rag.retrieval().corpusContentSha256(),
                rag.retrieval().currentVectorSearch(),
                embedding.executedInThisRun(),
                embedding.provider(),
                embedding.modelId(),
                embedding.dimensions(),
                rag.retrieval().rankedMatches().size(),
                rag.verification().overallOutcome(),
                rag.providerRoute(),
                rag.receipt().modelId(),
                rag.receipt().providerResponseId(),
                rag.receipt().tokenUsage(),
                rag.error() == null ? null : rag.error().code()
        );
    }

    private DemoCustomerChatTurnResponse.RagExecution noRag() {
        return new DemoCustomerChatTurnResponse.RagExecution(
                false,
                "not_run",
                null,
                null,
                null,
                false,
                false,
                null,
                null,
                null,
                0,
                "not_applicable",
                null,
                null,
                null,
                null,
                null
        );
    }

    private DemoCustomerChatTurnResponse.Receipt receipt(
            int readOperations,
            int providerCalls,
            int embeddingCalls,
            int vectorSearches,
            int generationCalls,
            long started,
            KnowledgeRagResponse rag
    ) {
        return new DemoCustomerChatTurnResponse.Receipt(
                readOperations,
                providerCalls,
                embeddingCalls,
                vectorSearches,
                generationCalls,
                0,
                "customer_order_and_refund_state",
                false,
                false,
                false,
                elapsedMs(started),
                rag == null ? null : rag.receipt().modelId(),
                rag == null ? null : rag.receipt().estimatedCostUsd(),
                rag == null ? "not_incurred" : rag.receipt().costStatus(),
                rag == null
                        ? "No provider call was made."
                        : rag.receipt().costBasis()
        );
    }

    private DemoCustomerChatTurnResponse.SafetyDecision safety(
            KnowledgeRagSafetyGate.Decision decision
    ) {
        return new DemoCustomerChatTurnResponse.SafetyDecision(
                decision.allowed() ? "ALLOW" : "BLOCK",
                decision.reasonCode().name(),
                decision.summarySv(),
                decision.summaryEn()
        );
    }

    private DemoCustomerChatTurnResponse.ToolEvent safetyEvent(
            int sequence,
            KnowledgeRagSafetyGate.Decision decision
    ) {
        return event(
                sequence,
                "safety",
                "screen_customer_message",
                decision.allowed() ? "completed" : "blocked",
                true,
                decision.summarySv(),
                decision.summaryEn(),
                null,
                List.of(),
                null
        );
    }

    private DemoCustomerChatTurnResponse.ToolEvent contextEvent(int sequence) {
        return event(
                sequence,
                "context_binding",
                "resolve_demo_customer_context",
                "completed",
                true,
                "Begränsade frågan till demokundens enda order.",
                "Scoped the question to the demo customer's only order.",
                contextCatalog.sourceRef(),
                List.of(contextCatalog.evidenceId()),
                null
        );
    }

    private DemoCustomerChatTurnResponse.ToolEvent event(
            int sequence,
            String type,
            String name,
            String status,
            boolean executed,
            String summarySv,
            String summaryEn,
            String sourceRef,
            List<String> evidenceIds,
            Long latencyMs
    ) {
        return new DemoCustomerChatTurnResponse.ToolEvent(
                sequence,
                type,
                eventActor(type),
                false,
                name,
                status,
                executed,
                summarySv,
                summaryEn,
                sourceRef,
                evidenceIds,
                latencyMs
        );
    }

    private String eventActor(String type) {
        return switch (type) {
            case "embedding", "vector_search", "context_build", "generation" ->
                    "knowledge_rag_service";
            default -> "spring_orchestrator";
        };
    }

    private DemoCustomerChatTurnResponse.Source contextSource() {
        return new DemoCustomerChatTurnResponse.Source(
                "customer_context",
                null,
                null,
                contextCatalog.contextVersion(),
                "Fixed synthetic demo customer context",
                "Current order binding",
                contextCatalog.sourceRef(),
                contextCatalog.evidenceId(),
                "VERSIONED_FIXTURE",
                null,
                "Samma syntetiska kund och enda order används för varje tur.",
                "The same synthetic customer and only order are used for every turn."
        );
    }

    private DemoCustomerChatTurnResponse.Source orderSource(
            DemoOrderSnapshot order
    ) {
        return new DemoCustomerChatTurnResponse.Source(
                "order_snapshot",
                null,
                null,
                "nordly-demo-orders-v1",
                "Synthetic current order snapshot",
                order.statusCode(),
                order.sourceRef(),
                order.evidenceId(),
                "VERSIONED_FIXTURE",
                null,
                order.summarySv(),
                order.summaryEn()
        );
    }

    private DemoCustomerChatTurnResponse.Source policySource(
            NordlyKnowledgeCorpus.EntryMetadata metadata,
            Double similarity
    ) {
        return new DemoCustomerChatTurnResponse.Source(
                "company_policy",
                metadata.documentId(),
                metadata.chunkId(),
                metadata.documentVersion(),
                metadata.title(),
                metadata.sectionHeading(),
                metadata.sourceRef(),
                metadata.evidenceId(),
                metadata.status(),
                similarity,
                metadata.displaySummarySv(),
                metadata.displaySummaryEn()
        );
    }

    private String boundedPolicyQuestion(
            DemoCustomerIntentClassifier.Intent intent,
            String locale
    ) {
        if (intent == DemoCustomerIntentClassifier.Intent.CANCELLATION_POLICY) {
            return "sv".equals(locale)
                    ? "Förklara enligt Nordlys godkända avbeställningspolicy om en order med status Skickad kan avbeställas och vad kunskapsassistenten får göra."
                    : "Explain from Nordly's approved cancellation policy whether an order with Shipped status can be cancelled and what the knowledge assistant may do.";
        }
        if (intent == DemoCustomerIntentClassifier.Intent.RETURN_POLICY) {
            return "sv".equals(locale)
                    ? "Förklara Nordlys godkända kriterier för returprövning och vad kunskapsassistenten inte får göra."
                    : "Explain Nordly's approved return-review criteria and what the knowledge assistant may not do.";
        }
        return "sv".equals(locale)
                ? "Förklara Nordlys godkända tidsregel för en redan godkänd kortåterbetalning och vad kunskapsassistenten inte får göra."
                : "Explain Nordly's approved timing rule for an already approved card refund and what the knowledge assistant may not do.";
    }

    private boolean isBoundedAction(
            DemoCustomerIntentClassifier.Decision intent,
            KnowledgeRagSafetyGate.Decision safety
    ) {
        if (!intent.actionRequested()) {
            return false;
        }
        return switch (intent.intent()) {
            case CANCEL_ORDER, PURCHASE_ITEM,
                    CHANGE_DELIVERY_ADDRESS -> safety.reasonCode()
                    == KnowledgeRagSafetyGate.ReasonCode.WRITE_ACTION;
            case RETURN_ORDER, REFUND_ORDER -> safety.reasonCode()
                    == KnowledgeRagSafetyGate.ReasonCode.FINANCIAL_ACTION;
            default -> false;
        };
    }

    private boolean shouldRedact(KnowledgeRagSafetyGate.Decision decision) {
        return !decision.allowed()
                && decision.reasonCode()
                != KnowledgeRagSafetyGate.ReasonCode.WRITE_ACTION
                && decision.reasonCode()
                != KnowledgeRagSafetyGate.ReasonCode.FINANCIAL_ACTION;
    }

    private DemoCustomerChatTurnResponse.AssistantMessage refusalMessage(
            KnowledgeRagSafetyGate.Decision safety
    ) {
        return switch (safety.reasonCode()) {
            case PII_REQUEST -> new DemoCustomerChatTurnResponse.AssistantMessage(
                    "Jag kan inte hjälpa till att lämna ut eller behandla privata "
                            + "personuppgifter. Jag kan däremot hjälpa dig med "
                            + "statusen för din egen order.",
                    "I cannot disclose or process private personal information. "
                            + "I can still help with the status of your own order."
            );
            case EMPLOYEE_COMPENSATION_REQUEST ->
                    new DemoCustomerChatTurnResponse.AssistantMessage(
                            "Jag kan inte hjälpa till att lämna ut privat "
                                    + "information om en medarbetares lön eller "
                                    + "ersättning. Jag kan däremot hjälpa dig med "
                                    + "din egen order.",
                            "I cannot help disclose private information about an "
                                    + "employee's salary or compensation. I can "
                                    + "still help with your own order."
                    );
            case SECRET_REQUEST -> new DemoCustomerChatTurnResponse.AssistantMessage(
                    "Jag kan inte lämna ut nycklar, lösenord eller andra hemligheter.",
                    "I cannot disclose keys, passwords, or other secrets."
            );
            case PROMPT_INJECTION ->
                    new DemoCustomerChatTurnResponse.AssistantMessage(
                            "Jag följer Nordlys säkerhetsregler och kan inte kringgå dem.",
                            "I follow Nordly's safety rules and cannot bypass them."
                    );
            default -> new DemoCustomerChatTurnResponse.AssistantMessage(
                    safety.summarySv(),
                    safety.summaryEn()
            );
        };
    }

    private NordlyKnowledgeCorpus.EntryMetadata approved(
            NordlyKnowledgeCorpus.EntryMetadata metadata,
            String evidenceId
    ) {
        if (!"APPROVED".equals(metadata.status())
                || !evidenceId.equals(metadata.evidenceId())) {
            throw new IllegalStateException(
                    "Required Nordly customer-chat policy is not approved: "
                            + evidenceId
            );
        }
        return metadata;
    }

    private String dateRangeSv(LocalDate from, LocalDate through) {
        Locale locale = Locale.forLanguageTag("sv-SE");
        if (from.getMonth() == through.getMonth()
                && from.getYear() == through.getYear()) {
            return from.getDayOfMonth() + "–" + through.format(
                    DateTimeFormatter.ofPattern("d MMMM", locale)
            );
        }
        return from.format(DateTimeFormatter.ofPattern("d MMMM", locale))
                + "–"
                + through.format(DateTimeFormatter.ofPattern("d MMMM", locale));
    }

    private String dateRangeEn(LocalDate from, LocalDate through) {
        Locale locale = Locale.ENGLISH;
        if (from.getMonth() == through.getMonth()
                && from.getYear() == through.getYear()) {
            return from.format(DateTimeFormatter.ofPattern("MMMM d", locale))
                    + "–"
                    + through.getDayOfMonth();
        }
        return from.format(DateTimeFormatter.ofPattern("MMMM d", locale))
                + "–"
                + through.format(DateTimeFormatter.ofPattern("MMMM d", locale));
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(
                0,
                (System.nanoTime() - startedNanos) / 1_000_000
        );
    }
}
