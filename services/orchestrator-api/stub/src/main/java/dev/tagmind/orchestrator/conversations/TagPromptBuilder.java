package dev.tagmind.orchestrator.conversations;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Component
public class TagPromptBuilder {

    public TagPrompt build(ConversationsService.TagInput input, List<HistoryEntry> history) {
        return switch (input.tag()) {
            case "help" -> helpPrompt();
            case "llm" -> llmPrompt(input.payload());
            case "recap" -> recapPrompt(history, input.count());
            case "judge" -> judgePrompt(history);
            case "fix" -> fixPrompt(history, input.payload());
            case "plan" -> planPrompt(input.payload());
            case "safe" -> safePrompt(input.payload());
            default -> llmPrompt(input.payload());
        };
    }

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
        public int tokenEstimate() {
            return prompt.length() / 4 + 1;
        }
    }
}
