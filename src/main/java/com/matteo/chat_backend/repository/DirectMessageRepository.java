package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.DirectMessage;
import com.matteo.chat_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {
    Optional<DirectMessage> findByUser1AndUser2(User user1, User user2);
    Optional<DirectMessage> findByUser2AndUser1(User user1, User user2);
    List<DirectMessage> findByUser1(User user1);
    List<DirectMessage> findByUser2(User user2);
}