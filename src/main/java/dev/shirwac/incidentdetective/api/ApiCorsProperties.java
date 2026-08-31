package dev.shirwac.incidentdetective.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@ConfigurationProperties(prefix = "incident-detective.api.cors")
public record ApiCorsProperties(List<String> allowedOrigins) {

    public ApiCorsProperties {
        if (allowedOrigins == null) {
            allowedOrigins = List.of();
        } else {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String origin : allowedOrigins) {
                if (origin == null || origin.isBlank()) {
                    continue;
                }
                normalized.add(requireExactHttpOrigin(origin.strip()));
            }
            allowedOrigins = List.copyOf(normalized);
        }
    }

    public boolean enabled() {
        return !allowedOrigins.isEmpty();
    }

    private static String requireExactHttpOrigin(String origin) {
        if (origin.contains("*")) {
            throw new IllegalArgumentException(
                    "API CORS origins must be exact and cannot contain wildcards"
            );
        }
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "API CORS origin must be a valid absolute HTTP(S) origin",
                    exception
            );
        }
        String scheme = uri.getScheme() == null
                ? ""
                : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty())
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "API CORS origin must contain only scheme, host, and optional port"
            );
        }
        return origin;
    }
}
