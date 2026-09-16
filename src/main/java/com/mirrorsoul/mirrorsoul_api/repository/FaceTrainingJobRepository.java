package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.FaceTrainingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface FaceTrainingJobRepository extends JpaRepository<FaceTrainingJob, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from FaceTrainingJob j where j.id = :id")
    Optional<FaceTrainingJob> findLockedById(@Param("id") Long id);
}
