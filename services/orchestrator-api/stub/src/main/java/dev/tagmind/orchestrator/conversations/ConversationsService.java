package dev.tagmind.orchestrator.conversations;

import dev.tagmind.orchestrator.persistence.ConversationMode;
import dev.tagmind.orchestrator.persistence.ConversationMessageEntity;
import dev.tagmind.orchestrator.persistence.ConversationMessageRepository;
import dev.tagmind.orchestrator.persistence.ConversationSessionEntity;
import dev.tagmind.orchestrator.persistence.ConversationSessionRepository;
import dev.tagmind.orchestrator.persistence.MessageDirection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConversationsService {

    private final ConversationSessionRepository sessions;
    private final ConversationMessageRepository messages;
    private final LlmGatewayClient llm;
    private final RetrieverClient retriever;
    private final TagPromptBuilder prompts;

    public ConversationsService(
            ConversationSessionRepository sessions,
            ConversationMessageRepository messages,
            LlmGatewayClient llm,
            RetrieverClient retriever,
            TagPromptBuilder prompts
    ) {
        this.sessions = sessions;
        this.messages = messages;
        this.llm = llm;
        this.retriever = retriever;
        this.prompts = prompts;
    }

    @Transactional
    public ConversationSessionEntity upsert(String contactId, ConversationMode mode) {
        ConversationSessionEntity session = sessions.findByContactId(contactId)
                .orElseGet(() -> {
                    ConversationSessionEntity s = new ConversationSessionEntity();
                    s.setContactId(contactId);
                    return s;
                });

        session.setMode(mode);
        return sessions.save(session);
    }

    @Transactional
    public MessageResult handleMessage(String contactId, String messageText, String requestId) {
        ConversationSessionEntity session = sessions.findByContactId(contactId)
                .orElseGet(() -> {
                    ConversationSessionEntity s = new ConversationSessionEntity();
                    s.setContactId(contactId);
                    s.setMode(ConversationMode.SUGGEST);
                    return s;
                });

        session.touch();
        session = sessions.save(session);

        ConversationMessageEntity incoming = new ConversationMessageEntity();
        incoming.setSession(session);
        incoming.setDirection(MessageDirection.IN);
        incoming.setMessageText(messageText);
        incoming.setRequestId(requestId);
        messages.save(incoming);

        if (session.getMode() == ConversationMode.OFF) {
            return new MessageResult(
                    "DO_NOT_RESPOND",
                    null,
                    session.getId(),
                    Map.of(
                            "mode", session.getMode().name(),
                            "llmCalled", false
                    )
            );
        }

        String suggestedReply;
        try {
            suggestedReply = llm.complete(messageText, requestId).text();
        } catch (RestClientException ex) {
            throw ex;
        }

        ConversationMessageEntity outgoing = new ConversationMessageEntity();
        outgoing.setSession(session);
        outgoing.setDirection(MessageDirection.OUT);
        outgoing.setMessageText(suggestedReply);
        outgoing.setRequestId(requestId);
        messages.save(outgoing);

        return new MessageResult(
                "SUGGEST",
                suggestedReply,
                session.getId(),
                Map.of(
                        "mode", session.getMode().name(),
                        "llmCalled", true
                )
        );
    }

    @Transactional
    public TagResult handleTag(TagInput input, String requestId) {
        ConversationSessionEntity session = sessions.findByContactId(input.contactId())
                .orElseGet(() -> {
                    ConversationSessionEntity s = new ConversationSessionEntity();
                    s.setContactId(input.contactId());
                    s.setMode(ConversationMode.SUGGEST);
                    return s;
                });
        session.touch();
        session = sessions.save(session);

        HistoryResult historyResult = fetchHistoryIfNeeded(session, input);
        RetrieverContext retrieverContext = maybeCallRetriever(input, requestId);
        TagPromptBuilder.TagPrompt prompt = prompts.build(input, historyResult.entries(), retrieverContext.results());

        LlmGatewayClient.LlmResponse llmResponse = llm.complete(prompt.prompt(), input.locale(), requestId);

        Map<String, Object> used = new HashMap<>();
        used.put("tag", input.tag());
        used.put("locale", input.locale());
        used.put("requestedCount", input.count());
        used.put("historyUsed", historyResult.entries().size());
        used.put("llmCalled", true);
        used.put("promptType", prompt.type());
        used.put("promptTokens", prompt.tokenEstimate());
        used.put("implemented", true);
        used.putAll(prompt.debug());
        used.put("retrieverUsed", retrieverContext.used());
        used.put("citationsCount", retrieverContext.results().size());
        if (!historyResult.entries().isEmpty()) {
            used.put("historyLimit", historyResult.limit());
            used.put("history", historyResult.asDebugHistory());
        }
        if (!retrieverContext.results().isEmpty()) {
            used.put("citations", retrieverContext.results());
        }

        return new TagResult(
                "RESPOND",
                llmResponse.text(),
                session.getId(),
                session.getContactId(),
                input.tag(),
                used
        );
    }

    private HistoryResult fetchHistoryIfNeeded(ConversationSessionEntity session, TagInput input) {
        if (!requiresHistory(input.tag())) {
            return new HistoryResult(0, List.of());
        }
        int limit = effectiveCount(input.tag(), input.count());
        PageRequest page = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<ConversationMessageEntity> latest = messages.findBySession(session, page);
        if (latest.isEmpty()) {
            return new HistoryResult(limit, List.of());
        }
        List<ConversationMessageEntity> copy = new ArrayList<>(latest);
        Collections.reverse(copy);
        List<TagPromptBuilder.HistoryEntry> history = copy.stream()
                .map(msg -> new TagPromptBuilder.HistoryEntry(
                        msg.getDirection().name(),
                        msg.getMessageText(),
                        msg.getCreatedAt().toString()
                ))
                .toList();
        return new HistoryResult(limit, history);
    }

    private boolean requiresHistory(String tag) {
        return switch (tag) {
            case "recap", "judge", "fix" -> true;
            default -> false;
        };
    }

    private int effectiveCount(String tag, Integer requested) {
        if (requested != null && requested > 0) return requested;
        return switch (tag) {
            case "judge" -> 8;
            case "fix" -> 5;
            default -> 10;
        };
    }

    private RetrieverContext maybeCallRetriever(TagInput input, String requestId) {
        if (!"web".equals(input.tag())) {
            return new RetrieverContext(false, List.of());
        }
        String query = (input.payload() == null || input.payload().trim().isEmpty())
                ? "TagMind web search placeholder"
                : input.payload().trim();
        String locale = (input.locale() == null || input.locale().trim().isEmpty())
                ? "ru-RU"
                : input.locale().trim();
        RetrieverClient.RetrieverResponse response = retriever.search(query, locale, 3, requestId);
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return new RetrieverContext(false, List.of());
        }
        List<Map<String, Object>> citations = response.results().stream()
                .map(r -> Map.<String, Object>of(
                        "title", r.title(),
                        "snippet", r.snippet(),
                        "url", r.url(),
                        "source", r.source(),
                        "publishedAt", r.publishedAt()
                ))
                .toList();
        return new RetrieverContext(true, citations);
    }

    public record MessageResult(
            String decision,
            String suggestedReply,
            UUID sessionId,
            Map<String, Object> used
    ) {}

    public record TagResult(
            String decision,
            String replyText,
            UUID sessionId,
            String contactId,
            String tag,
            Map<String, Object> used
    ) {}

    public record TagInput(
            String contactId,
            String tag,
            Integer count,
            String payload,
            String locale
    ) {}

    private record HistoryResult(int limit, List<TagPromptBuilder.HistoryEntry> entries) {
        List<Map<String, Object>> asDebugHistory() {
            return entries.stream()
                    .map(entry -> Map.<String, Object>of(
                            "direction", entry.direction(),
                            "text", entry.text(),
                            "createdAt", entry.createdAt()
                    ))
                    .toList();
        }
    }

    private record RetrieverContext(boolean used, List<Map<String, Object>> results) {}
}
