package com.matteo.chat_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "channel_reads")
@IdClass(ChannelRead.Key.class)
@Data
public class ChannelRead {
    @Id @Column(name = "user_id") private UUID userId;
    @Id @Column(name = "channel_id") private UUID channelId;
    @Column(name = "read_at", nullable = false) private LocalDateTime readAt;

    @Data
    public static class Key implements Serializable {
        private UUID userId;
        private UUID channelId;
    }
}
