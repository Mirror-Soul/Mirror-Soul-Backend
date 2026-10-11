package com.mirrorsoul.mirrorsoul_api.quality;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TrainingRetryRepository extends JpaRepository<TrainingRetryCommand,Long> {
    Optional<TrainingRetryCommand> findByOperationKey(String key);
    boolean existsBySourceJobId(String id);
}
