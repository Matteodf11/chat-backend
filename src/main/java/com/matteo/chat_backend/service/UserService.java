package com.matteo.chat_backend.service;

import com.matteo.chat_backend.dto.ProfileEvent;
import com.matteo.chat_backend.dto.UpdateProfileRequest;
import com.matteo.chat_backend.dto.UserProfileResponse;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.exception.ConflictException;
import com.matteo.chat_backend.exception.ResourceNotFoundException;
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
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return toResponse(user);
    }

    public UserProfileResponse getUserById(UUID userId) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        return toResponse(user);
    }

    public UserProfileResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
        return toResponse(user);
    }

    public UserProfileResponse updateProfile(UpdateProfileRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            if (!request.getUsername().equals(user.getUsername()) &&
                    userRepository.findByUsername(request.getUsername()).isPresent()) {
                throw new ConflictException("User", "username", request.getUsername());
            }
            user.setUsername(request.getUsername());
        }

        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        if (request.getProfileBannerUrl() != null) {
            user.setProfileBannerUrl(request.getProfileBannerUrl());
        }

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        if (request.getBio() != null) {
            user.setBio(request.getBio().strip());
        }

        if (request.getProfileColor() != null && !request.getProfileColor().isBlank()) {
            user.setProfileColor(request.getProfileColor());
        }

        User saved = userRepository.save(user);
        publishProfileEvent(saved);
        return toResponse(saved);
    }

    public UserProfileResponse uploadAvatar(MultipartFile file, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario no encontrado"));
        String avatarUrl = avatarStorageService.uploadAvatar(user, file);
        user.setAvatarUrl(avatarUrl);
        User saved = userRepository.save(user);
        publishProfileEvent(saved);
        return toResponse(saved);
    }

    public UserProfileResponse uploadAvatar(byte[] bytes, String contentType, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario no encontrado"));
        String avatarUrl = avatarStorageService.uploadAvatar(user, bytes, contentType);
        user.setAvatarUrl(avatarUrl);
        User saved = userRepository.save(user);
        publishProfileEvent(saved);
        return toResponse(saved);
    }

    public UserProfileResponse uploadBanner(MultipartFile file, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario no encontrado"));
        String bannerUrl = avatarStorageService.uploadBanner(user, file);
        user.setProfileBannerUrl(bannerUrl);
        User saved = userRepository.save(user);
        publishProfileEvent(saved);
        return toResponse(saved);
    }

    public UserProfileResponse uploadBanner(byte[] bytes, String contentType, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", "Usuario no encontrado"));
        String bannerUrl = avatarStorageService.uploadBanner(user, bytes, contentType);
        user.setProfileBannerUrl(bannerUrl);
        User saved = userRepository.save(user);
        publishProfileEvent(saved);
        return toResponse(saved);
    }

    public AvatarStorageService.AvatarFile getAvatar(UUID userId, String fileName) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Recurso", "Usuario no encontrado");
        }
        return avatarStorageService.downloadAvatar(userId, fileName);
    }

    public AvatarStorageService.AvatarFile getBanner(UUID userId, String fileName) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Recurso", "Usuario no encontrado");
        }
        return avatarStorageService.downloadBanner(userId, fileName);
    }

    public void markOffline(String email) {
        // Presence is scoped to WebSocket sessions. Closing one client must not
        // disconnect other windows or devices belonging to the same account.
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
                user.getProfileBannerUrl(),
                user.getStatus(),
                user.getProfileColor(),
                presenceService.isOnline(user),
                user.getLastSeen(),
                user.getCreatedAt(),
                user.getBio()
        );
    }

    private void publishProfileEvent(User user) {
        messagingTemplate.convertAndSend(
                "/topic/profile",
                new ProfileEvent(user.getId(), user.getUsername(), user.getAvatarUrl(), user.getProfileBannerUrl(), user.getStatus(), user.getProfileColor(), user.getBio())
        );
    }
}
