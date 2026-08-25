package dev.shirwac.incidentdetective.domain.diagnosis;

import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimValueTaxonomyTest {

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Test
    void everyCurrentGroundTruthClaimUsesTheSharedTaxonomy() throws Exception {
        for (String resourcePath : List.of(
                "fixtures/ground-truth/checkout-orders-at-risk-v1.json",
                "fixtures/ground-truth/checkout-cart-segment-failures-v1.json"
        )) {
            GroundTruth groundTruth;
            try (InputStream input = new ClassPathResource(resourcePath).getInputStream()) {
                groundTruth = jsonMapper.readValue(input, GroundTruth.class);
            }

            assertTrue(groundTruth.expectedClaims().stream().allMatch(claim ->
                    ClaimValueTaxonomy.contains(
                            claim.claimCode(),
                            claim.claimValueCode()
                    )
            ), resourcePath);
        }
    }

    @Test
    void sharedTaxonomyContainsNeitherScenarioNorEvidenceIds() throws Exception {
        String taxonomy = jsonMapper.writeValueAsString(
                ClaimValueTaxonomy.wireValues()
        );

        assertFalse(taxonomy.contains("checkout-"));
        assertFalse(taxonomy.contains("cpt-v1"));
        assertFalse(taxonomy.contains("cic-v1"));
    }
}
