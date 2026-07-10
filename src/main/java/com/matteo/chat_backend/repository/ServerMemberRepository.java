package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.Server;
import com.matteo.chat_backend.entity.ServerMember;
import com.matteo.chat_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServerMemberRepository extends JpaRepository<ServerMember, UUID> {
    List<ServerMember> findByServer(Server server);
    Optional<ServerMember> findByServerAndUser(Server server, User user);
    boolean existsByServerAndUser(Server server, User user);
    void deleteByServer(Server server);
}
