package dev.tagmind.orchestrator;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;

@RestController
public class OrchestratorController {

    private static final SecureRandom RNG = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

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
    public String healthz() {
        return "ok";
    }

    @PostMapping(value = "/v1/orchestrate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> orchestrate(@RequestBody OrchestrateRequest body, HttpServletRequest req) {
        String requestId = getOrCreateRequestId(req);

        if (body.userId() == null || body.userId().trim().isEmpty()
                || body.chatId() == null || body.chatId().trim().isEmpty()
                || body.message() == null || body.message().trim().isEmpty()) {
            return Map.of(
                    "requestId", requestId,
                    "code", "BAD_REQUEST",
                    "message", "userId, chatId and message are required"
            );
        }

        // Phase 4 mock: no calls to retriever/llm yet.
        return Map.of(
                "requestId", requestId,
                "answer", "stub: orchestrator received message; retriever/llm calls will be added in a later commit",
                "used", Map.of(
                        "mode", body.mode() == null ? "chat" : body.mode(),
                        "locale", body.locale() == null ? "ru-RU" : body.locale()
                )
        );
    }
}
