package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.Server;
import com.matteo.chat_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface ServerRepository extends JpaRepository<Server, UUID> {
    List<Server> findByOwner(User owner);

    @Query("SELECT sm.server FROM ServerMember sm WHERE sm.user = :user")
    List<Server> findServersByMember(@Param("user") User user);
}