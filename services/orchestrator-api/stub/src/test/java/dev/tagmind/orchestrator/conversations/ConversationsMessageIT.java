package dev.tagmind.orchestrator.conversations;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
import java.util.Objects;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConversationsMessageIT {

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
    JdbcTemplate jdbc;

    @Test
    void message_off_doesNotCallLlm_andPersistsIncomingOnly() throws Exception {
        mvc.perform(post("/v1/conversations/upsert")
                        .contentType("application/json")
                        .header("X-Request-Id", "req_msg_off_12345678")
                        .content("""
                                {"contactId":"tg:off","mode":"OFF"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(post("/v1/conversations/message")
                        .contentType("application/json")
                        .header("X-Request-Id", "req_msg_off_12345678")
                        .content("""
                                {"contactId":"tg:off","message":"hi"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DO_NOT_RESPOND"))
                .andExpect(jsonPath("$.suggestedReply").isEmpty())
                .andExpect(jsonPath("$.used.llmCalled").value(false));

        UUID sessionId = jdbc.queryForObject(
                "select id from conversation_sessions where contact_id=?",
                (rs, rowNum) -> UUID.fromString(rs.getString(1)),
                "tg:off"
        );
        long msgCount = Objects.requireNonNull(jdbc.queryForObject(
                "select count(*) from conversation_messages where session_id=?",
                Long.class,
                sessionId
        ));
        long inCount = Objects.requireNonNull(jdbc.queryForObject(
                "select count(*) from conversation_messages where session_id=? and direction='IN'",
                Long.class,
                sessionId
        ));
        long outCount = Objects.requireNonNull(jdbc.queryForObject(
                "select count(*) from conversation_messages where session_id=? and direction='OUT'",
                Long.class,
                sessionId
        ));
        if (msgCount != 1 || inCount != 1 || outCount != 0) {
            throw new AssertionError("unexpected messages: total=" + msgCount + " in=" + inCount + " out=" + outCount);
        }
    }

    @Test
    void message_suggest_callsLlm_andPersistsIncomingAndOutgoing() throws Exception {
        mvc.perform(post("/v1/conversations/message")
                        .contentType("application/json")
                        .header("X-Request-Id", "req_msg_suggest_12345678")
                        .content("""
                                {"contactId":"tg:suggest","message":"hello"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("SUGGEST"))
                .andExpect(jsonPath("$.suggestedReply").value("stubbed reply"))
                .andExpect(jsonPath("$.used.llmCalled").value(true));

        UUID sessionId = jdbc.queryForObject(
                "select id from conversation_sessions where contact_id=?",
                (rs, rowNum) -> UUID.fromString(rs.getString(1)),
                "tg:suggest"
        );
        long msgCount = Objects.requireNonNull(jdbc.queryForObject(
                "select count(*) from conversation_messages where session_id=?",
                Long.class,
                sessionId
        ));
        long inCount = Objects.requireNonNull(jdbc.queryForObject(
                "select count(*) from conversation_messages where session_id=? and direction='IN'",
                Long.class,
                sessionId
        ));
        long outCount = Objects.requireNonNull(jdbc.queryForObject(
                "select count(*) from conversation_messages where session_id=? and direction='OUT'",
                Long.class,
                sessionId
        ));
        if (msgCount != 2 || inCount != 1 || outCount != 1) {
            throw new AssertionError("unexpected messages: total=" + msgCount + " in=" + inCount + " out=" + outCount);
        }
    }

    private static HttpServer startLlmStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/complete", ConversationsMessageIT::handleComplete);
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
        if (requestId == null || requestId.isBlank()) requestId = "req_test_stub";

        byte[] body = ("""
                {"requestId":%s,"text":"stubbed reply","usage":{"stub":true}}
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

