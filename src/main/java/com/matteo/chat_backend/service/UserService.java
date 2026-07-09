package com.matteo.chat_backend.service;

import com.matteo.chat_backend.dto.ProfileEvent;
import com.matteo.chat_backend.dto.UpdateProfileRequest;
import com.matteo.chat_backend.dto.UserProfileResponse;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final AvatarStorageService avatarStorageService;
    private final SimpMessagingTemplate messagingTemplate;

    public UserProfileResponse getMyProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return toResponse(user);
    }

    public UserProfileResponse getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return toResponse(user);
    }

    public UserProfileResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return toResponse(user);
    }

    public UserProfileResponse updateProfile(UpdateProfileRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            if (!request.getUsername().equals(user.getUsername()) &&
                    userRepository.findByUsername(request.getUsername()).isPresent()) {
                throw new RuntimeException("El username ya está en uso");
            }
            user.setUsername(request.getUsername());
        }

        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        User saved = userRepository.save(user);
        publishProfileEvent(saved);
        return toResponse(saved);
    }

    public UserProfileResponse uploadAvatar(MultipartFile file, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        String avatarUrl = avatarStorageService.uploadAvatar(user, file);
        user.setAvatarUrl(avatarUrl);
        User saved = userRepository.save(user);
        publishProfileEvent(saved);
        return toResponse(saved);
    }

    public UserProfileResponse uploadAvatar(byte[] bytes, String contentType, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        String avatarUrl = avatarStorageService.uploadAvatar(user, bytes, contentType);
        user.setAvatarUrl(avatarUrl);
        User saved = userRepository.save(user);
        publishProfileEvent(saved);
        return toResponse(saved);
    }

    public AvatarStorageService.AvatarFile getAvatar(UUID userId, String fileName) {
        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("Usuario no encontrado");
        }
        return avatarStorageService.downloadAvatar(userId, fileName);
    }

    public void markOffline(String email) {
        presenceService.disconnectAll(email);
    }

    public void markOnline(String email) {
        presenceService.markOnline(email);
    }

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getStatus(),
                presenceService.isOnline(user),
                user.getLastSeen(),
                user.getCreatedAt()
        );
    }

    private void publishProfileEvent(User user) {
        messagingTemplate.convertAndSend(
                "/topic/profile",
                new ProfileEvent(user.getId(), user.getUsername(), user.getAvatarUrl(), user.getStatus())
        );
    }
}
