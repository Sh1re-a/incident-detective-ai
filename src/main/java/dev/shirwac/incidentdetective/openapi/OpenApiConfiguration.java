package dev.shirwac.incidentdetective.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI incidentDetectiveOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Incident Detective API")
                .version("v1")
                .description(
                        "Simulated incident — recorded deterministic replay. "
                                + "The current API does not run live AI or execute remediation."
                ));
    }

    @Bean
    ModelResolver snakeCaseOpenApiModelResolver() {
        ObjectMapper mapper = Json31.mapper().copy();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return new ModelResolver(mapper).openapi31(true);
    }
}
