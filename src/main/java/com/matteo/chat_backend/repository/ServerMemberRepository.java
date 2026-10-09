package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.Server;
import com.matteo.chat_backend.entity.ServerMember;
import com.matteo.chat_backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServerMemberRepository extends JpaRepository<ServerMember, UUID> {
    @Query("SELECT sm FROM ServerMember sm WHERE sm.server = :server AND sm.deletedAt IS NULL")
    Page<ServerMember> findByServer(Server server, Pageable pageable);

    @Query("SELECT sm FROM ServerMember sm WHERE sm.server = :server AND sm.deletedAt IS NULL")
    List<ServerMember> findByServer(Server server);

    @Query("SELECT sm FROM ServerMember sm WHERE sm.server = :server AND sm.user = :user AND sm.deletedAt IS NULL")
    Optional<ServerMember> findByServerAndUser(Server server, User user);

    @Query("SELECT COUNT(sm) > 0 FROM ServerMember sm WHERE sm.server = :server AND sm.user = :user AND sm.deletedAt IS NULL")
    boolean existsByServerAndUser(Server server, User user);

    @Modifying
    @Query("UPDATE ServerMember sm SET sm.deletedAt = CURRENT_TIMESTAMP WHERE sm.server = :server")
    void softDeleteByServer(Server server);

    @Modifying
    @Query("UPDATE ServerMember sm SET sm.deletedAt = CURRENT_TIMESTAMP WHERE sm.id = :id")
    void softDeleteById(UUID id);

    @Query("SELECT sm FROM ServerMember sm WHERE sm.id = :id AND sm.deletedAt IS NULL")
    Optional<ServerMember> findActiveById(UUID id);
}
