package com.matteo.chat_backend.controller;

import com.matteo.chat_backend.dto.UpdateProfileRequest;
import com.matteo.chat_backend.dto.UserProfileResponse;
import com.matteo.chat_backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
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

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getUserById(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<UserProfileResponse> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUserByUsername(username));
    }
}
