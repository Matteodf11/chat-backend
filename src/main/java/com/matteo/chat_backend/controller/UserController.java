package com.matteo.chat_backend.controller;

import com.matteo.chat_backend.dto.UpdateProfileRequest;
import com.matteo.chat_backend.dto.UserProfileResponse;
import com.matteo.chat_backend.service.AvatarStorageService;
import com.matteo.chat_backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(Principal principal) {
        return ResponseEntity.ok(userService.getMyProfile(principal.getName()));
    }

    @PostMapping("/me/offline")
    public ResponseEntity<Void> markOffline(Principal principal) {
        userService.markOffline(principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Principal principal) {
        return ResponseEntity.ok(userService.updateProfile(request, principal.getName()));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProfileResponse> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        return ResponseEntity.ok(userService.uploadAvatar(file, principal.getName()));
    }

    @PostMapping(
            value = "/me/avatar",
            consumes = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp"}
    )
    public ResponseEntity<UserProfileResponse> uploadAvatarBytes(
            @RequestBody byte[] bytes,
            @RequestHeader("Content-Type") String contentType,
            Principal principal) {
        return ResponseEntity.ok(userService.uploadAvatar(bytes, contentType, principal.getName()));
    }

    @GetMapping("/{userId}/avatar/{fileName}")
    public ResponseEntity<byte[]> getAvatar(
            @PathVariable UUID userId,
            @PathVariable String fileName) {
        AvatarStorageService.AvatarFile avatar = userService.getAvatar(userId, fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(avatar.bytes());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getUserById(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<UserProfileResponse> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUserByUsername(username));
    }
}
