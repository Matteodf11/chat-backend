package com.matteo.chat_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Data
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

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

    private Boolean edited = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
