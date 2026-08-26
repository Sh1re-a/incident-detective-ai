package dev.shirwac.incidentdetective;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IncidentDetectiveApplication {

    public static void main(String[] args) {
        requireSingleCommand(args);
        if (RunbookCorpusImportCommand.requested(args)) {
            RunbookCorpusImportCommand.run(args);
            return;
        }
        if (RunbookRetrievalEvalCommand.requested(args)) {
            RunbookRetrievalEvalCommand.run(args);
            return;
        }
        SpringApplication.run(IncidentDetectiveApplication.class, args);
    }

    static void requireSingleCommand(String[] args) {
        if (RunbookCorpusImportCommand.requested(args)
                && RunbookRetrievalEvalCommand.requested(args)) {
            throw new IllegalArgumentException(
                    "Import and retrieval eval must be run as separate commands"
            );
        }
    }
}
