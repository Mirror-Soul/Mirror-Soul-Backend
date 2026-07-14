package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.User;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUuid(UUID uuid);

    Boolean existsByName(String name);

    boolean existsByEmail(String email);

    @Modifying
    @Query("""
            update User user set user.lastActiveAt = :now
            where user.id = :userId
              and (user.lastActiveAt is null or user.lastActiveAt < :threshold)
            """)
    int touchLastActiveAt(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now,
            @Param("threshold") LocalDateTime threshold
    );
}
