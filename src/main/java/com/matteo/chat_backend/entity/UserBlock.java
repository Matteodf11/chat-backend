package com.matteo.chat_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_blocks", uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}))
@Data
public class UserBlock {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne @JoinColumn(name = "blocker_id", nullable = false) private User blocker;
    @ManyToOne @JoinColumn(name = "blocked_id", nullable = false) private User blocked;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
