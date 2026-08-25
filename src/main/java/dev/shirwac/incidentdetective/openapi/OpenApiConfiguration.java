package dev.shirwac.incidentdetective.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    private static final List<String> EVIDENCE_IMPLEMENTATIONS = List.of(
            "MetricEvidence",
            "LogEvidence",
            "TraceEvidence",
            "RunbookEvidence"
    );

    @Bean
    OpenAPI incidentDetectiveOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Incident Detective API")
                .version("v1")
                .description(
                        "Synthetic incident data with two clearly separated modes: "
                                + "recorded deterministic replay and an explicitly confirmed "
                                + "live Gemini investigation. Neither mode executes remediation."
                ));
    }

    @Bean
    ModelResolver snakeCaseOpenApiModelResolver() {
        ObjectMapper mapper = Json31.mapper().copy();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return new ModelResolver(mapper).openapi31(true);
    }

    @Bean
    OpenApiCustomizer customizeComponentSchemas() {
        return openApi -> {
            if (openApi.getComponents() == null
                    || openApi.getComponents().getSchemas() == null) {
                return;
            }

            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            EVIDENCE_IMPLEMENTATIONS.forEach(name -> {
                Schema<?> schema = schemas.get(name);
                if (!(schema instanceof ComposedSchema composed)
                        || composed.getAllOf() == null) {
                    return;
                }

                composed.getAllOf().stream()
                        .filter(part -> part.get$ref() == null)
                        .findFirst()
                        .ifPresent(objectSchema -> {
                            objectSchema.setRequired(composed.getRequired());
                            schemas.put(name, objectSchema);
                        });
            });

            Schema<?> recordedResult = schemas.get("RecordedReplayResult");
            if (recordedResult != null
                    && recordedResult.getProperties() != null) {
                Schema<?> tokenUsageSchema = new Schema<>()
                        .types(Set.of("null"))
                        .description("Always null because replay uses no model tokens.");
                recordedResult.getProperties().put("token_usage", tokenUsageSchema);
            }
        };
    }
}
