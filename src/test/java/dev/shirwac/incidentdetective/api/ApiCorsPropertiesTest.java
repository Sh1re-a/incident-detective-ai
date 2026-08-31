package dev.shirwac.incidentdetective.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiCorsPropertiesTest {

    @Test
    void normalizesBlankAndDuplicateExactOrigins() {
        ApiCorsProperties properties = new ApiCorsProperties(List.of(
                " ",
                "https://portfolio.example",
                "https://portfolio.example",
                "http://127.0.0.1:5173"
        ));

        assertTrue(properties.enabled());
        assertEquals(List.of(
                "https://portfolio.example",
                "http://127.0.0.1:5173"
        ), properties.allowedOrigins());
    }

    @Test
    void defaultsToDisabled() {
        ApiCorsProperties properties = new ApiCorsProperties(null);

        assertFalse(properties.enabled());
        assertEquals(List.of(), properties.allowedOrigins());
    }

    @Test
    void rejectsWildcardsAndNonOriginUrls() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiCorsProperties(List.of("https://*.example.com"))
        );
        assertThrows(IllegalArgumentException.class, () ->
                new ApiCorsProperties(List.of("https://example.com/path"))
        );
        assertThrows(IllegalArgumentException.class, () ->
                new ApiCorsProperties(List.of("file:///tmp/demo"))
        );
    }
}
