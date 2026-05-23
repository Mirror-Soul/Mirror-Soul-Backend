package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.InterviewRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRecordRepository extends JpaRepository<InterviewRecord, Long> {

    Optional<InterviewRecord> findByUser_IdAndInterview_Id(Long userId, Long interviewId);

    long countByUser_Id(Long userId);

    List<InterviewRecord> findAllByUser_IdOrderByInterview_IdAsc(Long userId);
}
