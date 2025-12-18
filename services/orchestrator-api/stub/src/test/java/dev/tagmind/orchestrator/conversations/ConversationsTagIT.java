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

    /**
     * Register test runtime properties for the Spring datasource and external service endpoints.
     *
     * Adds the JDBC URL, username, and password from the PostgreSQL Testcontainer to the given
     * DynamicPropertyRegistry and sets the system properties `LLM_URL` and `RETRIEVER_URL` to the
     * in-process stub servers' endpoints used by the tests.
     *
     * @param registry the dynamic property registry to receive datasource properties
     */
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        System.setProperty("LLM_URL", "http://127.0.0.1:" + llmStub.getAddress().getPort() + "/v1/complete");
        System.setProperty("RETRIEVER_URL", "http://127.0.0.1:" + retrieverStub.getAddress().getPort() + "/v1/search");
    }

    /**
     * Stops the in-process LLM and retriever HTTP stub servers and removes their system property URLs.
     */
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

    /**
     * Verifies that the "recap" tag returns the most recent conversation messages in chronological order and honors the requested history limit.
     *
     * Sets up a session with alternating IN/OUT messages, sends a recap request with count 3, and asserts that the response reports the history limit and used count, that the returned history is ordered as expected, and that the generated reply text matches the LLM stub.
     *
     * @throws Exception if an error occurs while performing the HTTP request or evaluating the response
     */
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
    void tag_off_blocksResponse() throws Exception {
        mvc.perform(post("/v1/conversations/upsert")
                        .contentType("application/json")
                        .content("""
                                {"contactId":"tg:off_tag","mode":"OFF"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(post("/v1/conversations/tag")
                        .contentType("application/json")
                        .content("""
                                {"contactId":"tg:off_tag","tag":"help","text":"@tagmind help:"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DO_NOT_RESPOND"))
                .andExpect(jsonPath("$.replyText").doesNotExist())
                .andExpect(jsonPath("$.used.mode").value("OFF"));
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

    /**
     * Creates and persists a ConversationMessageEntity for the given session with the specified direction and text.
     *
     * The persisted message is assigned a generated requestId.
     *
     * @param session   the conversation session to associate the message with
     * @param direction the message direction (IN or OUT)
     * @param text      the message text to store
     */
    private void storeMessage(ConversationSessionEntity session, MessageDirection direction, String text) {
        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setSession(session);
        message.setDirection(direction);
        message.setMessageText(text);
        message.setRequestId("req_" + UUID.randomUUID());
        messages.save(message);
    }

    /**
     * Starts an in-process HTTP stub that serves the LLM completion endpoint at /v1/complete on localhost.
     *
     * @return the started HttpServer bound to localhost on an ephemeral port
     */
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

    /**
     * Handles HTTP requests for the stubbed LLM completion endpoint and returns a fixed completion JSON.
     *
     * <p>Accepts only POST requests; other methods receive 405. Reads the incoming "X-Request-Id"
     * header (falls back to "req_tag_stub" when absent or blank), echoes it in the response header,
     * and returns a 200 response body containing `requestId`, `text` set to "tag-response", and a
     * `usage` object indicating the response is a stub.</p>
     *
     * @param exchange the HTTP exchange representing the request and response
     * @throws IOException if an I/O error occurs while reading or writing the exchange
     */
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

    /**
     * Escape a Java string and wrap it as a JSON string literal.
     *
     * @param s the input string to convert; may contain quotes or backslashes
     * @return the JSON string literal representing the input (surrounded by double quotes, with backslashes and quotes escaped)
     */
    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Starts an in-process HTTP stub that handles retriever search requests at the "/v1/search" path on localhost.
     *
     * @return an active HttpServer bound to an ephemeral port with the "/v1/search" context registered
     */
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

    /**
     * HTTP handler that responds to retriever search requests with a fixed two-result JSON payload.
     *
     * <p>Accepts only POST requests; for non-POST methods it sends a 405 response. It reads the
     * `X-Request-Id` request header and echoes it back (or uses `req_retriever_stub` when missing),
     * sets `Content-Type: application/json; charset=utf-8`, and returns a 200 response containing a
     * JSON object with the `requestId` and two predefined search result entries.</p>
     *
     * @param exchange the HTTP exchange representing the incoming request and outgoing response
     * @throws IOException if an I/O error occurs while sending the response
     */
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