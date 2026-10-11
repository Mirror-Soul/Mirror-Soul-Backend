package com.mirrorsoul.mirrorsoul_api.access;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.LocalDateTime;
public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent,Long>,JpaSpecificationExecutor<AdminAuditEvent> {
 long countByStaffIdAndActionAndOccurredAtAfter(Long staffId,String action,LocalDateTime time);
 @Modifying @Query("delete from AdminAuditEvent e where e.occurredAt<:cutoff") int purge(@Param("cutoff") LocalDateTime cutoff);
}