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

import java.util.LinkedHashSet;
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
    private static final Map<String, Set<String>> NULLABLE_PROPERTIES =
            Map.ofEntries(
                    Map.entry(
                            "RetrievalCapability",
                            Set.of("active_embedding_profile")
                    ),
                    Map.entry(
                            "LiveInvestigationResult",
                            Set.of(
                                    "token_usage",
                                    "estimated_cost_usd",
                                    "model_cost_breakdown"
                            )
                    ),
                    Map.entry(
                            "ModelCallMetadata",
                            Set.of(
                                    "provider_response_id",
                                    "model_version",
                                    "token_usage"
                            )
                    ),
                    Map.entry(
                            "ModelTokenUsage",
                            Set.of(
                                    "input_tokens",
                                    "cached_input_tokens",
                                    "uncached_input_tokens",
                                    "candidate_output_tokens",
                                    "thinking_output_tokens",
                                    "output_tokens",
                                    "tool_use_prompt_tokens",
                                    "total_tokens"
                            )
                    ),
                    Map.entry(
                            "LiveToolEvent",
                            Set.of("runbook_retrieval")
                    ),
                    Map.entry(
                            "RunbookRetrievalMetadata",
                            Set.of(
                                    "corpus_version",
                                    "embedding_profile",
                                    "query_embedding"
                            )
                    ),
                    Map.entry(
                            "QueryEmbeddingUsage",
                            Set.of(
                                    "provider_billable_characters",
                                    "provider_input_tokens"
                            )
                    ),
                    Map.entry(
                            "Match",
                            Set.of("cosine_similarity", "content_sha256")
                    ),
                    Map.entry(
                            "Diagnosis",
                            Set.of("root_cause_code", "affected_service")
                    ),
                    Map.entry("EvidencePrecision", Set.of("score")),
                    Map.entry("ClaimCoverage", Set.of("score")),
                    Map.entry(
                            "ReplayComparison",
                            Set.of(
                                    "expected_root_cause_code",
                                    "expected_affected_service"
                            )
                    ),
                    Map.entry(
                            "PromptCacheTelemetry",
                            Set.of("cached_input_tokens")
                    ),
                    Map.entry(
                            "ModelCostBreakdown",
                            Set.of(
                                    "uncached_input_usd",
                                    "cached_input_usd",
                                    "output_usd",
                                    "observed_cache_savings_usd"
                            )
                    ),
                    Map.entry(
                            "ProviderUsage",
                            Set.of(
                                    "provider_billable_characters",
                                    "provider_input_tokens",
                                    "estimated_list_price_cost_usd"
                            )
                    )
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

            enforceNullableProperties(schemas);
            normalizeNullableReferences(schemas);
            requireEveryDeclaredProperty(schemas);
        };
    }

    private static void enforceNullableProperties(
            Map<String, Schema> schemas
    ) {
        NULLABLE_PROPERTIES.forEach((schemaName, propertyNames) -> {
            Schema<?> owner = schemas.get(schemaName);
            if (owner == null || owner.getProperties() == null) {
                throw new IllegalStateException(
                        "OpenAPI schema is missing: " + schemaName
                );
            }
            propertyNames.forEach(propertyName -> {
                Object rawProperty = owner.getProperties().get(propertyName);
                if (!(rawProperty instanceof Schema<?> property)) {
                    throw new IllegalStateException(
                            "OpenAPI property is missing: "
                                    + schemaName + "." + propertyName
                    );
                }
                owner.getProperties().put(
                        propertyName,
                        nullable(property)
                );
            });
        });
    }

    private static Schema<?> nullable(Schema<?> property) {
        if (property instanceof ComposedSchema composed
                && composed.getOneOf() != null
                && composed.getOneOf().stream().anyMatch(
                part -> Set.of("null").equals(part.getTypes())
        )) {
            return property;
        }
        if (property.get$ref() != null) {
            ComposedSchema nullableReference = new ComposedSchema();
            nullableReference.setOneOf(List.of(
                    new Schema<>().$ref(property.get$ref()),
                    new Schema<>().types(Set.of("null"))
            ));
            nullableReference.setDescription(property.getDescription());
            return nullableReference;
        }

        Set<String> types = new LinkedHashSet<>();
        if (property.getTypes() != null) {
            types.addAll(property.getTypes());
        } else if (property.getType() != null) {
            types.add(property.getType());
        }
        types.add("null");
        property.setTypes(types);
        return property;
    }

    private static void normalizeNullableReferences(
            Map<String, Schema> schemas
    ) {
        schemas.values().forEach(owner -> {
            if (owner.getProperties() == null) {
                return;
            }
            owner.getProperties().replaceAll((name, value) -> {
                if (!(value instanceof Schema<?> property)
                        || property.get$ref() == null
                        || !Set.of("null").equals(property.getTypes())) {
                    return value;
                }

                ComposedSchema nullableReference = new ComposedSchema();
                nullableReference.setOneOf(List.of(
                        new Schema<>().$ref(property.get$ref()),
                        new Schema<>().types(Set.of("null"))
                ));
                nullableReference.setDescription(property.getDescription());
                return nullableReference;
            });
        });
    }

    private static void requireEveryDeclaredProperty(
            Map<String, Schema> schemas
    ) {
        schemas.values().forEach(schema -> {
            if (schema.getProperties() == null
                    || schema.getProperties().isEmpty()) {
                return;
            }
            Set<String> required = new LinkedHashSet<>();
            if (schema.getRequired() != null) {
                required.addAll(schema.getRequired());
            }
            required.addAll(schema.getProperties().keySet());
            schema.setRequired(List.copyOf(required));
        });
    }
}
