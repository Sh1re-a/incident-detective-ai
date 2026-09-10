package dev.shirwac.incidentdetective;

import dev.shirwac.incidentdetective.nordly.NordlyKnowledgeCorpusImporter;
import dev.shirwac.incidentdetective.rag.RunbookImportReport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;

final class NordlyKnowledgeCorpusImportCommand {

    static final String FLAG = "--import-nordly-knowledge";

    private NordlyKnowledgeCorpusImportCommand() {
    }

    static boolean requested(String[] args) {
        return args != null && Arrays.asList(args).contains(FLAG);
    }

    static void run(String[] args) {
        SpringApplication application = new SpringApplication(
                IncidentDetectiveApplication.class
        );
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("rag");
        try (ConfigurableApplicationContext context = application.run(
                withoutCommandFlag(args)
        )) {
            RunbookImportReport report = context
                    .getBean(NordlyKnowledgeCorpusImporter.class)
                    .importMissingOrChanged();
            JsonMapper jsonMapper = context.getBean(JsonMapper.class);
            System.out.println("NORDLY_KNOWLEDGE_IMPORT_REPORT="
                    + jsonMapper.writeValueAsString(report));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not serialize the Nordly knowledge import report",
                    exception
            );
        }
    }

    static String[] withoutCommandFlag(String[] args) {
        if (args == null) {
            return new String[0];
        }
        return Arrays.stream(args)
                .filter(argument -> !FLAG.equals(argument))
                .toArray(String[]::new);
    }
}
