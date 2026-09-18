package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.api.ApiCorsProperties;
import dev.shirwac.incidentdetective.nordly.DemoCustomerChatController;
import dev.shirwac.incidentdetective.nordly.DemoCustomerChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DemoCustomerChatController.class)
@ActiveProfiles("rag")
@EnableConfigurationProperties(ApiCorsProperties.class)
class DemoCustomerChatQuotaApiTest {

    private static final String PATH = "/api/v1/demo-customer/chat/turns";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoCustomerChatService service;

    @Test
    void returnsTheStableDailyQuotaProblemAndRetryHeader() throws Exception {
        when(service.run(any())).thenThrow(new LiveDailyQuotaExceededException(
                Instant.now().plusSeconds(3_600)
        ));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "What is the return policy?",
                                  "locale": "en",
                                  "confirm_live_ai": true
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value(
                        "LIVE_AI_DAILY_LIMIT_REACHED"
                ));
    }
}
