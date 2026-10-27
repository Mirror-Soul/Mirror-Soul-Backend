package com.mirrorsoul.mirrorsoul_api.quality;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface CallQualityEventRepository extends JpaRepository<CallQualityEvent,Long> {
    Optional<CallQualityEvent> findByEventKey(String key);
    @Query("select e from CallQualityEvent e where e.id in (select min(a.id) from CallQualityEvent a where a.call.id=:id and a.turnKey=:turn and a.step not in ('CONNECTION','ERROR') group by a.step) order by e.id")
    List<CallQualityEvent> anchors(@Param("id") Long callId,@Param("turn") String turnKey);
    Optional<CallQualityEvent> findFirstByCallIdAndStepOrderByOccurredAtDescIdDesc(Long callId,CallQualityDtos.Step step);
    @Query("select e from CallQualityEvent e where e.call.id=:id and e.errorCode is not null order by e.occurredAt desc,e.id desc")
    List<CallQualityEvent> errors(@Param("id") Long id,org.springframework.data.domain.Pageable page);
    long countByCallId(Long callId);
}
