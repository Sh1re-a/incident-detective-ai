package dev.shirwac.incidentdetective;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NordlyKnowledgeCorpusImportCommandTest {

    @Test
    void detectsAndRemovesOnlyItsExplicitFlag() {
        String[] args = {
                "--spring.profiles.active=replay",
                NordlyKnowledgeCorpusImportCommand.FLAG,
                "--other=value"
        };

        assertTrue(NordlyKnowledgeCorpusImportCommand.requested(args));
        assertArrayEquals(
                new String[]{
                        "--spring.profiles.active=replay",
                        "--other=value"
                },
                NordlyKnowledgeCorpusImportCommand.withoutCommandFlag(args)
        );
        assertFalse(NordlyKnowledgeCorpusImportCommand.requested(new String[0]));
    }
}
