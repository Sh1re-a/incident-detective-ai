package dev.shirwac.incidentdetective.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiCorsConfiguration implements WebMvcConfigurer {

    private static final long PREFLIGHT_MAX_AGE_SECONDS = 3_600;

    private final ApiCorsProperties properties;

    public ApiCorsConfiguration(ApiCorsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!properties.enabled()) {
            return;
        }
        registry.addMapping("/api/v1/**")
                .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Accept", "Content-Type")
                .exposedHeaders(HttpHeaders.RETRY_AFTER)
                .allowCredentials(false)
                .maxAge(PREFLIGHT_MAX_AGE_SECONDS);
    }
}
