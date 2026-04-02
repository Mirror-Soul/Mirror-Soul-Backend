package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.FaceFile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaceFileRepository extends JpaRepository<FaceFile, Long> {

    Optional<FaceFile> findByUser_Id(Long userId);
}
