package dev.tagmind.orchestrator.conversations;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Component
public class TagPromptBuilder {

    /**
     * Builds a TagPrompt determined by the tag in the provided input, using chat history and citations when applicable.
     *
     * @param input     contains the selected tag and optional payload/count that determine which prompt is constructed
     * @param history   chat history entries that are used by prompts requiring conversation context
     * @param citations web citation entries (maps with title/snippet/url) used exclusively by the "web" prompt
     * @return          a TagPrompt instance representing the prompt for the specified tag
     */
    public TagPrompt build(ConversationsService.TagInput input, List<HistoryEntry> history, List<Map<String, Object>> citations) {
        return switch (input.tag()) {
            case "help" -> helpPrompt();
            case "llm" -> llmPrompt(input.payload());
            case "recap" -> recapPrompt(history, input.count());
            case "judge" -> judgePrompt(history);
            case "fix" -> fixPrompt(history, input.payload());
            case "plan" -> planPrompt(input.payload());
            case "safe" -> safePrompt(input.payload());
            case "web" -> webPrompt(input.payload(), citations);
            default -> llmPrompt(input.payload());
        };
    }

    /**
     * Builds a help prompt that lists available @tagmind commands and short guidance in Russian.
     *
     * @return a TagPrompt of type "help" whose prompt contains concise Russian descriptions of each command; debug map is empty
     */
    private TagPrompt helpPrompt() {
        String prompt = """
                You are TagMind assistant. Explain available @tagmind commands with short guidance:
                - help: list tags
                - llm: answer free-form questions
                - web: perform web search with citations
                - recap[n]: summarize last N chat messages (default 10)
                - judge[n]: compare viewpoints from last N messages (default 8)
                - fix[n]: improve last N messages + payload (default 5)
                - plan: build plan of actions
                - safe: assess risks and safety considerations
                Keep it concise in Russian.
                """;
        return new TagPrompt("help", prompt.trim(), Map.of());
    }

    /**
     * Builds a conversational prompt requesting a detailed answer to the user's query.
     *
     * If `payload` is null or empty, the prompt asks the user politely what they need.
     *
     * @param payload user-provided query text; may be null or empty
     * @return a TagPrompt with type "llm", the composed prompt text, and a debug map containing the payload length under the key "payloadLen"
     */
    private TagPrompt llmPrompt(String payload) {
        String content = (payload == null || payload.trim().isEmpty())
                ? "Пользователь ничего не написал, спроси вежливо, что ему нужно."
                : payload.trim();
        String prompt = """
                Пользователь обратился к тебе напрямую. Ответь развёрнуто и по существу.
                Вопрос: %s
                """.formatted(content);
        return new TagPrompt("llm", prompt.trim(), Map.of("payloadLen", content.length()));
    }

    /**
     * Builds a prompt that asks for a one-paragraph concise summary of recent chat messages.
     *
     * @param history list of history entries in chronological order (oldest first)
     * @param count   optional requested number of messages to summarize; may be {@code null}
     * @return a TagPrompt with type "recap", the generated summary prompt, and a debug map containing the number of history entries provided and the requested count
     */
    private TagPrompt recapPrompt(List<HistoryEntry> history, Integer count) {
        String formatted = formatHistory(history);
        String prompt = """
                Даны последние сообщения чата (от старых к новым). Одним абзацем дай сжатое резюме ключевых пунктов без лишних деталей.
                История:
                %s
                """.formatted(formatted);
        Map<String, Object> debug = Map.of(
                "historyProvided", history.size(),
                "requested", count
        );
        return new TagPrompt("recap", prompt.trim(), debug);
    }

    /**
     * Builds a prompt that requests an impartial judgment of the provided conversation history.
     *
     * @param history list of HistoryEntry objects representing conversation messages ordered from oldest to newest
     * @return a TagPrompt of type "judge" containing the generated prompt (asks for positions, strengths/weaknesses, and a verdict) and a debug map with key "historyProvided" set to the number of history entries
     */
    private TagPrompt judgePrompt(List<HistoryEntry> history) {
        String prompt = """
                Ты — беспристрастный судья. Проанализируй дискуссию (сообщения перечислены от старых к новым) и дай вывод:
                1) Кратко изложи позицию стороны A (пользователь) и стороны B (бот/собеседник).
                2) Укажи сильные и слабые аргументы.
                3) Вынеси вердикт: кто прав/не прав/нужны данные.
                История:
                %s
                """.formatted(formatHistory(history));
        return new TagPrompt("judge", prompt.trim(), Map.of("historyProvided", history.size()));
    }

