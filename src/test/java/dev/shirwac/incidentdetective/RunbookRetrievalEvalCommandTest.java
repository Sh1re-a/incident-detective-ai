package dev.shirwac.incidentdetective;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunbookRetrievalEvalCommandTest {

    @Test
    void recognizesAndRemovesOnlyTheExplicitEvalFlag() {
        String[] args = {
                "--server.port=0",
                RunbookRetrievalEvalCommand.FLAG,
                "--spring.main.banner-mode=off"
        };

        assertTrue(RunbookRetrievalEvalCommand.requested(args));
        assertArrayEquals(
                new String[]{"--server.port=0", "--spring.main.banner-mode=off"},
                RunbookRetrievalEvalCommand.withoutCommandFlag(args)
        );
        assertFalse(RunbookRetrievalEvalCommand.requested(new String[0]));
    }

    @Test
    void rejectsImportAndEvalInTheSameProcess() {
        String[] args = {
                RunbookCorpusImportCommand.FLAG,
                RunbookRetrievalEvalCommand.FLAG
        };

        assertThrows(
                IllegalArgumentException.class,
                () -> IncidentDetectiveApplication.requireSingleCommand(args)
        );
    }
}
