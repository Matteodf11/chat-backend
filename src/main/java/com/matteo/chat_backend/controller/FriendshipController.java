package com.matteo.chat_backend.controller;

import com.matteo.chat_backend.dto.DmResponse;
import com.matteo.chat_backend.dto.FriendshipResponse;
import com.matteo.chat_backend.service.FriendshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
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
    public ResponseEntity<List<FriendshipResponse>> getFriends(Principal principal) {
        return ResponseEntity.ok(friendshipService.getFriends(principal.getName()));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<FriendshipResponse>> getPending(Principal principal) {
        return ResponseEntity.ok(friendshipService.getPendingRequests(principal.getName()));
    }

    @PostMapping("/dm/{otherUserId}")
    public ResponseEntity<DmResponse> getOrCreateDm(
            @PathVariable UUID otherUserId,
            Principal principal) {
        return ResponseEntity.ok(friendshipService.getOrCreateDm(otherUserId, principal.getName()));
    }

    @GetMapping("/dm")
    public ResponseEntity<List<DmResponse>> getMyDms(Principal principal) {
        return ResponseEntity.ok(friendshipService.getMyDms(principal.getName()));
    }

    @PutMapping("/dm/{dmId}/read")
    public ResponseEntity<Void> markDmRead(
            @PathVariable UUID dmId,
            Principal principal) {
        friendshipService.markDmRead(dmId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
