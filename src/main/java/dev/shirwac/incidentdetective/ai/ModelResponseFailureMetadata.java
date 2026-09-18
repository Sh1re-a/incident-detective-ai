package dev.shirwac.incidentdetective.ai;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Safe, bounded metadata for rejected structured model responses. */
public record ModelResponseFailureMetadata(
        Category category,
        Reason reason,
        List<String> propertyPaths
) {

    private static final int MAX_PROPERTY_PATHS = 16;
    private static final Pattern SAFE_PROPERTY_PATH = Pattern.compile(
            "(?:\\$|[A-Za-z_][A-Za-z0-9_]*(?:\\[\\d{1,4}])?"
                    + "(?:\\.[A-Za-z_][A-Za-z0-9_]*(?:\\[\\d{1,4}])?)*)"
    );

    public ModelResponseFailureMetadata {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        propertyPaths = propertyPaths == null
                ? List.of()
                : propertyPaths.stream()
                        .filter(Objects::nonNull)
                        .map(String::strip)
                        .filter(path -> SAFE_PROPERTY_PATH.matcher(path).matches())
                        .distinct()
                        .sorted()
                        .limit(MAX_PROPERTY_PATHS)
                        .toList();
    }

    public enum Category {
        JSON_DESERIALIZATION,
        BEAN_VALIDATION
    }

    public enum Reason {
        EMPTY_RESPONSE,
        INVALID_JSON,
        UNKNOWN_PROPERTY,
        VALUE_DESERIALIZATION,
        CONSTRAINT_VIOLATION
    }
}
