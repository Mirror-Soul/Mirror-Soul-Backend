package com.mirrorsoul.mirrorsoul_api.quality;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
public interface CallStageMeasurementRepository extends JpaRepository<CallStageMeasurement,Long>,JpaSpecificationExecutor<CallStageMeasurement> {
    boolean existsByCallIdAndTurnKeyAndStage(Long callId,String key,CallQualityDtos.Stage stage);
    List<CallStageMeasurement> findByCallIdOrderByMeasuredAtDescIdDesc(Long id,Pageable pageable);
}
