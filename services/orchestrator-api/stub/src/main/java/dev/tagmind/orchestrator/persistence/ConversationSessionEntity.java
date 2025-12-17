package dev.tagmind.orchestrator.persistence;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(
        name = "conversation_sessions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_conversation_sessions_contact_id", columnNames = {"contact_id"})
        }
)
public class ConversationSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "contact_id", nullable = false, updatable = false)
    private String contactId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private ConversationMode mode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (mode == null) mode = ConversationMode.SUGGEST;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public String getContactId() {
        return contactId;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }

    public ConversationMode getMode() {
        return mode;
    }

    public void setMode(ConversationMode mode) {
        this.mode = mode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
