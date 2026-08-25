package dev.shirwac.incidentdetective.openapi;

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
}
