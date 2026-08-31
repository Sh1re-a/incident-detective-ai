package dev.shirwac.incidentdetective.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiTransportExceptionHandlerTest {

    private static final String LIVE_PATH =
            "/api/v1/scenarios/checkout-orders-at-risk-v1/runs/live-ai";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsAStableProblemCodeForUnsupportedContentType() throws Exception {
        mockMvc.perform(post(LIVE_PATH)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("confirm_live_ai=true"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void returnsAStableProblemCodeForUnsupportedMethod() throws Exception {
        mockMvc.perform(get(LIVE_PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void returnsAStableProblemCodeForUnknownRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/not-a-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
    }
}
