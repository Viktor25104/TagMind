package dev.tagmind.orchestrator.conversations;

import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class LlmGatewayClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestTemplate restTemplate;
    private final String llmUrl;

    /**
     * Constructs an LlmGatewayClient, configuring the HTTP client and resolving the LLM gateway URL.
     *
     * <p>The constructor creates a RestTemplate with predefined connect and read timeouts and sets
     * the instance's LLM gateway URL by reading the environment variable `LLM_URL`, falling back to
     * the system property `LLM_URL`, and finally to the default {@code http://llm-gateway/v1/complete}
     * if neither is provided.</p>
     */
    public LlmGatewayClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restTemplate = new RestTemplate(factory);
        String env = System.getenv("LLM_URL");
        if (env != null && !env.trim().isEmpty()) {
            this.llmUrl = env.trim();
        } else {
            String sys = System.getProperty("LLM_URL");
            this.llmUrl = (sys != null && !sys.trim().isEmpty())
                    ? sys.trim()
                    : "http://llm-gateway/v1/complete";
        }
    }

    /**
     * Send a completion request to the configured LLM gateway and return its parsed response.
     *
     * @param prompt    the text prompt to send to the LLM
     * @param locale    the locale to include in the request; when null or empty, "ru-RU" is used
     * @param requestId identifier added to the "X-Request-Id" header for request tracing
     * @return          the deserialized LlmResponse returned by the gateway
     * @throws RestClientException if the HTTP request fails or the gateway response has no body
     */
    public LlmResponse complete(String prompt, String locale, String requestId) throws RestClientException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("prompt", prompt);
        String effectiveLocale = (locale == null || locale.trim().isEmpty()) ? "ru-RU" : locale.trim();
        payload.put("locale", effectiveLocale);
        payload.put("model", "stub");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", requestId);

        ResponseEntity<LlmResponse> response = restTemplate.exchange(
                llmUrl,
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                LlmResponse.class
        );

        LlmResponse body = response.getBody();
        if (body == null) {
            throw new RestClientException("llm-gateway response missing body");
        }
        return body;
    }

    /**
     * Completes the given prompt using the default locale "ru-RU".
     *
     * @param prompt    the text prompt to send to the LLM gateway
     * @param requestId the request identifier to include in the gateway request headers
     * @return          the LlmResponse returned by the LLM gateway
     * @throws RestClientException if the HTTP request fails or the gateway response is missing a body
     */
    public LlmResponse complete(String prompt, String requestId) throws RestClientException {
        return complete(prompt, "ru-RU", requestId);
    }

    public record LlmResponse(String requestId, String text, Map<String, Object> usage) {}
}