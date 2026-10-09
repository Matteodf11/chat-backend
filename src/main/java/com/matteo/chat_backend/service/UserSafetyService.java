package com.matteo.chat_backend.service;

import com.matteo.chat_backend.entity.*;
import com.matteo.chat_backend.repository.*;
import com.matteo.chat_backend.dto.BlockedUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class UserSafetyService {
    private final UserRepository users;
    private final UserBlockRepository blocks;
    private final FriendshipRepository friendships;
    private final org.springframework.beans.factory.ObjectProvider<SimpMessagingTemplate> events;

    public boolean canMessage(UUID one, UUID two) { return !blocks.existsBetween(one, two); }
    public void requireAllowed(UUID one, UUID two) {
        if (!canMessage(one, two)) throw new AccessDeniedException("No se puede contactar con este usuario");
    }
    public void lockPair(UUID one, UUID two) {
        java.util.stream.Stream.of(one, two).distinct().sorted().forEach(id -> users.lockActiveById(id).orElseThrow());
    }
    private User caller(String email) { return users.findByEmail(email).orElseThrow(() -> new AccessDeniedException("Usuario no autorizado")); }
    private User other(UUID id) { return users.findActiveById(id).orElseThrow(() -> new IllegalArgumentException("Usuario no disponible")); }

    public List<BlockedUserResponse> blockedUsers(String email) {
        return blocks.findByBlockerIdOrderByCreatedAtDesc(caller(email).getId()).stream().map(b ->
            new BlockedUserResponse(b.getBlocked().getId(), b.getBlocked().getUsername(), b.getBlocked().getAvatarUrl())).toList();
    }
    @Transactional
    public void block(UUID id, String email) {
        User actor = caller(email), target = other(id);
        if (actor.getId().equals(id)) throw new IllegalArgumentException("No puedes bloquearte a ti mismo");
        lockPair(actor.getId(), id);
        if (!blocks.existsByBlockerIdAndBlockedId(actor.getId(), id)) {
            var block = new UserBlock(); block.setBlocker(actor); block.setBlocked(target); block.setCreatedAt(LocalDateTime.now());
            blocks.save(block);
        }
        removeRelationship(actor, target);
        changed(actor, target);
    }
    @Transactional
    public void unblock(UUID id, String email) {
        User actor = caller(email), target = other(id);
        lockPair(actor.getId(), id);
        blocks.deleteByBlockerIdAndBlockedId(actor.getId(), id);
        changed(actor, target);
    }
    @Transactional
    public void removeFriend(UUID id, String email) {
        User actor = caller(email), target = other(id);
        lockPair(actor.getId(), id);
        removeRelationship(actor, target);
        changed(actor, target);
    }
    private void removeRelationship(User actor, User target) {
        friendships.findBetweenUsers(actor, target).ifPresent(f -> { f.setDeletedAt(LocalDateTime.now()); friendships.save(f); });
    }
    private void changed(User actor, User target) {
        Runnable publish = () -> {
            events.getObject().convertAndSendToUser(actor.getEmail(), "/queue/contacts", Map.of("type", "contacts_changed"));
            events.getObject().convertAndSendToUser(target.getEmail(), "/queue/contacts", Map.of("type", "contacts_changed"));
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { publish.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { publish.run(); }
        });
    }
}
