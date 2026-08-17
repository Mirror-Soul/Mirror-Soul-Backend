package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @Query("""
            select (count(ub) > 0) from UserBlock ub
            where (ub.blocker.id = :firstUserId and ub.blocked.id = :secondUserId)
               or (ub.blocker.id = :secondUserId and ub.blocked.id = :firstUserId)
            """)
    boolean existsBetween(
            @Param("firstUserId") Long firstUserId,
            @Param("secondUserId") Long secondUserId
    );
}
