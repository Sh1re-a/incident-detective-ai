package dev.shirwac.incidentdetective.incidentlab.followup;

import dev.shirwac.incidentdetective.ai.GoogleGenAiProviderRoute;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lets Gemini route a free question; it never authors incident facts. */
public interface IncidentFollowUpRouter {

    Result route(Input input);

    record Input(
            String question,
            String locale,
            String originalAnswerState
    ) {
        public Input {
            if (question == null || question.isBlank() || question.length() > 500) {
                throw new IllegalArgumentException("question is invalid");
            }
            if (!Set.of("sv", "en").contains(locale)) {
                throw new IllegalArgumentException("locale is invalid");
            }
        }
    }

    record Decision(Intent intent, List<Section> sections) {
        public Decision {
            sections = sections == null ? List.of() : List.copyOf(sections);
            if (sections.isEmpty() || sections.size() > 6
                    || new HashSet<>(sections).size() != sections.size()) {
                throw new IllegalArgumentException(
                        "sections must contain one to six unique values"
                );
            }
        }
    }

    enum Intent {
        INCIDENT_QUESTION,
        OUTSIDE_SCOPE
    }

    enum Section {
        SUMMARY,
        PROBLEM_LOCATION,
        CAUSE,
        CUSTOMER_IMPACT,
        KNOWN,
        UNKNOWN,
        SOURCES,
        BOUNDARY
    }

    record Result(Decision decision, ProviderMetadata provider) {
    }

    record ProviderMetadata(
            GoogleGenAiProviderRoute route,
            String responseId,
            String modelVersion,
            ModelTokenUsage tokenUsage,
            long latencyMs
    ) {
    }
}
