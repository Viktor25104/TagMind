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

    /**
     * Create a ConversationsService wired with the repositories, LLM gateway, retriever, and prompt builder required to manage sessions, messages, and tag-based prompts.
     *
     * @param sessions repository for conversation session lifecycle and lookup
     * @param messages repository for persisting conversation messages
     * @param llm      client used to obtain completions from the language model
     * @param retriever client used to perform optional external retrievals for tag-based prompts
     * @param prompts  builder that assembles tag-specific prompts (including history and retriever data)
     */
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

    /**
     * Create or update a conversation session for the given contact and set its mode.
     *
     * If no session exists for the contact, a new session is created; the session's mode
     * is set to the provided value and the session is persisted.
     *
     * @param contactId the identifier of the contact owning the session
     * @param mode      the conversation mode to assign to the session
     * @return          the persisted ConversationSessionEntity with the updated mode
     */
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

    /**
     * Handle an incoming user message for the given contact: persist the session and messages, optionally invoke the LLM, and produce a result describing the decision and reply.
     *
     * @param contactId   the contact identifier for the conversation session
     * @param messageText the incoming message text to process
     * @param requestId   an opaque request identifier used for tracking/persistence and LLM calls
     * @return a MessageResult containing the decision, the suggested reply text when applicable, the session id, and a metadata map (including the session mode and whether the LLM was called)
     * @throws RestClientException if the LLM client call fails
     */
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

    /**
     * Handle a tag-based request: ensure or create a session, persist the incoming tag input, optionally include history and retriever results, call the LLM, persist the LLM response, and return a TagResult describing the outcome.
     *
     * @param input     the tag request payload containing contactId, tag, optional count, payload, locale, and/or explicit text
     * @param requestId identifier propagated to downstream services for tracing the request
     * @return          a TagResult containing the decision ("RESPOND" or "DO_NOT_RESPOND"), the LLM reply text (null when not responding), the session and contact identifiers, the tag, and a `used` metadata map describing history/retriever/LLM usage and related diagnostics
     */
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

        String incomingText = resolveIncomingText(input);
        persistMessage(session, MessageDirection.IN, incomingText, requestId);

        if (session.getMode() == ConversationMode.OFF) {
            Map<String, Object> used = Map.of(
                    "mode", session.getMode().name(),
                    "llmCalled", false,
                    "retrieverUsed", false
            );
            return new TagResult(
                    "DO_NOT_RESPOND",
                    null,
                    session.getId(),
                    session.getContactId(),
                    input.tag(),
                    used
            );
        }

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

        persistMessage(session, MessageDirection.OUT, llmResponse.text(), requestId);

        return new TagResult(
                "RESPOND",
                llmResponse.text(),
                session.getId(),
                session.getContactId(),
                input.tag(),
                used
        );
    }

    /**
     * Fetches recent message history for the given session when the requested tag requires contextual history.
     *
     * <p>The returned history is ordered from oldest to newest and is limited according to the tag-specific
     * effective count or the explicit count in the input.</p>
     *
     * @param session the conversation session whose history should be considered
     * @param input input that contains the tag and optional requested count; the tag determines whether history is required
     * @return a HistoryResult containing the effective limit used and a list of history entries (empty if history is not required or no messages exist)
     */
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

    /**
     * Determine whether a tag requires including recent conversation history.
     *
     * <p>Specifically, the tags "recap", "judge", and "fix" require history.</p>
     *
     * @param tag the tag to evaluate (e.g., "recap", "judge", "fix")
     * @return `true` if the tag requires recent conversation history, `false` otherwise
     */
    private boolean requiresHistory(String tag) {
        return switch (tag) {
            case "recap", "judge", "fix" -> true;
            default -> false;
        };
    }

    /**
     * Resolve the number of history items to fetch for a given tag.
     *
     * @param tag the tag name that may determine a default count
     * @param requested an optional requested count; if non-null and greater than zero it is used
     * @return the effective count: `requested` if positive, otherwise `8` for "judge", `5` for "fix", and `10` for other tags
     */
    private int effectiveCount(String tag, Integer requested) {
        if (requested != null && requested > 0) return requested;
        return switch (tag) {
            case "judge" -> 8;
            case "fix" -> 5;
            default -> 10;
        };
    }

    /**
     * Calls the retriever for "web" tag requests and returns search citations when available.
     *
     * @param input the tag input; the method uses the input's tag, payload (as query), and locale
     * @param requestId correlation id forwarded to the retriever call
     * @return a RetrieverContext with `used` = true and a list of citation maps (keys: `title`, `snippet`, `url`, `source`, `publishedAt`) when results are returned; `used` = false and an empty list otherwise
     */
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
            String locale,
            String text
    ) {}

    private record HistoryResult(int limit, List<TagPromptBuilder.HistoryEntry> entries) {
        /**
         * Produce a debug-friendly representation of the history entries as a list of maps.
         *
         * @return a list where each element is a map with keys "direction", "text", and "createdAt"
         *         mapped to the corresponding entry's direction, text, and creation timestamp
         */
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

    /**
     * Persist a conversation message linked to the given session with the provided direction, text, and request identifier.
     *
     * @param session   the conversation session to associate the message with
     * @param direction the message direction (IN or OUT)
     * @param text      the message content to store
     * @param requestId an optional request identifier to correlate this message with an external request
     */
    private void persistMessage(ConversationSessionEntity session, MessageDirection direction, String text, String requestId) {
        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setSession(session);
        message.setDirection(direction);
        message.setMessageText(text);
        message.setRequestId(requestId);
        messages.save(message);
    }

    /**
     * Builds the textual representation of an incoming tag request.
     *
     * If `input.text()` is provided and not empty, that trimmed text is returned.
     * Otherwise a command-style string is produced in the form
     * "`@tagmind <tag>`" with an optional "`[count]`" appended when `input.count()` > 0
     * and an optional "`: <payload>`" appended when `input.payload()` is present.
     *
     * @param input the tag request input
     * @return the resolved incoming text (either the trimmed `input.text()` or the constructed tag command)
     */
    private String resolveIncomingText(TagInput input) {
        if (input.text() != null && !input.text().trim().isEmpty()) {
            return input.text().trim();
        }
        StringBuilder sb = new StringBuilder("@tagmind ").append(input.tag());
        if (input.count() != null && input.count() > 0) {
            sb.append("[").append(input.count()).append("]");
        }
        if (input.payload() != null && !input.payload().trim().isEmpty()) {
            sb.append(": ").append(input.payload().trim());
        }
        return sb.toString();
    }
}