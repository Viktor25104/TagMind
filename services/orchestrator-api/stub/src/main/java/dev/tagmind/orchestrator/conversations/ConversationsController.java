package dev.tagmind.orchestrator.conversations;

import dev.tagmind.orchestrator.persistence.ConversationMode;
import dev.tagmind.orchestrator.persistence.ConversationSessionEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

@RestController
public class ConversationsController {

    private static final SecureRandom RNG = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    private static final Set<String> SUPPORTED_TAGS = Set.of(
            "help", "llm", "web", "recap", "judge", "fix", "plan", "safe"
    );

    private final ConversationsService service;

    public ConversationsController(ConversationsService service) {
        this.service = service;
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

    @PostMapping(
            value = "/v1/conversations/upsert",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> upsert(@RequestBody UpsertConversationRequest body, HttpServletRequest req) {
        String requestId = getOrCreateRequestId(req);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("X-Request-Id", requestId);

        if (body.contactId() == null || body.contactId().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .headers(responseHeaders)
                    .body(Map.of(
                            "requestId", requestId,
                            "code", "BAD_REQUEST",
                            "message", "contactId is required"
                    ));
        }
        if (body.mode() == null) {
            return ResponseEntity.badRequest()
                    .headers(responseHeaders)
                    .body(Map.of(
                            "requestId", requestId,
                            "code", "BAD_REQUEST",
                            "message", "mode is required"
                    ));
        }

        ConversationMode mode;
        try {
            mode = ConversationMode.valueOf(body.mode().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .headers(responseHeaders)
                    .body(Map.of(
                            "requestId", requestId,
                            "code", "BAD_REQUEST",
                            "message", "mode must be OFF or SUGGEST"
                    ));
        }

        ConversationSessionEntity session = service.upsert(body.contactId().trim(), mode);

        return ResponseEntity.ok()
                .headers(responseHeaders)
                .body(Map.of(
                        "requestId", requestId,
                        "sessionId", session.getId().toString(),
                        "contactId", session.getContactId(),
                        "mode", session.getMode().name()
                ));
    }

    @PostMapping(
            value = "/v1/conversations/message",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> message(@RequestBody ConversationMessageRequest body, HttpServletRequest req) {
        String requestId = getOrCreateRequestId(req);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("X-Request-Id", requestId);

        if (body.contactId() == null || body.contactId().trim().isEmpty()
                || body.message() == null || body.message().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .headers(responseHeaders)
                    .body(Map.of(
                            "requestId", requestId,
                            "code", "BAD_REQUEST",
                            "message", "contactId and message are required"
                    ));
        }

        ConversationsService.MessageResult result;
        try {
            result = service.handleMessage(body.contactId().trim(), body.message().trim(), requestId);
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(responseHeaders)
                    .body(Map.of(
                            "requestId", requestId,
                            "code", "LLM_ERROR",
                            "message", "llm-gateway call failed (status=" + ex.getStatusCode().value() + ")"
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

        return ResponseEntity.ok()
                .headers(responseHeaders)
                .body(messageResponseBody(requestId, result));
    }

    private static Map<String, Object> messageResponseBody(String requestId, ConversationsService.MessageResult result) {
        Map<String, Object> body = new HashMap<>();
        body.put("requestId", requestId);
        body.put("decision", result.decision());
        body.put("suggestedReply", result.suggestedReply());
        body.put("sessionId", result.sessionId().toString());
        body.put("used", result.used());
        return body;
    }

    @PostMapping(
            value = "/v1/conversations/tag",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> tag(@RequestBody TagRequest body, HttpServletRequest req) {
        String requestId = getOrCreateRequestId(req);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("X-Request-Id", requestId);

        String contactId = body.contactId() == null ? "" : body.contactId().trim();
        String tag = body.tag() == null ? "" : body.tag().trim().toLowerCase();
        Integer count = body.count();
        String payload = body.payload() == null ? null : body.payload().trim();
        String locale = body.locale() == null || body.locale().trim().isEmpty() ? "ru-RU" : body.locale().trim();

        if (contactId.isEmpty()) {
            return badRequest(responseHeaders, requestId, "contactId is required");
        }
        if (tag.isEmpty()) {
            return badRequest(responseHeaders, requestId, "tag is required");
        }
        if (!SUPPORTED_TAGS.contains(tag)) {
            return badRequest(responseHeaders, requestId, "tag is not supported");
        }
        if (count != null && count <= 0) {
            return badRequest(responseHeaders, requestId, "count must be positive");
        }

        ConversationsService.TagResult result;
        try {
            result = service.handleTag(
                    new ConversationsService.TagInput(contactId, tag, count, payload, locale),
                    requestId
            );
        } catch (RestClientResponseException ex) {
            return upstreamError(responseHeaders, requestId, "LLM_ERROR", "llm-gateway call failed (status=" + ex.getStatusCode().value() + ")");
        } catch (RestClientException ex) {
            return upstreamError(responseHeaders, requestId, "LLM_ERROR", "llm-gateway call failed");
        }

        return ResponseEntity.ok()
                .headers(responseHeaders)
                .body(Map.of(
                        "requestId", requestId,
                        "decision", result.decision(),
                        "replyText", result.replyText(),
                        "sessionId", result.sessionId().toString(),
                        "contactId", result.contactId(),
                        "tag", result.tag(),
                        "used", result.used()
                ));
    }

    private ResponseEntity<Map<String, Object>> badRequest(HttpHeaders headers, String requestId, String message) {
        return ResponseEntity.badRequest()
                .headers(headers)
                .body(Map.of(
                        "requestId", requestId,
                        "code", "BAD_REQUEST",
                        "message", message
                ));
    }

    private ResponseEntity<Map<String, Object>> upstreamError(HttpHeaders headers, String requestId, String code, String message) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .headers(headers)
                .body(Map.of(
                        "requestId", requestId,
                        "code", code,
                        "message", message
                ));
    }
}
