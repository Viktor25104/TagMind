package dev.tagmind.orchestrator.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConversationSessionRepository extends JpaRepository<ConversationSessionEntity, UUID> {
    Optional<ConversationSessionEntity> findByContactId(String contactId);
}
