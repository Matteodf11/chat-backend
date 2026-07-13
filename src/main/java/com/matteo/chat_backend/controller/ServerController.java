package com.matteo.chat_backend.controller;

import com.matteo.chat_backend.dto.*;
import com.matteo.chat_backend.service.ServerService;
import com.matteo.chat_backend.service.AvatarStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;

    @PostMapping
    public ResponseEntity<ServerResponse> createServer(
            @Valid @RequestBody CreateServerRequest request,
            Principal principal) {
        return ResponseEntity.ok(serverService.createServer(request, principal.getName()));
    }

    @GetMapping("/me")
    public ResponseEntity<List<ServerResponse>> getMyServers(Principal principal) {
        return ResponseEntity.ok(serverService.getMyServers(principal.getName()));
    }

    @GetMapping("/{serverId}")
    public ResponseEntity<ServerResponse> getServer(
            @PathVariable UUID serverId,
            Principal principal) {
        return ResponseEntity.ok(serverService.getServer(serverId, principal.getName()));
    }

    @PutMapping("/{serverId}")
    public ResponseEntity<ServerResponse> updateServer(
            @PathVariable UUID serverId,
            @Valid @RequestBody UpdateServerRequest request,
            Principal principal) {
        return ResponseEntity.ok(serverService.updateServer(serverId, request, principal.getName()));
    }

    @PostMapping(
            value = "/{serverId}/icon",
            consumes = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp"}
    )
    public ResponseEntity<ServerResponse> uploadServerIcon(
            @PathVariable UUID serverId,
            @RequestBody byte[] bytes,
            @RequestHeader("Content-Type") String contentType,
            Principal principal) {
        return ResponseEntity.ok(serverService.uploadServerIcon(serverId, bytes, contentType, principal.getName()));
    }

    @GetMapping("/{serverId}/icon/{fileName}")
    public ResponseEntity<byte[]> getServerIcon(
            @PathVariable UUID serverId,
            @PathVariable String fileName) {
        AvatarStorageService.AvatarFile icon = serverService.getServerIcon(serverId, fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(icon.contentType()))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(icon.bytes());
    }

    @PostMapping("/{serverId}/channels")
    public ResponseEntity<ChannelResponse> createChannel(
            @PathVariable UUID serverId,
            @Valid @RequestBody CreateChannelRequest request,
            Principal principal) {
        return ResponseEntity.ok(serverService.createChannel(serverId, request, principal.getName()));
    }

    @GetMapping("/{serverId}/channels")
    public ResponseEntity<List<ChannelResponse>> getChannels(
            @PathVariable UUID serverId,
            Principal principal) {
        return ResponseEntity.ok(serverService.getServerChannels(serverId, principal.getName()));
    }

    @GetMapping("/{serverId}/members")
    public ResponseEntity<List<ServerMemberResponse>> getMembers(
            @PathVariable UUID serverId,
            Principal principal) {
        return ResponseEntity.ok(serverService.getServerMembers(serverId, principal.getName()));
    }

    @PutMapping("/{serverId}/members/{userId}/role")
    public ResponseEntity<ServerMemberResponse> updateMemberRole(
            @PathVariable UUID serverId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateServerMemberRoleRequest request,
            Principal principal) {
        return ResponseEntity.ok(serverService.updateMemberRole(serverId, userId, request.getRole(), principal.getName()));
    }

    @DeleteMapping("/{serverId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID serverId,
            @PathVariable UUID userId,
            Principal principal) {
        serverService.removeMember(serverId, userId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{serverId}/members")
    public ResponseEntity<ServerInvitationResponse> inviteMember(
            @PathVariable UUID serverId,
            @Valid @RequestBody InviteServerMemberRequest request,
            Principal principal) {
        return ResponseEntity.ok(serverService.inviteMember(serverId, request, principal.getName()));
    }

    @GetMapping("/invitations/pending")
    public ResponseEntity<List<ServerInvitationResponse>> getPendingInvitations(Principal principal) {
        return ResponseEntity.ok(serverService.getPendingInvitations(principal.getName()));
    }

    @PostMapping("/invitations/{invitationId}/accept")
    public ResponseEntity<ServerResponse> acceptInvitation(
            @PathVariable UUID invitationId,
            Principal principal) {
        return ResponseEntity.ok(serverService.acceptInvitation(invitationId, principal.getName()));
    }

    @PostMapping("/invitations/{invitationId}/reject")
    public ResponseEntity<Void> rejectInvitation(
            @PathVariable UUID invitationId,
            Principal principal) {
        serverService.rejectInvitation(invitationId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{serverId}/leave")
    public ResponseEntity<Void> leaveServer(
            @PathVariable UUID serverId,
            Principal principal) {
        serverService.leaveServer(serverId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{serverId}")
    public ResponseEntity<Void> deleteServer(
            @PathVariable UUID serverId,
            Principal principal) {
        serverService.deleteServer(serverId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
