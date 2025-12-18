package dev.tagmind.orchestrator.conversations;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.tagmind.orchestrator.persistence.ConversationMessageEntity;
import dev.tagmind.orchestrator.persistence.ConversationMessageRepository;
import dev.tagmind.orchestrator.persistence.ConversationMode;
import dev.tagmind.orchestrator.persistence.ConversationSessionEntity;
import dev.tagmind.orchestrator.persistence.ConversationSessionRepository;
import dev.tagmind.orchestrator.persistence.MessageDirection;
import org.junit.jupiter.api.AfterAll;
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

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

    static final HttpServer llmStub = startLlmStub();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        System.setProperty("LLM_URL", "http://127.0.0.1:" + llmStub.getAddress().getPort() + "/v1/complete");
    }

    @AfterAll
    static void shutdown() {
        llmStub.stop(0);
        System.clearProperty("LLM_URL");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ConversationSessionRepository sessions;

    @Autowired
    ConversationMessageRepository messages;

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
                .andExpect(jsonPath("$.decision").value("RESPOND"))
                .andExpect(jsonPath("$.contactId").value("tg:test"))
                .andExpect(jsonPath("$.tag").value("help"));
    }

    @Test
    void tag_recap_fetchesHistoryChronologically() throws Exception {
        ConversationSessionEntity session = new ConversationSessionEntity();
        session.setContactId("tg:history");
        session.setMode(ConversationMode.SUGGEST);
        session = sessions.save(session);

        storeMessage(session, MessageDirection.IN, "msg1");
        storeMessage(session, MessageDirection.OUT, "msg2");
        storeMessage(session, MessageDirection.IN, "msg3");
        storeMessage(session, MessageDirection.OUT, "msg4");

        mvc.perform(post("/v1/conversations/tag")
                        .contentType("application/json")
                        .content("""
                                {"contactId":"tg:history","tag":"recap","count":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.used.historyLimit").value(3))
                .andExpect(jsonPath("$.used.historyUsed").value(3))
                .andExpect(jsonPath("$.used.history[0].text").value("msg2"))
                .andExpect(jsonPath("$.used.history[2].text").value("msg4"))
                .andExpect(jsonPath("$.replyText").value("tag-response"));
    }

    private void storeMessage(ConversationSessionEntity session, MessageDirection direction, String text) {
        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setSession(session);
        message.setDirection(direction);
        message.setMessageText(text);
        message.setRequestId("req_" + UUID.randomUUID());
        messages.save(message);
    }

    private static HttpServer startLlmStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/complete", ConversationsTagIT::handleComplete);
            server.start();
            return server;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void handleComplete(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
        if (requestId == null || requestId.isBlank()) requestId = "req_tag_stub";

        byte[] body = ("""
                {"requestId":%s,"text":"tag-response","usage":{"stub":true}}
                """.formatted(jsonString(requestId))).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("X-Request-Id", requestId);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
