package dev.shirwac.incidentdetective.domain.scenario;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScenarioTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsAValidScenario() {
        assertEquals(0, validator.validate(validScenario()).size());
    }

    @Test
    void rejectsANonCanonicalServiceCode() {
        Scenario scenario = new Scenario(
                "checkout-payment-timeout-v1",
                "Checkout errors threaten orders",
                "Payment attempts become slow after a release.",
                Instant.parse("2026-08-25T08:00:00Z"),
                new TimeWindow(
                        Instant.parse("2026-08-25T07:55:00Z"),
                        Instant.parse("2026-08-25T08:20:00Z")
                ),
                List.of("payment-adapter"),
                "Some customers cannot complete checkout.",
                List.of(new InitialSymptom(
                        "CHECKOUT_ERROR_RATE_HIGH",
                        "Checkout errors increased after the release.",
                        Instant.parse("2026-08-25T08:01:00Z")
                )),
                1
        );

        assertEquals(1, validator.validate(scenario).size());
    }

    @Test
    void copiesMutableInputLists() {
        List<String> services = new ArrayList<>(List.of("PAYMENT_ADAPTER"));
        Scenario scenario = new Scenario(
                "checkout-payment-timeout-v1",
                "Checkout errors threaten orders",
                "Payment attempts become slow after a release.",
                Instant.parse("2026-08-25T08:00:00Z"),
                new TimeWindow(
                        Instant.parse("2026-08-25T07:55:00Z"),
                        Instant.parse("2026-08-25T08:20:00Z")
                ),
                services,
                "Some customers cannot complete checkout.",
                List.of(new InitialSymptom(
                        "CHECKOUT_ERROR_RATE_HIGH",
                        "Checkout errors increased after the release.",
                        Instant.parse("2026-08-25T08:01:00Z")
                )),
                1
        );

        services.add("CHECKOUT_API");

        assertEquals(List.of("PAYMENT_ADAPTER"), scenario.affectedServices());
        assertThrows(UnsupportedOperationException.class,
                () -> scenario.affectedServices().add("CHECKOUT_API"));
    }

    @Test
    void rejectsAnEmptyOrReversedTimeWindow() {
        Instant now = Instant.parse("2026-08-25T08:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> new TimeWindow(now, now));
        assertThrows(IllegalArgumentException.class,
                () -> new TimeWindow(now, now.minusSeconds(1)));
    }

    private static Scenario validScenario() {
        return new Scenario(
                "checkout-payment-timeout-v1",
                "Checkout errors threaten orders",
                "Payment attempts become slow after a release.",
                Instant.parse("2026-08-25T08:00:00Z"),
                new TimeWindow(
                        Instant.parse("2026-08-25T07:55:00Z"),
                        Instant.parse("2026-08-25T08:20:00Z")
                ),
                List.of("PAYMENT_ADAPTER", "CHECKOUT_API"),
                "Some customers cannot complete checkout.",
                List.of(new InitialSymptom(
                        "CHECKOUT_ERROR_RATE_HIGH",
                        "Checkout errors increased after the release.",
                        Instant.parse("2026-08-25T08:01:00Z")
                )),
                1
        );
    }
}
