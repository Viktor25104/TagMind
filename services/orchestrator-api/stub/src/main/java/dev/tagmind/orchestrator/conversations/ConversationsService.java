package dev.tagmind.orchestrator.conversations;

import dev.tagmind.orchestrator.persistence.ConversationMode;
import dev.tagmind.orchestrator.persistence.ConversationSessionEntity;
import dev.tagmind.orchestrator.persistence.ConversationSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationsService {

    private final ConversationSessionRepository sessions;

    public ConversationsService(ConversationSessionRepository sessions) {
        this.sessions = sessions;
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
}

