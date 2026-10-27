package com.mirrorsoul.mirrorsoul_api.safety;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface SafetyNoticeRepository extends JpaRepository<SafetyNotice,Long> {
    Page<SafetyNotice> findByUserId(Long userId,Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select n from SafetyNotice n where n.id=:id and n.userId=:userId")
    Optional<SafetyNotice> findForUpdate(@Param("id") Long id,@Param("userId") Long userId);
}
