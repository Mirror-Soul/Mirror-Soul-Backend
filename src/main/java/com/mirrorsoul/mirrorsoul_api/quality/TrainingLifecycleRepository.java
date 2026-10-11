package com.mirrorsoul.mirrorsoul_api.quality;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TrainingLifecycleRepository extends JpaRepository<TrainingLifecycleEvent,Long> {
    Optional<TrainingLifecycleEvent> findByEventKey(String key);
    Optional<TrainingLifecycleEvent> findFirstByTrainingJobIdAndAcceptedTrueOrderBySequenceDesc(String id);
    Optional<TrainingLifecycleEvent> findFirstByTrainingJobIdAndAcceptedTrueAndStateOrderBySequenceAsc(String id,TrainingDtos.State state);
    Optional<TrainingLifecycleEvent> findFirstByTrainingJobIdAndSequenceOrderByIdAsc(String id,long sequence);
}
