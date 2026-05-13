package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUuid(UUID uuid);

    Boolean existsByName(String name);

    boolean existsByEmail(String email);
}
