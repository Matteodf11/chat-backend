package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.Server;
import com.matteo.chat_backend.entity.ServerInvitation;
import com.matteo.chat_backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServerInvitationRepository extends JpaRepository<ServerInvitation, UUID> {
    @Query("SELECT si FROM ServerInvitation si WHERE si.invitee = :invitee AND si.status = :status AND si.deletedAt IS NULL ORDER BY si.createdAt DESC")
    Page<ServerInvitation> findByInviteeAndStatusOrderByCreatedAtDesc(User invitee, String status, Pageable pageable);

    @Query("SELECT si FROM ServerInvitation si WHERE si.invitee = :invitee AND si.status = :status AND si.deletedAt IS NULL ORDER BY si.createdAt DESC")
    List<ServerInvitation> findByInviteeAndStatusOrderByCreatedAtDesc(User invitee, String status);

    @Query("SELECT si FROM ServerInvitation si WHERE si.server = :server AND si.invitee = :invitee AND si.status = :status AND si.deletedAt IS NULL")
    Optional<ServerInvitation> findByServerAndInviteeAndStatus(Server server, User invitee, String status);

    @Query("SELECT si FROM ServerInvitation si WHERE si.id = :id AND si.invitee = :invitee AND si.deletedAt IS NULL")
    Optional<ServerInvitation> findByIdAndInvitee(UUID id, User invitee);

    @Modifying
    @Query("UPDATE ServerInvitation si SET si.deletedAt = CURRENT_TIMESTAMP WHERE si.server = :server")
    void softDeleteByServer(Server server);

    @Modifying
    @Query("UPDATE ServerInvitation si SET si.deletedAt = CURRENT_TIMESTAMP WHERE si.id = :id")
    void softDeleteById(UUID id);

    @Query("SELECT si FROM ServerInvitation si WHERE si.id = :id AND si.deletedAt IS NULL")
    Optional<ServerInvitation> findActiveById(UUID id);
}
