package com.matteo.chat_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "direct_messages")
@Data
public class DirectMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "user1_last_read_at", nullable = false)
    private LocalDateTime user1LastReadAt;

    @Column(name = "user2_last_read_at", nullable = false)
    private LocalDateTime user2LastReadAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
