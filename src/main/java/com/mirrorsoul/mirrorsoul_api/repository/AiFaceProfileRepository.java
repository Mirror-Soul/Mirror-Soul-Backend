package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.AiFaceProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AiFaceProfileRepository extends JpaRepository<AiFaceProfile, Long> {
    List<AiFaceProfile> findAllByCloneIdAndActiveTrue(Long cloneId);
    boolean existsByCloneIdAndActiveTrueAndStatus(Long cloneId, String status);
}
