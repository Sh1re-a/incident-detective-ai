package dev.shirwac.incidentdetective.replay;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JsonTest
class ScenarioFixtureIndexTest {

    private static Validator validator;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void indexesBothRecordedScenariosWithReadableResources() throws Exception {
        ScenarioFixtureIndex index;
        try (InputStream input = new ClassPathResource("fixtures/index.json")
                .getInputStream()) {
            index = jsonMapper.readValue(input, ScenarioFixtureIndex.class);
        }

        assertTrue(validator.validate(index).isEmpty());
        assertEquals(2, index.scenarios().size());
        assertEquals(
                Set.of(
                        "checkout-orders-at-risk-v1",
                        "checkout-cart-segment-failures-v1"
                ),
                index.scenarios().stream()
                        .map(ScenarioFixtureIndexEntry::scenarioId)
                        .collect(Collectors.toSet())
        );
        assertTrue(index.scenarios().stream().allMatch(entry ->
                new ClassPathResource(entry.recordedResource()).isReadable()
                        && new ClassPathResource(entry.groundTruthResource()).isReadable()
        ));
    }
}
