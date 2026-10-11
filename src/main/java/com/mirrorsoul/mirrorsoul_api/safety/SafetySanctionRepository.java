package com.mirrorsoul.mirrorsoul_api.safety;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;
public interface SafetySanctionRepository extends JpaRepository<SafetySanction,Long> {
    Optional<SafetySanction> findByReportId(Long reportId);
    List<SafetySanction> findByUserIdAndReleasedAtIsNullAndExpiresAtAfter(Long userId,LocalDateTime now);
    List<SafetySanction> findByUserIdOrderByIdDesc(Long userId,org.springframework.data.domain.Pageable pageable);
}
