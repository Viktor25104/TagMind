package dev.tagmind.orchestrator.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessageEntity, UUID> {
    /**
 * Retrieve a paginated list of messages for the given conversation session.
 *
 * @param session the conversation session whose messages should be returned
 * @param pageable pagination and sorting information to apply to the result set
 * @return a list of ConversationMessageEntity objects for the specified session constrained by the provided pagination
 */
List<ConversationMessageEntity> findBySession(ConversationSessionEntity session, Pageable pageable);
}