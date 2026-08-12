package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.FaceTrainingJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaceTrainingJobRepository extends JpaRepository<FaceTrainingJob, Long> {
}
