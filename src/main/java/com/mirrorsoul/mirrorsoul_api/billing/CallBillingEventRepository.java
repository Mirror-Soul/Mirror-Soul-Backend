package com.mirrorsoul.mirrorsoul_api.billing;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CallBillingEventRepository extends JpaRepository<CallBillingEvent, Long> {
    Optional<CallBillingEvent> findByEventKey(String key);
}
