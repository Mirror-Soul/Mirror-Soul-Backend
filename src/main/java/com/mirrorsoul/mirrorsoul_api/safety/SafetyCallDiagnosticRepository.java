package com.mirrorsoul.mirrorsoul_api.safety;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;
public interface SafetyCallDiagnosticRepository extends JpaRepository<SafetyCallDiagnostic,Long> {
    Optional<SafetyCallDiagnostic> findByCallIdAndEventKey(Long callId,String key);
    List<SafetyCallDiagnostic> findByCallIdOrderByIdDesc(Long callId,org.springframework.data.domain.Pageable pageable);
    long countByCallId(Long callId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from SafetyCallDiagnostic d where d.createdAt<=:cutoff")
    int deleteExpired(@org.springframework.data.repository.query.Param("cutoff") LocalDateTime cutoff);
}
