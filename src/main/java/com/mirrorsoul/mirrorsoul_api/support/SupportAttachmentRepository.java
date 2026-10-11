package com.mirrorsoul.mirrorsoul_api.support;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
public interface SupportAttachmentRepository extends JpaRepository<SupportAttachment,Long> {
 List<SupportAttachment> findByTicketIdAndConfirmedTrueAndDeletedFalseAndExpiresAtAfter(Long ticketId,LocalDateTime now);
 long countByTicketIdAndDeletedFalse(Long ticketId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from SupportAttachment a where a.id=:id") Optional<SupportAttachment> lock(@Param("id") Long id);
 @Query("select a.id from SupportAttachment a where a.deleted=false and a.expiresAt<=:now order by a.id") List<Long> expired(@Param("now") LocalDateTime now,org.springframework.data.domain.Pageable page);
}