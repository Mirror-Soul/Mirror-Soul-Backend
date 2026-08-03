package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.TalkLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TalkLogRepository extends JpaRepository<TalkLog, Long> {

    List<TalkLog> findAllByVideoCallIdOrderByStartedAtAscIdAsc(Long videoCallId);

    Optional<TalkLog> findByIdAndVideoCallId(Long id, Long videoCallId);
}
