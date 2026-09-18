package dev.shirwac.incidentdetective.incidentlab.followup;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IncidentFollowUpEventLoggerTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private IncidentFollowUpEventLogger eventLogger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(
                IncidentFollowUpEventLogger.class
        );
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        eventLogger = new IncidentFollowUpEventLogger();
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void lifecycleUsesStructuredFieldsWithoutQuestionOrAnswerText() {
        eventLogger.started(
                "ilr_secret_reference",
                "turn_secret_reference",
                IncidentFollowUpResponse.Mode.LIVE_AI
        );

        ILoggingEvent event = appender.list.getFirst();
        Map<String, Object> fields = fields(event);
        assertEquals(Level.INFO, event.getLevel());
        assertEquals("Incident Lab follow-up started",
                event.getFormattedMessage());
        assertEquals(IncidentFollowUpEventLogger.EVENT_STARTED,
                fields.get("event_name"));
        assertEquals("incident_follow_up", fields.get("operation"));
        assertEquals("live_ai", fields.get("mode"));
        assertEquals("started", fields.get("status"));
        assertEquals(false, fields.get("write_tools_available"));
        assertEquals(false, fields.get("action_executed"));
        assertFalse(fields.get("run_ref_hash").toString()
                .contains("secret"));
        assertFalse(fields.get("turn_ref_hash").toString()
                .contains("secret"));
        assertFalse(fields.containsKey("question"));
        assertFalse(fields.containsKey("answer"));
    }

    @Test
    void failureDoesNotLogExceptionMessage() {
        eventLogger.failed(
                "ilr_secret_reference",
                "turn_secret_reference",
                new IllegalStateException("sensitive provider payload")
        );

        ILoggingEvent event = appender.list.getFirst();
        Map<String, Object> fields = fields(event);
        assertEquals(Level.WARN, event.getLevel());
        assertEquals(IncidentFollowUpEventLogger.EVENT_FAILED,
                fields.get("event_name"));
        assertEquals("IllegalStateException", fields.get("error_type"));
        assertFalse(event.getFormattedMessage().contains("sensitive"));
        assertFalse(fields.values().stream()
                .map(String::valueOf)
                .anyMatch(value -> value.contains("provider payload")));
    }

    private Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream().collect(Collectors.toMap(
                pair -> pair.key,
                pair -> pair.value
        ));
    }
}
