package com.matteo.chat_backend.service;

import com.matteo.chat_backend.dto.*;
import com.matteo.chat_backend.entity.*;
import com.matteo.chat_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServerService {

    private final ServerRepository serverRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;
    private final PresenceService presenceService;

    public ServerResponse createServer(CreateServerRequest request, String email) {
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Server server = new Server();
        server.setName(request.getName());
        server.setDescription(request.getDescription());
        server.setIconUrl(request.getIconUrl());
        server.setOwner(owner);
        server.setCreatedAt(LocalDateTime.now());
        serverRepository.save(server);

        ServerMember member = new ServerMember();
        member.setServer(server);
        member.setUser(owner);
        member.setRole("admin");
        member.setJoinedAt(LocalDateTime.now());
        serverMemberRepository.save(member);

        Channel general = new Channel();
        general.setServer(server);
        general.setName("general");
        general.setType("text");
        general.setCreatedAt(LocalDateTime.now());
        channelRepository.save(general);

        return toResponse(server);
    }

    public ServerResponse joinServer(UUID serverId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));

        if (serverMemberRepository.existsByServerAndUser(server, user)) {
            throw new RuntimeException("Ya eres miembro de este servidor");
        }

        ServerMember member = new ServerMember();
        member.setServer(server);
        member.setUser(user);
        member.setRole("member");
        member.setJoinedAt(LocalDateTime.now());
        serverMemberRepository.save(member);

        return toResponse(server);
    }

    public List<ServerResponse> getMyServers(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return serverRepository.findServersByMember(user)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public ServerResponse getServer(UUID serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));
        return toResponse(server);
    }

    public ServerResponse updateServer(UUID serverId, UpdateServerRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));

        if (!server.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Solo el dueno puede editar este servidor");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            server.setName(request.getName());
        }
        if (request.getDescription() != null) {
            server.setDescription(request.getDescription());
        }
        if (request.getIconUrl() != null) {
            server.setIconUrl(request.getIconUrl());
        }

        serverRepository.save(server);
        return toResponse(server);
    }

    public ChannelResponse createChannel(UUID serverId, CreateChannelRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));

        if (!server.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Solo el dueño puede crear canales");
        }

        Channel channel = new Channel();
        channel.setServer(server);
        channel.setName(request.getName());
        channel.setType(request.getType());
        channel.setCreatedAt(LocalDateTime.now());
        channelRepository.save(channel);

        return toChannelResponse(channel);
    }

    public List<ChannelResponse> getServerChannels(UUID serverId) {
        return channelRepository.findByServerId(serverId)
                .stream().map(this::toChannelResponse).collect(Collectors.toList());
    }

    public List<ServerMemberResponse> getServerMembers(UUID serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));
        return serverMemberRepository.findByServer(server)
                .stream().map(this::toMemberResponse).collect(Collectors.toList());
    }

    private ServerResponse toResponse(Server server) {
        return new ServerResponse(
                server.getId(),
                server.getName(),
                server.getDescription(),
                server.getIconUrl(),
                server.getOwner().getUsername(),
                server.getCreatedAt()
        );
    }

    private ChannelResponse toChannelResponse(Channel channel) {
        return new ChannelResponse(
                channel.getId(),
                channel.getName(),
                channel.getType(),
                channel.getServer().getId(),
                channel.getCreatedAt()
        );
    }

    private ServerMemberResponse toMemberResponse(ServerMember member) {
        return new ServerMemberResponse(
                member.getUser().getId(),
                member.getUser().getUsername(),
                member.getUser().getAvatarUrl(),
                member.getRole(),
                presenceService.isOnline(member.getUser()),
                member.getJoinedAt()
        );
    }
}
