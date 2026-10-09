package com.matteo.chat_backend.service;

import com.matteo.chat_backend.exception.ForbiddenException;

import com.matteo.chat_backend.exception.ValidationException;

import com.matteo.chat_backend.exception.ResourceNotFoundException;

import com.matteo.chat_backend.dto.DmResponse;
import com.matteo.chat_backend.dto.FriendshipEvent;
import com.matteo.chat_backend.dto.FriendshipResponse;
import com.matteo.chat_backend.dto.PageResponse;
import com.matteo.chat_backend.entity.DirectMessage;
import com.matteo.chat_backend.entity.Friendship;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.DirectMessageRepository;
import com.matteo.chat_backend.repository.FriendshipRepository;
import com.matteo.chat_backend.repository.MessageRepository;
import com.matteo.chat_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final DirectMessageRepository directMessageRepository;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;
    private final MessageCryptoService cryptoService;

    public FriendshipResponse sendRequest(UUID receiverId, String requesterEmail) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario no encontrado"));
        User receiver = userRepository.findActiveById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario destino no encontrado"));

        if (requester.getId().equals(receiverId)) {
            throw new ValidationException("No puedes enviarte una solicitud a ti mismo");
        }

        friendshipRepository.findBetweenUsers(requester, receiver).ifPresent(f -> {
            throw new ValidationException("Ya existe una relación con este usuario");
        });

        Friendship friendship = new Friendship();
        friendship.setRequester(requester);
        friendship.setReceiver(receiver);
        friendship.setStatus("pending");
        friendship.setCreatedAt(LocalDateTime.now());
        friendshipRepository.save(friendship);

        publishFriendshipEvent("friend_request_created", friendship, requester, receiver);
        return toResponse(friendship, requester);
    }

    public FriendshipResponse acceptRequest(UUID friendshipId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario no encontrado"));
        Friendship friendship = friendshipRepository.findActiveById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Solicitud no encontrada"));

        if (!friendship.getReceiver().getId().equals(user.getId())) {
            throw new ForbiddenException("No tienes permiso para aceptar esta solicitud");
        }

        friendship.setStatus("accepted");
        friendshipRepository.save(friendship);

        publishFriendshipEvent("friend_request_accepted", friendship, friendship.getRequester(), friendship.getReceiver());
        return toResponse(friendship, user);
    }

    public FriendshipResponse rejectRequest(UUID friendshipId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario no encontrado"));
        Friendship friendship = friendshipRepository.findActiveById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Solicitud no encontrada"));

        if (!friendship.getReceiver().getId().equals(user.getId())) {
            throw new ForbiddenException("No tienes permiso para rechazar esta solicitud");
        }

        friendship.setStatus("rejected");
        friendshipRepository.save(friendship);

        publishFriendshipEvent("friend_request_rejected", friendship, friendship.getRequester(), friendship.getReceiver());
        return toResponse(friendship, user);
    }

    public PageResponse<FriendshipResponse> getFriends(String email, org.springframework.data.domain.Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario no encontrado"));
        return PageResponse.of(friendshipRepository.findAllByUserAndStatus(user, "accepted", pageable)
                .map(f -> toResponse(f, user)));
    }

    public PageResponse<FriendshipResponse> getPendingRequests(String email, org.springframework.data.domain.Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario no encontrado"));
        return PageResponse.of(friendshipRepository.findAllByUserAndStatus(user, "pending", pageable)
                .map(f -> toResponse(f, user)));
    }

    public DmResponse getOrCreateDm(UUID otherUserId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario no encontrado"));
        User other = userRepository.findActiveById(otherUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario no encontrado"));

        DirectMessage dm = directMessageRepository.findByUser1AndUser2(user, other)
                .or(() -> directMessageRepository.findByUser2AndUser1(user, other))
                .orElseGet(() -> {
                    DirectMessage newDm = new DirectMessage();
                    newDm.setUser1(user);
                    newDm.setUser2(other);
                    LocalDateTime now = LocalDateTime.now();
                    newDm.setCreatedAt(now);
                    newDm.setLastActivityAt(now);
                    newDm.setUser1LastReadAt(now);
                    newDm.setUser2LastReadAt(now);
                    return directMessageRepository.save(newDm);
                });

        return toDmResponse(dm, user);
    }

    public PageResponse<DmResponse> getMyDms(String email, org.springframework.data.domain.Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario no encontrado"));

        var orderedPage = pageable.isPaged() ? org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
                : org.springframework.data.domain.Pageable.unpaged();
        return PageResponse.of(directMessageRepository.findAllByUser(user, orderedPage)
                .map(dm -> toDmResponse(dm, user)));
    }

    @Transactional
    public void markDmRead(UUID dmId, String email) {
        markDmRead(dmId, email, null);
    }

    @Transactional
    public void markDmRead(UUID dmId, String email, UUID upToMessageId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario no encontrado"));
        DirectMessage dm = directMessageRepository.lockActiveById(dmId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Conversacion no encontrada"));
        LocalDateTime now = LocalDateTime.now();
        if (upToMessageId != null) {
            var message = messageRepository.findActiveById(upToMessageId)
                    .orElseThrow(() -> new ValidationException("Mensaje no disponible"));
            if (message.getDirectMessage() == null || !message.getDirectMessage().getId().equals(dmId)) {
                throw new ValidationException("El mensaje no pertenece a esta conversación");
            }
            now = message.getCreatedAt();
        }
        if (dm.getUser1().getId().equals(user.getId())) {
            if (dm.getUser1LastReadAt() != null && !now.isAfter(dm.getUser1LastReadAt())) return;
            dm.setUser1LastReadAt(now);
        } else if (dm.getUser2().getId().equals(user.getId())) {
            if (dm.getUser2LastReadAt() != null && !now.isAfter(dm.getUser2LastReadAt())) return;
            dm.setUser2LastReadAt(now);
        } else {
            throw new org.springframework.security.access.AccessDeniedException("No tienes acceso a esta conversacion");
        }
        directMessageRepository.save(dm);
        var event = new com.matteo.chat_backend.dto.DmReadEvent("dm_read", dmId, user.getId(), now);
        afterCommit(() -> publishDmEvent(dm, "/queue/read-receipts", event));
    }

    @Transactional
    public DmResponse updateDmPreferences(UUID dmId, String email, com.matteo.chat_backend.dto.DmPreferencesRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ForbiddenException("Usuario no autorizado"));
        var dm = directMessageRepository.lockActiveById(dmId).orElseThrow(() -> new ResourceNotFoundException("Conversación", dmId.toString()));
        if (dm.getUser1().getId().equals(user.getId())) {
            if (request.pinned() != null) dm.setUser1Pinned(request.pinned());
            if (request.muted() != null) dm.setUser1Muted(request.muted());
            if (request.archived() != null) dm.setUser1Archived(request.archived());
        } else if (dm.getUser2().getId().equals(user.getId())) {
            if (request.pinned() != null) dm.setUser2Pinned(request.pinned());
            if (request.muted() != null) dm.setUser2Muted(request.muted());
            if (request.archived() != null) dm.setUser2Archived(request.archived());
        } else throw new ForbiddenException("No tienes acceso a esta conversación");
        directMessageRepository.save(dm);
        var response = toDmResponse(dm, user);
        afterCommit(() -> messagingTemplate.convertAndSendToUser(email, "/queue/dm-settings", response));
        return response;
    }

    private void publishDmEvent(DirectMessage dm, String queue, Object event) {
        messagingTemplate.convertAndSend("/topic/dm." + dm.getId(), event);
        messagingTemplate.convertAndSendToUser(dm.getUser1().getEmail(), queue, event);
        messagingTemplate.convertAndSendToUser(dm.getUser2().getEmail(), queue, event);
    }

    private void afterCommit(Runnable action) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { action.run(); }
                });
    }

    private FriendshipResponse toResponse(Friendship f, User currentUser) {
        User friend = f.getRequester().getId().equals(currentUser.getId())
                ? f.getReceiver() : f.getRequester();
        boolean isIncoming = f.getReceiver().getId().equals(currentUser.getId());
        return new FriendshipResponse(
                f.getId(),
                friend.getId(),
                friend.getUsername(),
                friend.getAvatarUrl(),
                presenceService.isOnline(friend),
                f.getStatus(),
                isIncoming,
                f.getCreatedAt()
        );
    }

    private DmResponse toDmResponse(DirectMessage dm, User currentUser) {
        User other = dm.getUser1().getId().equals(currentUser.getId())
                ? dm.getUser2() : dm.getUser1();
        LocalDateTime lastReadAt = dm.getUser1().getId().equals(currentUser.getId())
                ? dm.getUser1LastReadAt() : dm.getUser2LastReadAt();
        if (lastReadAt == null) lastReadAt = dm.getCreatedAt();
        long unreadCount = messageRepository.countUnreadDmMessages(
                dm.getId(), currentUser.getId(), lastReadAt
        );
        var latest = messageRepository.findByDirectMessageIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                dm.getId(), LocalDateTime.of(9999, 12, 31, 23, 59), org.springframework.data.domain.PageRequest.of(0, 1));
        var last = latest.isEmpty() ? null : latest.getFirst();
        String preview = last == null ? null : cryptoService.decrypt(last.getContent());
        if (last != null && preview.isBlank()) preview = last.getAttachmentContentType() != null &&
                last.getAttachmentContentType().startsWith("audio/") ? "Nota de voz" : last.getAttachmentName();
        if (preview != null && preview.length() > 180) preview = preview.substring(0, 180);
        LocalDateTime activity = dm.getLastActivityAt() == null ? dm.getCreatedAt() : dm.getLastActivityAt();
        if (last != null && (activity == null || last.getCreatedAt().isAfter(activity))) activity = last.getCreatedAt();
        return new DmResponse(
                dm.getId(),
                other.getId(),
                other.getUsername(),
                other.getAvatarUrl(),
                presenceService.isOnline(other),
                unreadCount > 0,
                dm.getCreatedAt(),
                unreadCount,
                preview,
                last == null ? null : last.getCreatedAt(),
                activity,
                dm.getUser1().getId().equals(currentUser.getId()) ? dm.isUser1Pinned() : dm.isUser2Pinned(),
                dm.getUser1().getId().equals(currentUser.getId()) ? dm.isUser1Muted() : dm.isUser2Muted(),
                dm.getUser1().getId().equals(currentUser.getId()) ? dm.isUser1Archived() : dm.isUser2Archived()
        );
    }

    private void publishFriendshipEvent(String type, Friendship friendship, User requester, User receiver) {
        FriendshipEvent requesterEvent = new FriendshipEvent(type, toResponse(friendship, requester));
        FriendshipEvent receiverEvent = new FriendshipEvent(type, toResponse(friendship, receiver));

        messagingTemplate.convertAndSendToUser(requester.getEmail(), "/queue/friends", requesterEvent);
        messagingTemplate.convertAndSendToUser(receiver.getEmail(), "/queue/friends", receiverEvent);
        messagingTemplate.convertAndSend("/topic/friends." + requester.getId(), requesterEvent);
        messagingTemplate.convertAndSend("/topic/friends." + receiver.getId(), receiverEvent);
    }
}
