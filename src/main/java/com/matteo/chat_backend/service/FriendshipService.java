package com.matteo.chat_backend.service;

import com.matteo.chat_backend.dto.DmResponse;
import com.matteo.chat_backend.dto.FriendshipResponse;
import com.matteo.chat_backend.entity.DirectMessage;
import com.matteo.chat_backend.entity.Friendship;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.DirectMessageRepository;
import com.matteo.chat_backend.repository.FriendshipRepository;
import com.matteo.chat_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public FriendshipResponse sendRequest(UUID receiverId, String requesterEmail) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new RuntimeException("Usuario destino no encontrado"));

        if (requester.getId().equals(receiverId)) {
            throw new RuntimeException("No puedes enviarte una solicitud a ti mismo");
        }

        friendshipRepository.findBetweenUsers(requester, receiver).ifPresent(f -> {
            throw new RuntimeException("Ya existe una relación con este usuario");
        });

        Friendship friendship = new Friendship();
        friendship.setRequester(requester);
        friendship.setReceiver(receiver);
        friendship.setStatus("pending");
        friendship.setCreatedAt(LocalDateTime.now());
        friendshipRepository.save(friendship);

        return toResponse(friendship, requester);
    }

    public FriendshipResponse acceptRequest(UUID friendshipId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        if (!friendship.getReceiver().getId().equals(user.getId())) {
            throw new RuntimeException("No tienes permiso para aceptar esta solicitud");
        }

        friendship.setStatus("accepted");
        friendshipRepository.save(friendship);

        return toResponse(friendship, user);
    }

    public FriendshipResponse rejectRequest(UUID friendshipId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        if (!friendship.getReceiver().getId().equals(user.getId())) {
            throw new RuntimeException("No tienes permiso para rechazar esta solicitud");
        }

        friendship.setStatus("rejected");
        friendshipRepository.save(friendship);

        return toResponse(friendship, user);
    }

    public List<FriendshipResponse> getFriends(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return friendshipRepository.findAllByUserAndStatus(user, "accepted")
                .stream().map(f -> toResponse(f, user)).collect(Collectors.toList());
    }

    public List<FriendshipResponse> getPendingRequests(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return friendshipRepository.findAllByUserAndStatus(user, "pending")
                .stream().map(f -> toResponse(f, user)).collect(Collectors.toList());
    }

    public DmResponse getOrCreateDm(UUID otherUserId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        User other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        DirectMessage dm = directMessageRepository.findByUser1AndUser2(user, other)
                .or(() -> directMessageRepository.findByUser2AndUser1(other, user))
                .orElseGet(() -> {
                    DirectMessage newDm = new DirectMessage();
                    newDm.setUser1(user);
                    newDm.setUser2(other);
                    newDm.setCreatedAt(LocalDateTime.now());
                    return directMessageRepository.save(newDm);
                });

        return toDmResponse(dm, user);
    }

    public List<DmResponse> getMyDms(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<DirectMessage> dms1 = directMessageRepository.findByUser1(user);
        List<DirectMessage> dms2 = directMessageRepository.findByUser2(user);

        List<DmResponse> result = dms1.stream()
                .map(dm -> toDmResponse(dm, user))
                .collect(Collectors.toList());
        dms2.stream()
                .map(dm -> toDmResponse(dm, user))
                .forEach(result::add);

        return result;
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
                friend.getIsOnline(),
                f.getStatus(),
                isIncoming,
                f.getCreatedAt()
        );
    }

    private DmResponse toDmResponse(DirectMessage dm, User currentUser) {
        User other = dm.getUser1().getId().equals(currentUser.getId())
                ? dm.getUser2() : dm.getUser1();
        return new DmResponse(
                dm.getId(),
                other.getId(),
                other.getUsername(),
                other.getAvatarUrl(),
                other.getIsOnline(),
                dm.getCreatedAt()
        );
    }
}