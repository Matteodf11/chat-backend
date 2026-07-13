package com.matteo.chat_backend.service;

import com.matteo.chat_backend.dto.ChannelResponse;
import com.matteo.chat_backend.dto.CreateChannelRequest;
import com.matteo.chat_backend.dto.CreateServerRequest;
import com.matteo.chat_backend.dto.InviteServerMemberRequest;
import com.matteo.chat_backend.dto.ServerInvitationResponse;
import com.matteo.chat_backend.dto.ServerMemberResponse;
import com.matteo.chat_backend.dto.ServerResponse;
import com.matteo.chat_backend.dto.ServerEventResponse;
import com.matteo.chat_backend.dto.UpdateServerRequest;
import com.matteo.chat_backend.entity.Channel;
import com.matteo.chat_backend.entity.Server;
import com.matteo.chat_backend.entity.ServerInvitation;
import com.matteo.chat_backend.entity.ServerMember;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.ChannelRepository;
import com.matteo.chat_backend.repository.MessageRepository;
import com.matteo.chat_backend.repository.ServerMemberRepository;
import com.matteo.chat_backend.repository.ServerRepository;
import com.matteo.chat_backend.repository.ServerInvitationRepository;
import com.matteo.chat_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class ServerService {

    private final ServerRepository serverRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final MessageRepository messageRepository;
    private final ServerInvitationRepository serverInvitationRepository;
    private final AvatarStorageService avatarStorageService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ServerResponse createServer(CreateServerRequest request, String email) {
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Server server = new Server();
        server.setName(request.getName());
        server.setDescription(request.getDescription());
        server.setIconUrl(null);
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
        serverRepository.save(server);
        return toResponse(server, member.getRole());
    }

    @Transactional
    public ServerResponse uploadServerIcon(UUID serverId, byte[] bytes, String contentType, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));
        ServerMember member = requireManager(server, user);
        server.setIconUrl(avatarStorageService.uploadServerIcon(server, bytes, contentType));
        serverRepository.save(server);
        return toResponse(server, member.getRole());
    }

    public AvatarStorageService.AvatarFile getServerIcon(UUID serverId, String fileName) {
        return avatarStorageService.downloadServerIcon(serverId, fileName);
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
        channel.setType("text");
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
                .stream()
                .sorted(Comparator.comparingInt(this::roleOrder)
                        .thenComparing(member -> member.getUser().getUsername(), String.CASE_INSENSITIVE_ORDER))
                .map(this::toMemberResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ServerMemberResponse updateMemberRole(UUID serverId, UUID targetUserId, String role, String email) {
        User actor = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));
        requireOwner(server, actor);

        if (!"admin".equals(role) && !"member".equals(role)) {
            throw new IllegalArgumentException("Rol no valido");
        }
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("Miembro no encontrado"));
        ServerMember target = requireMember(server, targetUser);
        if (server.getOwner().getId().equals(targetUserId) || "owner".equals(target.getRole())) {
            throw new RuntimeException("No puedes cambiar el rol del dueno");
        }
        target.setRole(role);
        ServerMemberResponse response = toMemberResponse(serverMemberRepository.save(target));
        messagingTemplate.convertAndSendToUser(
                targetUser.getEmail(),
                "/queue/server-events",
                new ServerEventResponse("ROLE_UPDATED", serverId, role)
        );
        return response;
    }

    @Transactional
    public void removeMember(UUID serverId, UUID targetUserId, String email) {
        User actor = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));
        ServerMember actorMember = requireManager(server, actor);
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("Miembro no encontrado"));
        ServerMember target = requireMember(server, targetUser);

        if (actor.getId().equals(targetUserId)) {
            throw new RuntimeException("Usa la opcion de salir del servidor");
        }
        if (server.getOwner().getId().equals(targetUserId) || "owner".equals(target.getRole())) {
            throw new RuntimeException("El dueno no puede ser expulsado");
        }
        if ("admin".equals(actorMember.getRole()) && !"member".equals(target.getRole())) {
            throw new RuntimeException("Un administrador solo puede expulsar miembros");
        }
        serverMemberRepository.delete(target);
        serverMemberRepository.flush();
        messagingTemplate.convertAndSendToUser(
                targetUser.getEmail(),
                "/queue/server-events",
                new ServerEventResponse("MEMBER_REMOVED", serverId, null)
        );
    }

    @Transactional
    public ServerInvitationResponse inviteMember(UUID serverId, InviteServerMemberRequest request, String email) {
        User inviter = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));
        requireManager(server, inviter);

        User invited = findUserByUsernameOrEmail(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (invited.getId().equals(inviter.getId())) {
            throw new RuntimeException("No puedes invitarte a ti mismo");
        }
        if (serverMemberRepository.existsByServerAndUser(server, invited)) {
            throw new RuntimeException("Ese usuario ya esta en el servidor");
        }
        if (serverInvitationRepository.findByServerAndInviteeAndStatus(server, invited, "pending").isPresent()) {
            throw new RuntimeException("Ese usuario ya tiene una invitacion pendiente");
        }

        ServerInvitation invitation = new ServerInvitation();
        invitation.setServer(server);
        invitation.setInviter(inviter);
        invitation.setInvitee(invited);
        invitation.setStatus("pending");
        invitation.setCreatedAt(LocalDateTime.now());
        serverInvitationRepository.save(invitation);
        return toInvitationResponse(invitation);
    }

    public List<ServerInvitationResponse> getPendingInvitations(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return serverInvitationRepository.findByInviteeAndStatusOrderByCreatedAtDesc(user, "pending")
                .stream().map(this::toInvitationResponse).collect(Collectors.toList());
    }

    @Transactional
    public ServerResponse acceptInvitation(UUID invitationId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        ServerInvitation invitation = serverInvitationRepository.findByIdAndInvitee(invitationId, user)
                .orElseThrow(() -> new RuntimeException("Invitacion no encontrada"));
        if (!"pending".equals(invitation.getStatus())) {
            throw new RuntimeException("La invitacion ya no esta pendiente");
        }

        Server server = invitation.getServer();
        if (!serverMemberRepository.existsByServerAndUser(server, user)) {
            ServerMember member = new ServerMember();
            member.setServer(server);
            member.setUser(user);
            member.setRole("member");
            member.setJoinedAt(LocalDateTime.now());
            serverMemberRepository.save(member);
        }

        invitation.setStatus("accepted");
        invitation.setRespondedAt(LocalDateTime.now());
        serverInvitationRepository.save(invitation);
        return toResponse(server, "member");
    }

    @Transactional
    public void rejectInvitation(UUID invitationId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        ServerInvitation invitation = serverInvitationRepository.findByIdAndInvitee(invitationId, user)
                .orElseThrow(() -> new RuntimeException("Invitacion no encontrada"));
        if (!"pending".equals(invitation.getStatus())) {
            return;
        }

        invitation.setStatus("rejected");
        invitation.setRespondedAt(LocalDateTime.now());
        serverInvitationRepository.save(invitation);
    }

    @Transactional
    public void leaveServer(UUID serverId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor no encontrado"));
        ServerMember member = requireMember(server, user);
        if ("owner".equals(member.getRole()) || server.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("El dueno debe borrar el servidor antes de salir");
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

        List<String> memberEmails = serverMemberRepository.findByServer(server).stream()
                .map(member -> member.getUser().getEmail())
                .filter(memberEmail -> !memberEmail.equalsIgnoreCase(email))
                .toList();
        List<UUID> channelIds = channelRepository.findByServerId(serverId)
                .stream().map(Channel::getId).toList();
        if (!channelIds.isEmpty()) {
            messageRepository.deleteByChannelIds(channelIds);
        }
        serverInvitationRepository.deleteByServer(server);
        channelRepository.deleteByServerId(serverId);
        serverMemberRepository.deleteByServer(server);
        serverRepository.delete(server);
        memberEmails.forEach(memberEmail -> messagingTemplate.convertAndSendToUser(
                memberEmail,
                "/queue/server-events",
                new ServerEventResponse("SERVER_DELETED", serverId, null)
        ));
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

    private ServerInvitationResponse toInvitationResponse(ServerInvitation invitation) {
        return new ServerInvitationResponse(
                invitation.getId(),
                invitation.getServer().getId(),
                invitation.getServer().getName(),
                invitation.getServer().getIconUrl(),
                invitation.getInviter().getUsername(),
                invitation.getStatus(),
                invitation.getCreatedAt()
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

    private ServerMember requireOwner(Server server, User user) {
        ServerMember member = requireMember(server, user);
        if (!server.getOwner().getId().equals(user.getId()) || !"owner".equals(member.getRole())) {
            throw new RuntimeException("Solo el dueno puede gestionar los roles");
        }
        return member;
    }

    private int roleOrder(ServerMember member) {
        return switch (member.getRole()) {
            case "owner" -> 0;
            case "admin" -> 1;
            default -> 2;
        };
    }

    private String roleFor(Server server, User user) {
        return serverMemberRepository.findByServerAndUser(server, user)
                .map(ServerMember::getRole)
                .orElse("member");
    }

    private java.util.Optional<User> findUserByUsernameOrEmail(String value) {
        String cleanValue = value == null ? "" : value.trim();
        if (cleanValue.contains("@")) {
            return userRepository.findByEmail(cleanValue)
                    .or(() -> userRepository.findByUsername(cleanValue));
        }
        return userRepository.findByUsername(cleanValue)
                .or(() -> userRepository.findByEmail(cleanValue));
    }
}
