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
                    newDm.setUser1LastReadAt(now);
                    newDm.setUser2LastReadAt(now);
                    return directMessageRepository.save(newDm);
                });

        return toDmResponse(dm, user);
    }

    public PageResponse<DmResponse> getMyDms(String email, org.springframework.data.domain.Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario no encontrado"));

        return PageResponse.of(directMessageRepository.findAllByUser(user, pageable)
                .map(dm -> toDmResponse(dm, user)));
    }

    @Transactional
    public void markDmRead(UUID dmId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario no encontrado"));
        DirectMessage dm = directMessageRepository.findActiveById(dmId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Conversacion no encontrada"));
        LocalDateTime now = LocalDateTime.now();
        if (dm.getUser1().getId().equals(user.getId())) {
            dm.setUser1LastReadAt(now);
        } else if (dm.getUser2().getId().equals(user.getId())) {
            dm.setUser2LastReadAt(now);
        } else {
            throw new org.springframework.security.access.AccessDeniedException("No tienes acceso a esta conversacion");
        }
        directMessageRepository.save(dm);
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
        boolean hasUnread = messageRepository.countUnreadDmMessages(
                dm.getId(), currentUser.getId(), lastReadAt
        ) > 0;
        return new DmResponse(
                dm.getId(),
                other.getId(),
                other.getUsername(),
                other.getAvatarUrl(),
                presenceService.isOnline(other),
                hasUnread,
                dm.getCreatedAt()
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
