package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.TalkTimeTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TalkTimeTransactionRepository extends JpaRepository<TalkTimeTransaction, Long> {
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(t.deltaSeconds), 0) from TalkTimeTransaction t where t.user.id = :id")
    long balance(@org.springframework.data.repository.query.Param("id") Long id);
    java.util.Optional<TalkTimeTransaction> findByOperationKey(String key);
    java.util.Optional<TalkTimeTransaction> findByVideoCallId(Long callId);
    org.springframework.data.domain.Page<TalkTimeTransaction> findByUserIdOrderByIdDesc(Long userId, org.springframework.data.domain.Pageable pageable);
    java.util.List<TalkTimeTransaction> findByPaymentIdOrderByIdAsc(Long paymentId);
    @org.springframework.data.jpa.repository.Query(value = """
            select count(*) from users u left join
            (select user_id, sum(delta_seconds) balance from talk_time_transactions group by user_id) t
            on u.id = t.user_id where u.remaining_talk_time <> coalesce(t.balance, 0)
            """, nativeQuery = true)
    long countBalanceMismatches();
}
