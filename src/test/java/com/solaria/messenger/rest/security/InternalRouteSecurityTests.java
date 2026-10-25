package com.solaria.messenger.rest.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.service-keys.ai-assistant=" + InternalRouteSecurityTests.AGENT_KEY,
        "app.service-keys.api-notify=" + InternalRouteSecurityTests.OTHER_KEY })
@AutoConfigureMockMvc
class InternalRouteSecurityTests {

    static final String AGENT_KEY = "agent-service-key-0123456789abcdef";
    static final String OTHER_KEY = "other-service-key-0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingKeyIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/internal/observability"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void unknownKeyIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/internal/observability").header("X-Service-Key", "unknown-service-key-0123456789abcdef"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keyOfAServiceThatIsNotAnAgentIsRefused() throws Exception {
        mockMvc.perform(get("/internal/observability").header("X-Service-Key", OTHER_KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void agentKeyReachesTheObservabilityRoutes() throws Exception {
        mockMvc.perform(get("/internal/observability").header("X-Service-Key", AGENT_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void undeclaredInternalRoutesStayClosed() throws Exception {
        mockMvc.perform(get("/internal/projects").header("X-Service-Key", AGENT_KEY))
                .andExpect(status().isUnauthorized());
    }
}
