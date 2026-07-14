package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.CallMatchAnalysis;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallMatchAnalysisRepository extends JpaRepository<CallMatchAnalysis, Long> {
    List<CallMatchAnalysis> findAllByVideoCallIdIn(Collection<Long> videoCallIds);
}
