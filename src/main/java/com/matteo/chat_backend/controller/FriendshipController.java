package com.matteo.chat_backend.controller;

import com.matteo.chat_backend.dto.DmResponse;
import com.matteo.chat_backend.dto.FriendshipResponse;
import com.matteo.chat_backend.dto.PageResponse;
import com.matteo.chat_backend.service.FriendshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    @PostMapping("/request/{receiverId}")
    public ResponseEntity<FriendshipResponse> sendRequest(
            @PathVariable UUID receiverId,
            Principal principal) {
        return ResponseEntity.ok(friendshipService.sendRequest(receiverId, principal.getName()));
    }

    @PutMapping("/accept/{friendshipId}")
    public ResponseEntity<FriendshipResponse> acceptRequest(
            @PathVariable UUID friendshipId,
            Principal principal) {
        return ResponseEntity.ok(friendshipService.acceptRequest(friendshipId, principal.getName()));
    }

    @PutMapping("/reject/{friendshipId}")
    public ResponseEntity<FriendshipResponse> rejectRequest(
            @PathVariable UUID friendshipId,
            Principal principal) {
        return ResponseEntity.ok(friendshipService.rejectRequest(friendshipId, principal.getName()));
    }

    @GetMapping
    public ResponseEntity<?> getFriends(
            Principal principal,
            @RequestParam(defaultValue = "false") boolean paged,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(friendshipService.getFriends(principal.getName(), paged ? pageable : Pageable.unpaged()).responseBody(paged));
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getPending(
            Principal principal,
            @RequestParam(defaultValue = "false") boolean paged,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(friendshipService.getPendingRequests(principal.getName(), paged ? pageable : Pageable.unpaged()).responseBody(paged));
    }

    @PostMapping("/dm/{otherUserId}")
    public ResponseEntity<DmResponse> getOrCreateDm(
            @PathVariable UUID otherUserId,
            Principal principal) {
        return ResponseEntity.ok(friendshipService.getOrCreateDm(otherUserId, principal.getName()));
    }

    @GetMapping("/dm")
    public ResponseEntity<?> getMyDms(
            Principal principal,
            @RequestParam(defaultValue = "false") boolean paged,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(friendshipService.getMyDms(principal.getName(), paged ? pageable : Pageable.unpaged()).responseBody(paged));
    }

    @PutMapping("/dm/{dmId}/read")
    public ResponseEntity<Void> markDmRead(
            @PathVariable UUID dmId,
            @RequestParam(required = false) UUID upToMessageId,
            Principal principal) {
        friendshipService.markDmRead(dmId, principal.getName(), upToMessageId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/dm/{dmId}/preferences")
    public ResponseEntity<DmResponse> updateDmPreferences(@PathVariable UUID dmId,
            @RequestBody com.matteo.chat_backend.dto.DmPreferencesRequest request, Principal principal) {
        return ResponseEntity.ok(friendshipService.updateDmPreferences(dmId, principal.getName(), request));
    }
}
