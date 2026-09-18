package dev.shirwac.incidentdetective.ai;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/** Public, non-secret route metadata for a provider call made in this run. */
public record GoogleGenAiProviderRoute(
        String transport,
        String authenticationMode,
        @Schema(nullable = true)
        String location
) {
    public static GoogleGenAiProviderRoute from(
            GeminiAiProperties properties
    ) {
        Objects.requireNonNull(properties, "properties must not be null");
        GoogleGenAiProvider provider = properties.provider();
        return new GoogleGenAiProviderRoute(
                provider.transport(),
                provider.authenticationMode(),
                provider == GoogleGenAiProvider.VERTEX_AI
                        ? properties.vertexLocation()
                        : null
        );
    }
}
