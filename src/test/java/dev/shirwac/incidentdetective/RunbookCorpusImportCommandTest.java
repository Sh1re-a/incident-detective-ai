package dev.shirwac.incidentdetective;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunbookCorpusImportCommandTest {

    @Test
    void recognizesAndRemovesOnlyTheExplicitImportFlag() {
        String[] args = {
                "--server.port=0",
                RunbookCorpusImportCommand.FLAG,
                "--spring.main.banner-mode=off"
        };

        assertTrue(RunbookCorpusImportCommand.requested(args));
        assertArrayEquals(
                new String[]{"--server.port=0", "--spring.main.banner-mode=off"},
                RunbookCorpusImportCommand.withoutCommandFlag(args)
        );
        assertFalse(RunbookCorpusImportCommand.requested(new String[0]));
    }
}
