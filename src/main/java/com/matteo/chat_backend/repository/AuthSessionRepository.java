package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.AuthSession;
import com.matteo.chat_backend.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSession> findByTokenHash(String tokenHash);
    List<AuthSession> findByUserAndRevokedAtIsNullOrderByLastUsedAtDesc(User user);
}
