package dev.shirwac.incidentdetective.nordly;

final class KnowledgeQuestionNotFoundException extends RuntimeException {

    KnowledgeQuestionNotFoundException(String questionId) {
        super("Nordly knowledge question not found: " + questionId);
    }
}
