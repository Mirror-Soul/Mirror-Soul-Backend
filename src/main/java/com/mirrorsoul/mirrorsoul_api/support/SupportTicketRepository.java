package com.mirrorsoul.mirrorsoul_api.support;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
public interface SupportTicketRepository extends JpaRepository<SupportTicket,Long>,JpaSpecificationExecutor<SupportTicket> {
 Optional<SupportTicket> findByMemberIdAndRequestKey(Long memberId,String key);
 Optional<SupportTicket> findByExternalMessageId(String externalMessageId);
 long countByMemberIdAndCreatedAtAfter(Long memberId,LocalDateTime after);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from SupportTicket t where t.id=:id") Optional<SupportTicket> lock(@Param("id") Long id);
}