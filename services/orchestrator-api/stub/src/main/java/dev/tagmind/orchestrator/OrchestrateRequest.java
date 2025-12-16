package dev.tagmind.orchestrator;

import java.util.Map;

public record OrchestrateRequest(
        String userId,
        String chatId,
        String message,
        String mode,
        String locale,
        Map<String, Object> context
) {}
