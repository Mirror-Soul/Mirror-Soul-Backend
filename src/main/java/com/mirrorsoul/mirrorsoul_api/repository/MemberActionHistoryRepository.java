package com.mirrorsoul.mirrorsoul_api.repository;
import com.mirrorsoul.mirrorsoul_api.domain.MemberActionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.Optional;
public interface MemberActionHistoryRepository extends JpaRepository<MemberActionHistory, Long> {
    Optional<MemberActionHistory> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);
    Page<MemberActionHistory> findByMemberId(Long memberId, Pageable pageable);
}
