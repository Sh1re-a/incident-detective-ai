package dev.shirwac.incidentdetective;

import dev.shirwac.incidentdetective.rag.eval.RunbookRetrievalEvalReport;
import dev.shirwac.incidentdetective.rag.eval.RunbookRetrievalEvalReportWriter;
import dev.shirwac.incidentdetective.rag.eval.RunbookRetrievalEvaluator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;

final class RunbookRetrievalEvalCommand {

    static final String FLAG = "--evaluate-runbook-retrieval";
    static final String GIT_SHA_ENV = "INCIDENT_DETECTIVE_GIT_SHA";

    private RunbookRetrievalEvalCommand() {
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
            RunbookRetrievalEvalReport report = context
                    .getBean(RunbookRetrievalEvaluator.class)
                    .evaluate(System.getenv(GIT_SHA_ENV));
            RunbookRetrievalEvalReportWriter.OutputFiles files = context
                    .getBean(RunbookRetrievalEvalReportWriter.class)
                    .write(report);
            System.out.println("RUNBOOK_RETRIEVAL_EVAL_JSON="
                    + files.json().toAbsolutePath());
            System.out.println("RUNBOOK_RETRIEVAL_EVAL_MARKDOWN="
                    + files.markdown().toAbsolutePath());
            System.out.println("RUNBOOK_RETRIEVAL_EVAL_THRESHOLD="
                    + report.calibration().frozenThreshold());
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
