package dev.tagmind.orchestrator.conversations;

public record UpsertConversationRequest(
        String contactId,
        String mode
) {}

