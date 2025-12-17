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

    public LlmGatewayClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restTemplate = new RestTemplate(factory);
        this.llmUrl = System.getenv().getOrDefault("LLM_URL", "http://llm-gateway/v1/complete");
    }

    public LlmResponse complete(String prompt, String requestId) throws RestClientException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("prompt", prompt);
        payload.put("locale", "ru-RU");
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

    public record LlmResponse(String requestId, String text, Map<String, Object> usage) {}
}

