package dev.tagmind.orchestrator.conversations;

public record ConversationMessageRequest(
        String contactId,
        String message
) {}

