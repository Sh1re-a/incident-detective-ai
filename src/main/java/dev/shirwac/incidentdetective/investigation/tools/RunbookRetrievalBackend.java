package dev.shirwac.incidentdetective.investigation.tools;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RunbookRetrievalBackend {
    DETERMINISTIC_FIXTURE("deterministic_fixture"),
    PGVECTOR_EXACT_COSINE("pgvector_exact_cosine");

    private final String wireValue;

    RunbookRetrievalBackend(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
