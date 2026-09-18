package dev.shirwac.incidentdetective.nordly;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeOutputVerifierTest {

    private static final String CITATION = "evidence-refund";
    private final KnowledgeOutputVerifier verifier =
            new KnowledgeOutputVerifier();

    @Test
    void acceptsStructuredOutputWithRetrievedApprovedCitation() {
        KnowledgeOutputVerifier.Result result = verifier.verify(
                answer(
                        "Banken behöver normalt två till fem bankdagar.",
                        "The bank normally needs two to five banking days."
                ),
                List.of(match())
        );

        assertTrue(result.schemaPass());
        assertTrue(result.citationsWithinRetrievedContext());
        assertTrue(result.approvedDocumentsOnly());
        assertTrue(result.outputPiiScanPass());
        assertTrue(result.outputPolicyScanPass());
        assertTrue(result.passed());
    }

    @Test
    void rejectsPersonalDataBeforeItCanLeaveTheBackend() {
        KnowledgeOutputVerifier.Result email = verifier.verify(
                answer("Kunden är anna@example.com.", "Customer: anna@example.com."),
                List.of(match())
        );
        KnowledgeOutputVerifier.Result phone = verifier.verify(
                answer("Ring 070-123 45 67.", "Call 070-123 45 67."),
                List.of(match())
        );

        assertFalse(email.outputPiiScanPass());
        assertFalse(email.passed());
        assertFalse(phone.outputPiiScanPass());
        assertFalse(phone.passed());
    }

    @Test
    void rejectsAFalseClaimThatAnActionWasExecuted() {
        KnowledgeOutputVerifier.Result result = verifier.verify(
                answer(
                        "Jag har återbetalat ordern.",
                        "I have refunded the order."
                ),
                List.of(match())
        );

        assertFalse(result.outputPolicyScanPass());
        assertFalse(result.passed());
    }

    private KnowledgeGeneratedAnswer answer(String sv, String en) {
        return new KnowledgeGeneratedAnswer(
                sv,
                en,
                List.of(new KnowledgeGeneratedAnswer.GeneratedClaim(
                        sv,
                        en,
                        List.of(CITATION)
                ))
        );
    }

    private KnowledgeRagResponse.RankedMatch match() {
        return new KnowledgeRagResponse.RankedMatch(
                1,
                0.91,
                "APPROVED",
                "document",
                "chunk",
                "1.0",
                "Title",
                "Section",
                "Owner",
                "knowledge/document#chunk",
                CITATION,
                "b".repeat(64),
                "Sammanfattning",
                "Summary",
                "Grounded text"
        );
    }
}
