package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.Friendship;
import com.matteo.chat_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("SELECT f FROM Friendship f WHERE f.requester = :user OR f.receiver = :user")
    List<Friendship> findAllByUser(@Param("user") User user);

    @Query("SELECT f FROM Friendship f WHERE (f.requester = :user OR f.receiver = :user) AND f.status = :status")
    List<Friendship> findAllByUserAndStatus(@Param("user") User user, @Param("status") String status);

    @Query("SELECT f FROM Friendship f WHERE (f.requester = :u1 AND f.receiver = :u2) OR (f.requester = :u2 AND f.receiver = :u1)")
    Optional<Friendship> findBetweenUsers(@Param("u1") User u1, @Param("u2") User u2);
}