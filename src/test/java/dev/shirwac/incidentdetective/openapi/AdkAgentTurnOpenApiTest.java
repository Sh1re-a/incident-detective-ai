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
class AdkAgentTurnOpenApiTest {

    private static final String ADK_TURN_POST =
            "$.paths['/api/v1/agent/turns'].post";
    private static final String PROBLEM_SCHEMA =
            "#/components/schemas/ApiProblemResponse";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsUpstreamAndTimeoutFailures() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        ADK_TURN_POST + ".responses['502'].description"
                ).value(
                        "Provider, embedding, or model tool output failed validation"
                ))
                .andExpect(jsonPath(
                        ADK_TURN_POST
                                + ".responses['502'].content['application/json']"
                                + ".schema['$ref']"
                ).value(PROBLEM_SCHEMA))
                .andExpect(jsonPath(
                        ADK_TURN_POST + ".responses['504'].description"
                ).value(
                        "The bounded ADK turn or model provider timed out"
                ))
                .andExpect(jsonPath(
                        ADK_TURN_POST
                                + ".responses['504'].content['application/json']"
                                + ".schema['$ref']"
                ).value(PROBLEM_SCHEMA));
    }
}
