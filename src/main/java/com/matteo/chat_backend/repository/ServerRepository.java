package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.Server;
import com.matteo.chat_backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServerRepository extends JpaRepository<Server, UUID> {
    @Query("SELECT s FROM Server s WHERE s.owner = :owner AND s.deletedAt IS NULL")
    Page<Server> findByOwner(User owner, Pageable pageable);

    @Query("SELECT s FROM Server s WHERE s.deletedAt IS NULL AND EXISTS (SELECT sm.id FROM ServerMember sm WHERE sm.server = s AND sm.user = :user AND sm.deletedAt IS NULL)")
    Page<Server> findServersByMember(@Param("user") User user, Pageable pageable);

    @Query("SELECT s FROM Server s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<Server> findActiveById(@Param("id") UUID id);
}
