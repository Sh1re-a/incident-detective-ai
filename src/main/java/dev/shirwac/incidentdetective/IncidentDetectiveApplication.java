package dev.shirwac.incidentdetective;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IncidentDetectiveApplication {

    public static void main(String[] args) {
        if (NordlyKnowledgeCorpusImportCommand.requested(args)) {
            NordlyKnowledgeCorpusImportCommand.run(args);
            return;
        }
        if (RunbookCorpusImportCommand.requested(args)) {
            RunbookCorpusImportCommand.run(args);
            return;
        }
        SpringApplication.run(IncidentDetectiveApplication.class, args);
    }
}
