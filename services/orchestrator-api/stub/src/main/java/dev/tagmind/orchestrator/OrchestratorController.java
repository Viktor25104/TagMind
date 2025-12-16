package dev.tagmind.orchestrator;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class OrchestratorController {

    private static final SecureRandom RNG = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    private static final int RETRIEVER_MAX_RESULTS = 3;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);
    private final RestTemplate restTemplate;
    private final String retrieverUrl;
    private final String llmUrl;

    public OrchestratorController() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restTemplate = new RestTemplate(factory);
        this.retrieverUrl = System.getenv().getOrDefault("RETRIEVER_URL", "http://web-retriever/v1/search");
        this.llmUrl = System.getenv().getOrDefault("LLM_URL", "http://llm-gateway/v1/complete");
    }

    private static String newRequestId() {
        byte[] b = new byte[12];
        RNG.nextBytes(b);
        return "req_" + HEX.formatHex(b);
    }

    private static String getOrCreateRequestId(HttpServletRequest req) {
        String id = req.getHeader("X-Request-Id");
        if (id != null) {
            id = id.trim();
            if (id.length() >= 8 && id.length() <= 128) return id;
        }
        return newRequestId();
    }

    @GetMapping(value = "/healthz", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> healthz(HttpServletRequest req) {
        String requestId = getOrCreateRequestId(req);
        return ResponseEntity.ok()
                .header("X-Request-Id", requestId)
                .body("ok");
    }

    @PostMapping(value = "/v1/orchestrate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> orchestrate(@RequestBody OrchestrateRequest body, HttpServletRequest req) {
        String requestId = getOrCreateRequestId(req);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("X-Request-Id", requestId);

        if (body.userId() == null || body.userId().trim().isEmpty()
                || body.chatId() == null || body.chatId().trim().isEmpty()
                || body.message() == null || body.message().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .headers(responseHeaders)
                    .body(Map.of(
                    "requestId", requestId,
                    "code", "BAD_REQUEST",
                    "message", "userId, chatId and message are required"
            ));
        }

        String mode = body.mode() == null || body.mode().trim().isEmpty() ? "chat" : body.mode().trim();
        String locale = body.locale() == null || body.locale().trim().isEmpty() ? "ru-RU" : body.locale().trim();
        String message = body.message().trim();

        boolean retrieverUsed = false;
        List<Map<String, String>> citations = List.of();
        try {
            RetrieverResponse retrieverResponse = maybeCallRetriever(message, mode, locale, requestId);
            if (retrieverResponse != null && retrieverResponse.results() != null && !retrieverResponse.results().isEmpty()) {
                retrieverUsed = true;
                citations = retrieverResponse.results().stream()
                        .map(r -> Map.of(
                                "url", r.url(),
                                "title", r.title(),
                                "snippet", r.snippet()
                        ))
                        .toList();
            }
        } catch (RestClientException ignored) {
            retrieverUsed = false;
            citations = List.of();
        }

        LlmResponse llmResponse;
        try {
            llmResponse = callLlmGateway(message, locale, citations, requestId);
        } catch (RestClientResponseException ex) {
            String errorMessage = "llm-gateway call failed (status=" + ex.getStatusCode().value() + ")";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(responseHeaders)
                    .body(Map.of(
                            "requestId", requestId,
                            "code", "LLM_ERROR",
                            "message", errorMessage
                    ));
        } catch (RestClientException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(responseHeaders)
                    .body(Map.of(
                            "requestId", requestId,
                            "code", "LLM_ERROR",
                            "message", "llm-gateway call failed"
                    ));
        }

        Map<String, Object> used = new HashMap<>();
        used.put("mode", mode);
        used.put("locale", locale);
        used.put("retrieverUsed", retrieverUsed);
        used.put("citationsCount", citations.size());

        return ResponseEntity.ok()
                .headers(responseHeaders)
                .body(Map.of(
                        "requestId", requestId,
                        "answer", llmResponse.text(),
                        "used", used
                ));
    }

    private RetrieverResponse maybeCallRetriever(String message, String mode, String locale, String requestId) {
        if ("no_context".equalsIgnoreCase(mode)) {
            return null;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("query", message);
        payload.put("lang", locale);
        payload.put("maxResults", RETRIEVER_MAX_RESULTS);
        payload.put("safe", true);
        payload.put("allowNoContext", true);

        ResponseEntity<RetrieverResponse> response = postWithRetry(retrieverUrl, payload, RetrieverResponse.class, requestId);
        return response.getBody();
    }

    private LlmResponse callLlmGateway(String prompt, String locale, List<Map<String, String>> citations, String requestId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("prompt", prompt);
        payload.put("locale", locale);
        payload.put("model", "stub");
        if (!citations.isEmpty()) {
            payload.put("citations", citations);
        }

        ResponseEntity<LlmResponse> response = postWithRetry(llmUrl, payload, LlmResponse.class, requestId);
        LlmResponse body = response.getBody();
        if (body == null) {
            throw new RestClientException("llm-gateway response missing body");
        }
        return body;
    }

    private HttpHeaders outboundHeaders(String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", requestId);
        return headers;
    }

    private <T> ResponseEntity<T> postWithRetry(String url, Object body, Class<T> type, String requestId) {
        RestClientException lastError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpEntity<Object> entity = new HttpEntity<>(body, outboundHeaders(requestId));
                return restTemplate.exchange(url, HttpMethod.POST, entity, type);
            } catch (RestClientResponseException ex) {
                throw ex;
            } catch (RestClientException ex) {
                lastError = ex;
                if (!(ex instanceof ResourceAccessException) || attempt == 1) {
                    throw ex;
                }
            }
        }
        if (lastError != null) throw lastError;
        throw new RestClientException("unexpected retry loop exit");
    }

    record RetrieverResponse(String requestId, List<RetrieverResult> results) {}

    record RetrieverResult(String title, String snippet, String url, String source, String publishedAt) {}

    record LlmResponse(String requestId, String text, Map<String, Object> usage) {}
}
