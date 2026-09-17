package dev.shirwac.incidentdetective.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import dev.shirwac.incidentdetective.planning.IncidentPlan;
import dev.shirwac.incidentdetective.planning.IncidentService;
import dev.shirwac.incidentdetective.planning.IncidentSeverity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentLabEventLoggerTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private IncidentLabEventLogger eventLogger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(IncidentLabEventLogger.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        eventLogger = new IncidentLabEventLogger();
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void blockedPlanLogContainsOnlyBoundedOperationalFields() {
        eventLogger.planDecided(
                "plan-correlation-1",
                "blocked_before_ai",
                "blocked",
                "pii_request",
                null,
                null
        );

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.getFirst();
        assertEquals(Level.INFO, event.getLevel());
        assertEquals(
                "Incident Lab plan decision recorded",
                event.getFormattedMessage()
        );
        Map<String, Object> fields = fields(event);
        assertEquals(
                IncidentLabEventLogger.EVENT_PLAN_DECIDED,
                fields.get("event_name")
        );
        assertEquals("plan-correlation-1", fields.get("correlation_id"));
        assertEquals("blocked_before_ai", fields.get("outcome"));
        assertEquals("pii_request", fields.get("safety_reason"));
        assertEquals(false, fields.get("provider_called"));
        assertFalse(fields.containsKey("prompt"));
        assertFalse(fields.containsKey("instruction"));
        assertFalse(fields.containsKey("customer_data"));
    }

    @Test
    void planRefExcludesFreeTextAndNormalizesServiceOrder() {
        IncidentPlan first = plan(
                "First model-written summary",
                List.of(
                        IncidentService.PAYMENT_ADAPTER,
                        IncidentService.CHECKOUT_API
                )
        );
        IncidentPlan sameControls = plan(
                "Completely different free text",
                List.of(
                        IncidentService.CHECKOUT_API,
                        IncidentService.PAYMENT_ADAPTER
                )
        );
        IncidentPlan differentFamily = new IncidentPlan(
                IncidentPlan.CONTRACT_VERSION,
                GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
                IncidentSeverity.HIGH,
                List.of(IncidentService.CATALOG_SERVICE),
                "Another summary",
                true,
                false,
                true
        );

        assertEquals(
                eventLogger.planRef(first),
                eventLogger.planRef(sameControls)
        );
        assertNotEquals(
                eventLogger.planRef(first),
                eventLogger.planRef(differentFamily)
        );
        assertTrue(eventLogger.planRef(first).matches("plan-[0-9a-f]{16}"));
    }

    @Test
    void failureLogDoesNotEmitExceptionMessage() {
        RuntimeException failure = new IllegalStateException(
                "secret or provider payload must never be logged"
        );

        eventLogger.operationFailed(
                "run-correlation-1",
                "run",
                "plan-0123456789abcdef",
                failure
        );

        ILoggingEvent event = appender.list.getFirst();
        Map<String, Object> fields = fields(event);
        assertEquals(Level.ERROR, event.getLevel());
        assertEquals("IllegalStateException", fields.get("error_type"));
        assertFalse(event.getFormattedMessage().contains("secret"));
        assertFalse(fields.values().stream()
                .map(String::valueOf)
                .anyMatch(value -> value.contains("provider payload")));
    }

    private IncidentPlan plan(
            String summary,
            List<IncidentService> services
    ) {
        return new IncidentPlan(
                IncidentPlan.CONTRACT_VERSION,
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                IncidentSeverity.HIGH,
                services,
                summary,
                true,
                false,
                true
        );
    }

    private Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream().collect(Collectors.toMap(
                pair -> pair.key,
                pair -> pair.value
        ));
    }
}
