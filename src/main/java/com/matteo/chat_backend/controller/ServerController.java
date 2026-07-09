package com.matteo.chat_backend.controller;

import com.matteo.chat_backend.dto.*;
import com.matteo.chat_backend.service.ServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

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

    @PostMapping("/{serverId}/join")
    public ResponseEntity<ServerResponse> joinServer(
            @PathVariable UUID serverId,
            Principal principal) {
        return ResponseEntity.ok(serverService.joinServer(serverId, principal.getName()));
    }

    @GetMapping("/me")
    public ResponseEntity<List<ServerResponse>> getMyServers(Principal principal) {
        return ResponseEntity.ok(serverService.getMyServers(principal.getName()));
    }

    @GetMapping("/{serverId}")
    public ResponseEntity<ServerResponse> getServer(@PathVariable UUID serverId) {
        return ResponseEntity.ok(serverService.getServer(serverId));
    }

    @PutMapping("/{serverId}")
    public ResponseEntity<ServerResponse> updateServer(
            @PathVariable UUID serverId,
            @Valid @RequestBody UpdateServerRequest request,
            Principal principal) {
        return ResponseEntity.ok(serverService.updateServer(serverId, request, principal.getName()));
    }

    @PostMapping("/{serverId}/channels")
    public ResponseEntity<ChannelResponse> createChannel(
            @PathVariable UUID serverId,
            @Valid @RequestBody CreateChannelRequest request,
            Principal principal) {
        return ResponseEntity.ok(serverService.createChannel(serverId, request, principal.getName()));
    }

    @GetMapping("/{serverId}/channels")
    public ResponseEntity<List<ChannelResponse>> getChannels(@PathVariable UUID serverId) {
        return ResponseEntity.ok(serverService.getServerChannels(serverId));
    }

    @GetMapping("/{serverId}/members")
    public ResponseEntity<List<ServerMemberResponse>> getMembers(@PathVariable UUID serverId) {
        return ResponseEntity.ok(serverService.getServerMembers(serverId));
    }
}
