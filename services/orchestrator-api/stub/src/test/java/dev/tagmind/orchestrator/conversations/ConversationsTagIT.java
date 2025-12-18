package dev.tagmind.orchestrator.conversations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConversationsTagIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tagmind")
            .withUsername("tagmind")
            .withPassword("tagmind");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Test
    void tag_requiresContactId() throws Exception {
        mvc.perform(post("/v1/conversations/tag")
                        .contentType("application/json")
                        .content("""
                                {"tag":"help"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void tag_requiresSupportedTag() throws Exception {
        mvc.perform(post("/v1/conversations/tag")
                        .contentType("application/json")
                        .content("""
                                {"contactId":"tg:test","tag":"unknown"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("tag is not supported"));
    }

    @Test
    void tag_success_returnsSkeletonResponse() throws Exception {
        mvc.perform(post("/v1/conversations/tag")
                        .contentType("application/json")
                        .content("""
                                {"contactId":"tg:test","tag":"help","payload":"ping"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DO_NOT_RESPOND"))
                .andExpect(jsonPath("$.contactId").value("tg:test"))
                .andExpect(jsonPath("$.tag").value("help"));
    }
}
