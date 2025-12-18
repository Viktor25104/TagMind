package dev.tagmind.orchestrator.conversations;

public record TagRequest(
        String contactId,
        String tag,
        Integer count,
        String payload,
        String locale,
        String text
) {}