    /**
     * Builds a prompt that asks to improve the wording of a provided text, including recent conversation
     * context when available.
     *
     * @param history  recent chat entries to include as context for the rewrite
     * @param payload  additional text to be improved; if null or empty, a placeholder indicating no extra text is used
     * @return         a TagPrompt of type "fix" whose prompt (in Russian) requests an improved version of the provided text;
     *                 the debug map contains `payloadLen` (length of the request text) and `historyProvided` (number of history entries)
     */
    private TagPrompt fixPrompt(List<HistoryEntry> history, String payload) {
        StringJoiner joiner = new StringJoiner("\n");
        if (!history.isEmpty()) {
            joiner.add("Контекст диалога:");
            joiner.add(formatHistory(history));
        }
        String request = (payload == null || payload.trim().isEmpty())
                ? "Нет дополнительного текста."
                : payload.trim();
        joiner.add("Нужно улучшить формулировку следующего текста, сохранив смысл и стиль:");
        joiner.add(request);
        joiner.add("Выдай улучшенную версию по-русски.");

        return new TagPrompt("fix", joiner.toString(), Map.of(
                "payloadLen", request.length(),
                "historyProvided", history.size()
        ));
    }

    /**
     * Builds a TagPrompt that requests a 3–5 step action plan for a given subject.
     *
     * If {@code payload} is null or empty, the subject defaults to "неопределённую задачу".
     *
     * @param payload the subject for the plan (trimmed); may be null or empty
     * @return a TagPrompt with type "plan", a Russian prompt asking for 3–5 steps with brief explanations and expected results for the subject, and a debug map containing the key "payloadLen" set to the subject's length
     */
    private TagPrompt planPrompt(String payload) {
        String subject = (payload == null || payload.trim().isEmpty())
                ? "неопределённую задачу"
                : payload.trim();
        String prompt = """
                Построй план действий из 3-5 шагов для: %s.
                Для каждого шага добавь краткое объяснение и ожидаемый результат.
                """.formatted(subject);
        return new TagPrompt("plan", prompt.trim(), Map.of("payloadLen", subject.length()));
    }

    /**
     * Builds a TagPrompt that performs a safety assessment for a specified situation.
     *
     * @param payload user-provided description of the situation; if null or blank, the prompt will use "неизвестную ситуацию"
     * @return a TagPrompt with type "safe", a Russian-language prompt requesting risk description, safe continuation recommendations, and constraints, and a debug map containing `payloadLen`
     */
    private TagPrompt safePrompt(String payload) {
        String topic = (payload == null || payload.trim().isEmpty())
                ? "неизвестную ситуацию"
                : payload.trim();
        String prompt = """
                Выполни safety-оценку для следующей ситуации: %s
                1) Опиши потенциальные риски.
                2) Дай рекомендации как безопасно продолжить.
                3) Если нужны ограничения, перечисли их.
                Ответ должен быть по-русски и лаконичным.
                """.formatted(topic);
        return new TagPrompt("safe", prompt.trim(), Map.of("payloadLen", topic.length()));
    }

    /**
     * Builds a web-search-based TagPrompt that instructs the model to answer using only the provided citations.
     *
     * The prompt contains a numbered list of citations (title, snippet, URL) and the user's question.
     *
     * @param payload   the user's query; if null or empty the query is treated as "неизвестный запрос"
     * @param citations a list of citation maps expected to contain keys "title", "snippet", and "url"; each map becomes a numbered citation in the prompt
     * @return          a TagPrompt with type "web", the assembled prompt text, and a debug map containing `payloadLen` (length of the query) and `citationsProvided` (number of citations)
     */
    private TagPrompt webPrompt(String payload, List<Map<String, Object>> citations) {
        String query = (payload == null || payload.trim().isEmpty())
                ? "неизвестный запрос"
                : payload.trim();
        StringBuilder prompt = new StringBuilder();
        prompt.append("Ниже приведены результаты веб-поиска. Используй только их для ответа и добавь цитаты вида [1], [2].\n");
        for (int i = 0; i < citations.size(); i++) {
            Map<String, Object> c = citations.get(i);
            prompt.append("[").append(i + 1).append("] ")
                    .append(c.getOrDefault("title", "Без названия"))
                    .append(" — ")
                    .append(c.getOrDefault("snippet", ""))
                    .append(" (")
                    .append(c.getOrDefault("url", ""))
                    .append(")\n");
        }
        prompt.append("Вопрос пользователя: ").append(query);
        Map<String, Object> debug = Map.of(
                "payloadLen", query.length(),
                "citationsProvided", citations.size()
        );
        return new TagPrompt("web", prompt.toString().trim(), debug);
    }

    /**
     * Formats a list of history entries into a readable transcript.
     *
     * @param history the history entries in chronological order to format
     * @return a string where each entry is on its own line as "`direction: text`", trimmed;
     *         returns "не найдено сообщений" if the list is empty
     */
    private String formatHistory(List<HistoryEntry> history) {
        if (history.isEmpty()) {
            return "не найдено сообщений";
        }
        StringBuilder sb = new StringBuilder();
        for (HistoryEntry entry : history) {
            sb.append(entry.direction())
                    .append(": ")
                    .append(entry.text())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    public record HistoryEntry(String direction, String text, String createdAt) {}

    public record TagPrompt(String type, String prompt, Map<String, Object> debug) {
        /**
         * Estimate the approximate number of tokens in the prompt.
         *
         * @return the estimated token count computed as (prompt length / 4) + 1
         */
        public int tokenEstimate() {
            return prompt.length() / 4 + 1;
        }
    }
}