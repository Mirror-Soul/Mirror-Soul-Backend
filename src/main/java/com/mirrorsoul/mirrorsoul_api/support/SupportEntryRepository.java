package com.mirrorsoul.mirrorsoul_api.support;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
public interface SupportEntryRepository extends JpaRepository<SupportEntry,Long> {
 Optional<SupportEntry> findByTicketIdAndRequestKey(Long ticketId,String key);
 List<SupportEntry> findByTicketIdOrderByIdAsc(Long ticketId);
 List<SupportEntry> findByTicketIdAndTypeInOrderByIdAsc(Long ticketId,Collection<SupportTypes.EntryType> types);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from SupportEntry e where e.id=:id") Optional<SupportEntry> lock(@Param("id") Long id);
 @Query("select e.id from SupportEntry e where e.delivery=:delivery order by e.id") List<Long> pending(@Param("delivery") SupportTypes.Delivery delivery,org.springframework.data.domain.Pageable page);
 @Modifying @Query("update SupportEntry e set e.delivery=:unknown,e.failureCode='DELIVERY_UNCERTAIN' where e.delivery=:sending and e.attemptedAt<:cutoff") int recover(@Param("cutoff") LocalDateTime cutoff,@Param("unknown") SupportTypes.Delivery unknown,@Param("sending") SupportTypes.Delivery sending);
}