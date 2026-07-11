package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.Server;
import com.matteo.chat_backend.entity.ServerInvitation;
import com.matteo.chat_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServerInvitationRepository extends JpaRepository<ServerInvitation, UUID> {
    List<ServerInvitation> findByInviteeAndStatusOrderByCreatedAtDesc(User invitee, String status);
    Optional<ServerInvitation> findByServerAndInviteeAndStatus(Server server, User invitee, String status);
    Optional<ServerInvitation> findByIdAndInvitee(UUID id, User invitee);
    void deleteByServer(Server server);
}
