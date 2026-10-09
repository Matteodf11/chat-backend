package com.matteo.chat_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages", uniqueConstraints = @UniqueConstraint(name = "uq_messages_sender_client_id", columnNames = {"sender_id", "client_message_id"}))
@Data
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_message_id")
    private UUID clientMessageId;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @ManyToOne
    @JoinColumn(name = "dm_id")
    private DirectMessage directMessage;

    @ManyToOne
    @JoinColumn(name = "reply_to_id")
    private Message replyTo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "attachment_key", length = 500)
    private String attachmentKey;

    @Column(name = "attachment_name")
    private String attachmentName;

    @Column(name = "attachment_content_type", length = 150)
    private String attachmentContentType;

    @Column(name = "attachment_size")
    private Long attachmentSize;

    private Boolean edited = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    private void normalizeTimestamp() {
        if (createdAt != null) createdAt = createdAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
}
