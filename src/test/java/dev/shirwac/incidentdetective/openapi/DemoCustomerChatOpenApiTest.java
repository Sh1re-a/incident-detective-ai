package dev.shirwac.incidentdetective.openapi;

import com.zaxxer.hikari.HikariDataSource;
import dev.shirwac.incidentdetective.nordly.DemoCustomerChatService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("rag")
class DemoCustomerChatOpenApiTest {

    private static final String PATH =
            "/paths/~1api~1v1~1demo-customer~1chat~1turns/post";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoCustomerChatService service;

    @MockitoBean(name = "ragDataSource")
    private HikariDataSource ragDataSource;

    @MockitoBean(name = "ragFlyway")
    private Flyway ragFlyway;

    @MockitoBean(name = "ragJdbcClient")
    private JdbcClient ragJdbcClient;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void documentsTheFixedStatelessReadOnlyCustomerContract()
            throws Exception {
        JsonNode document = jsonMapper.readTree(
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        assertEquals(
                "Run one controlled Nordly customer-chat turn",
                document.at(PATH + "/summary").asText()
        );
        assertEquals(
                "#/components/schemas/DemoCustomerChatTurnRequest",
                document.at(
                        PATH + "/requestBody/content/application~1json/schema/$ref"
                ).asText()
        );
        assertEquals(
                "#/components/schemas/DemoCustomerChatTurnResponse",
                document.at(
                        PATH + "/responses/200/content/application~1json/schema/$ref"
                ).asText()
        );

        JsonNode schemas = document.at("/components/schemas");
        JsonNode request = schemas.get("DemoCustomerChatTurnRequest");
        assertEquals(
                Set.of(
                        "message",
                        "locale",
                        "confirm_live_ai",
                        "recent_conversation"
                ),
                fieldNames(request.get("properties"))
        );
        assertFalse(request.get("properties").has("customer_id"));
        assertFalse(request.get("properties").has("order_id"));
        assertFalse(request.get("properties").has("session_id"));
        assertFalse(request.get("properties").has("history"));
        assertEquals(3,
                request.get("properties").get("message").get("minLength").asInt());
        assertEquals(500,
                request.get("properties").get("message").get("maxLength").asInt());
        assertEquals("sv|en",
                request.get("properties").get("locale").get("pattern").asText());
        assertEquals(6, request.get("properties")
                .get("recent_conversation").get("maxItems").asInt());
        assertEquals(
                "#/components/schemas/ConversationTurn",
                request.get("properties").get("recent_conversation")
                        .get("items").get("$ref").asText()
        );

        JsonNode response = schemas.get("DemoCustomerChatTurnResponse");
        assertEquals(
                "#/components/schemas/DemoCustomerReceipt",
                response.get("properties").get("receipt").get("$ref").asText()
        );
        assertEquals(
                "#/components/schemas/DemoCustomerRagExecution",
                response.get("properties").get("rag").get("$ref").asText()
        );
        assertEquals(
                "#/components/schemas/DemoCustomerVerification",
                response.get("properties").get("verification").get("$ref").asText()
        );
        assertTrue(response.get("properties").has("verified_claims"));

        JsonNode receipt = schemas.get("DemoCustomerReceipt");
        assertTrue(receipt.get("properties").has("business_write_operations"));
        assertTrue(receipt.get("properties").has("business_write_scope"));
        assertTrue(receipt.get("properties").has("persistent_memory_used"));
        assertFalse(receipt.get("properties").has("write_operations"));

        JsonNode rag = schemas.get("DemoCustomerRagExecution");
        assertTrue(rag.get("properties").has("provider_route"));
        assertTrue(rag.get("properties").has("generation_model_id"));
        assertTrue(rag.get("properties").has("provider_response_id"));
        assertTrue(rag.get("properties").has("token_usage"));

        JsonNode toolEvent = schemas.get("DemoCustomerToolEvent");
        assertTrue(toolEvent.get("properties").has("initiated_by"));
        assertTrue(toolEvent.get("properties").has("model_selected"));

        for (String code : new String[]{"400", "429", "503"}) {
            assertEquals(
                    "#/components/schemas/ApiProblemResponse",
                    document.at(
                            PATH + "/responses/" + code
                                    + "/content/application~1problem+json/schema/$ref"
                    ).asText(),
                    code
            );
        }
    }

    private static Set<String> fieldNames(JsonNode object) {
        Set<String> names = new HashSet<>();
        object.propertyStream().forEach(entry -> names.add(entry.getKey()));
        return names;
    }
}
