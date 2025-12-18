package dev.tagmind.orchestrator.conversations;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RetrieverClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestTemplate restTemplate;
    private final String retrieverUrl;

    public RetrieverClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restTemplate = new RestTemplate(factory);
        String env = System.getenv("RETRIEVER_URL");
        if (env != null && !env.trim().isEmpty()) {
            this.retrieverUrl = env.trim();
        } else {
            String sys = System.getProperty("RETRIEVER_URL");
            this.retrieverUrl = (sys != null && !sys.trim().isEmpty())
                    ? sys.trim()
                    : "http://web-retriever/v1/search";
        }
    }

    public RetrieverResponse search(String query, String locale, int maxResults, String requestId) throws RestClientException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        payload.put("lang", locale);
        payload.put("maxResults", maxResults);
        payload.put("safe", true);
        payload.put("allowNoContext", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", requestId);

        ResponseEntity<RetrieverResponse> response = restTemplate.exchange(
                retrieverUrl,
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                RetrieverResponse.class
        );

        return response.getBody();
    }

    public record RetrieverResponse(String requestId, List<RetrieverResult> results) {}

    public record RetrieverResult(String title, String snippet, String url, String source, String publishedAt) {}
}
