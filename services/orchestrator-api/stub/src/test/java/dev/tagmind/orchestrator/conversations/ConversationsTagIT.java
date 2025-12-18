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
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.List;
import java.util.Map;
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
    static final HttpServer retrieverStub = startRetrieverStub();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        System.setProperty("LLM_URL", "http://127.0.0.1:" + llmStub.getAddress().getPort() + "/v1/complete");
        System.setProperty("RETRIEVER_URL", "http://127.0.0.1:" + retrieverStub.getAddress().getPort() + "/v1/search");
    }

    @AfterAll
    static void shutdown() {
        llmStub.stop(0);
        retrieverStub.stop(0);
        System.clearProperty("LLM_URL");
        System.clearProperty("RETRIEVER_URL");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ConversationSessionRepository sessions;

    @Autowired
    ConversationMessageRepository messages;

    @Autowired
    JdbcTemplate jdbc;

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
                                {"contactId":"tg:test","tag":"help","payload":"ping","text":"@tagmind help: ping"}
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
                                {"contactId":"tg:history","tag":"recap","count":3,"text":"@tagmind recap[3]:"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.used.historyLimit").value(3))
                .andExpect(jsonPath("$.used.historyUsed").value(3))
                .andExpect(jsonPath("$.used.history[0].text").value("msg2"))
                .andExpect(jsonPath("$.used.history[2].text").value("msg4"))
                .andExpect(jsonPath("$.replyText").value("tag-response"));
    }

    @Test
    void tag_web_usesRetriever() throws Exception {
        mvc.perform(post("/v1/conversations/tag")
                        .contentType("application/json")
                        .content("""
                                {"contactId":"tg:web","tag":"web","payload":"Новости ИИ","text":"@tagmind web: Новости ИИ"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.used.retrieverUsed").value(true))
                .andExpect(jsonPath("$.used.citationsCount").value(2))
                .andExpect(jsonPath("$.replyText").value("tag-response"));
    }

    @Test
    void tag_persistsInOutMessages() throws Exception {
        mvc.perform(post("/v1/conversations/tag")
                        .contentType("application/json")
                        .content("""
                                {"contactId":"tg:persist","tag":"help","payload":"hi","text":"@tagmind help: hi"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("RESPOND"));

        ConversationSessionEntity session = sessions.findByContactId("tg:persist").orElseThrow();
        List<Map<String, Object>> rows = jdbc.query(
                """
                        select m.direction, m.message_text
                        from conversation_messages m
                        where m.session_id=?
                        order by m.created_at asc
                        """,
                (rs, rowNum) -> Map.of(
                        "direction", rs.getString(1),
                        "text", rs.getString(2)
                ),
                session.getId()
        );
        if (rows.size() != 2) {
            throw new AssertionError("expected 2 messages, got " + rows.size());
        }
        if (!"IN".equals(rows.get(0).get("direction")) || !"@tagmind help: hi".equals(rows.get(0).get("text"))) {
            throw new AssertionError("unexpected IN message: " + rows.get(0));
        }
        if (!"OUT".equals(rows.get(1).get("direction")) || !"tag-response".equals(rows.get(1).get("text"))) {
            throw new AssertionError("unexpected OUT message: " + rows.get(1));
        }
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

    private static HttpServer startRetrieverStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/search", ConversationsTagIT::handleSearch);
            server.start();
            return server;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void handleSearch(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
        if (requestId == null || requestId.isBlank()) requestId = "req_retriever_stub";

        byte[] body = ("""
                {"requestId":%s,"results":[
                  {"title":"Result 1","snippet":"Snippet 1","url":"https://example.com/1","source":"example","publishedAt":"2024-01-01T00:00:00Z"},
                  {"title":"Result 2","snippet":"Snippet 2","url":"https://example.com/2","source":"example","publishedAt":"2024-01-02T00:00:00Z"}
                ]}
                """.formatted(jsonString(requestId))).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("X-Request-Id", requestId);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
