package com.mirrorsoul.mirrorsoul_api.billing;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BillingAuditRepository extends JpaRepository<BillingAudit, Long> {
    Optional<BillingAudit> findByOperationKey(String key);
}
