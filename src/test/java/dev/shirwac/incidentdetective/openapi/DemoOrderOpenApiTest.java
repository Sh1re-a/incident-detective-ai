package dev.shirwac.incidentdetective.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoOrderOpenApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsTheReadOnlySyntheticOrderCatalogAndLookup() throws Exception {
        String list = "$.paths['/api/v1/demo-orders'].get";
        String lookup = "$.paths['/api/v1/demo-orders/{orderId}'].get";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(list + ".summary")
                        .value("List synthetic Nordly demo orders"))
                .andExpect(jsonPath(
                        list + ".responses['200'].content['application/json']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/DemoOrderCatalogResponse"))
                .andExpect(jsonPath(lookup + ".summary")
                        .value("Get one synthetic Nordly demo order"))
                .andExpect(jsonPath(lookup + ".parameters[0].name")
                        .value("orderId"))
                .andExpect(jsonPath(lookup + ".parameters[0].required")
                        .value(true))
                .andExpect(jsonPath(
                        lookup + ".responses['200'].content['application/json']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/DemoOrderLookupResponse"))
                .andExpect(jsonPath(
                        lookup + ".responses['404'].content"
                                + "['application/problem+json'].schema['$ref']"
                ).value("#/components/schemas/ApiProblemResponse"))
                .andExpect(jsonPath(
                        "$.components.schemas.DemoOrderReadReceipt.properties"
                                + ".read_operations"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.DemoOrderReadReceipt.properties"
                                + ".write_operations"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.DemoOrderReadReceipt.properties.ai_calls"
                ).exists());
    }
}
