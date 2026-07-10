package com.matteo.chat_backend.service;

import com.matteo.chat_backend.dto.ChannelResponse;
import com.matteo.chat_backend.dto.CreateChannelRequest;
import com.matteo.chat_backend.dto.CreateServerRequest;
import com.matteo.chat_backend.dto.InviteServerMemberRequest;
import com.matteo.chat_backend.dto.ServerMemberResponse;
import com.matteo.chat_backend.dto.ServerResponse;
import com.matteo.chat_backend.dto.UpdateServerRequest;
import com.matteo.chat_backend.entity.Channel;
import com.matteo.chat_backend.entity.Server;
import com.matteo.chat_backend.entity.ServerMember;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.ChannelRepository;
import com.matteo.chat_backend.repository.MessageRepository;
import com.matteo.chat_backend.repository.ServerMemberRepository;
import com.matteo.chat_backend.repository.ServerRepository;
import com.matteo.chat_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final MessageRepository messageRepository;

    @Transactional
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
        member.setRole("owner");
        member.setJoinedAt(LocalDateTime.now());
        serverMemberRepository.save(member);

        Channel general = new Channel();
        general.setServer(server);
        general.setName("general");
        general.setType("text");
        general.setCreatedAt(LocalDateTime.now());
        channelRepository.save(general);

        return toResponse(server, "owner");
    }

    @Transactional
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

        return toResponse(server, "member");
    }

    public List<ServerResponse> getMyServers(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return serverRepository.findServersByMember(user)
                .stream()
                .map(server -> toResponse(server, roleFor(server, user)))
                .collect(Collectors.toList());
    }

    public ServerResponse getServer(UUID serverId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));
        ServerMember member = requireMember(server, user);
        return toResponse(server, member.getRole());
    }

    @Transactional
    public ServerResponse updateServer(UUID serverId, UpdateServerRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));

        ServerMember member = requireManager(server, user);

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
        return toResponse(server, member.getRole());
    }

    @Transactional
    public ChannelResponse createChannel(UUID serverId, CreateChannelRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));

        requireManager(server, user);

        Channel channel = new Channel();
        channel.setServer(server);
        channel.setName(request.getName());
        channel.setType(request.getType());
        channel.setCreatedAt(LocalDateTime.now());
        channelRepository.save(channel);

        return toChannelResponse(channel);
    }

    public List<ChannelResponse> getServerChannels(UUID serverId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));
        requireMember(server, user);
        return channelRepository.findByServerId(serverId)
                .stream().map(this::toChannelResponse).collect(Collectors.toList());
    }

    public List<ServerMemberResponse> getServerMembers(UUID serverId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));
        requireMember(server, user);
        return serverMemberRepository.findByServer(server)
                .stream().map(this::toMemberResponse).collect(Collectors.toList());
    }

    @Transactional
    public ServerMemberResponse inviteMember(UUID serverId, InviteServerMemberRequest request, String email) {
        User inviter = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));
        requireManager(server, inviter);

        User invited = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (serverMemberRepository.existsByServerAndUser(server, invited)) {
            throw new RuntimeException("Ese usuario ya esta en el servidor");
        }

        ServerMember member = new ServerMember();
        member.setServer(server);
        member.setUser(invited);
        member.setRole("member");
        member.setJoinedAt(LocalDateTime.now());
        serverMemberRepository.save(member);
        return toMemberResponse(member);
    }

    @Transactional
    public void leaveServer(UUID serverId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));
        ServerMember member = requireMember(server, user);
        if ("owner".equals(member.getRole()) || server.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("El dueno debe borrar el servidor o transferirlo antes de salir");
        }
        serverMemberRepository.delete(member);
    }

    @Transactional
    public void deleteServer(UUID serverId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));
        if (!server.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Solo el dueno puede borrar este servidor");
        }

        List<UUID> channelIds = channelRepository.findByServerId(serverId)
                .stream().map(Channel::getId).toList();
        if (!channelIds.isEmpty()) {
            messageRepository.deleteByChannelIds(channelIds);
        }
        channelRepository.deleteByServerId(serverId);
        serverMemberRepository.deleteByServer(server);
        serverRepository.delete(server);
    }

    private ServerResponse toResponse(Server server, String myRole) {
        return new ServerResponse(
                server.getId(),
                server.getName(),
                server.getDescription(),
                server.getIconUrl(),
                server.getOwner().getUsername(),
                myRole,
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

    private ServerMember requireMember(Server server, User user) {
        return serverMemberRepository.findByServerAndUser(server, user)
                .orElseThrow(() -> new RuntimeException("No eres miembro de este servidor"));
    }

    private ServerMember requireManager(Server server, User user) {
        ServerMember member = requireMember(server, user);
        if (!"owner".equals(member.getRole()) && !"admin".equals(member.getRole())) {
            throw new RuntimeException("No tienes permisos para gestionar este servidor");
        }
        return member;
    }

    private String roleFor(Server server, User user) {
        return serverMemberRepository.findByServerAndUser(server, user)
                .map(ServerMember::getRole)
                .orElse("member");
    }
}
