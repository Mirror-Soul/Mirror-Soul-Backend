package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

public interface VideoCallRepository extends JpaRepository<VideoCall, Long> {
    Optional<VideoCall> findByRoomId(String roomId);

    boolean existsByCloneIdAndStatusIn(Long cloneId, Collection<VideoCallStatus> statuses);

    @Query("""
            select videoCall
            from VideoCall videoCall
            join fetch videoCall.clone clone
            join fetch clone.user
            where videoCall.user.uuid = :userUuid
              and videoCall.status = :status
            order by videoCall.startedAt desc, videoCall.id desc
            """)
    List<VideoCall> findAllByUserUuidAndStatusOrderByLatest(
            @Param("userUuid") UUID userUuid,
            @Param("status") VideoCallStatus status
    );

    @Query("""
            select videoCall
            from VideoCall videoCall
            join fetch videoCall.user caller
            join fetch videoCall.clone clone
            join fetch clone.user cloneOwner
            where videoCall.status = :status
              and videoCall.startedAt >= :startedAt
              and videoCall.startedAt < :endedBefore
              and (caller.uuid = :userUuid or cloneOwner.uuid = :userUuid)
            order by videoCall.startedAt desc, videoCall.id desc
            """)
    List<VideoCall> findRecentHistory(
            @Param("userUuid") UUID userUuid,
            @Param("status") VideoCallStatus status,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("endedBefore") LocalDateTime endedBefore
    );

    @Query("""
            select videoCall
            from VideoCall videoCall
            join fetch videoCall.user caller
            join fetch videoCall.clone clone
            join fetch clone.user cloneOwner
            where videoCall.id = :callId
            """)
    Optional<VideoCall> findByIdWithParticipants(@Param("callId") Long callId);

    @Query("""
            select count(videoCall)
            from VideoCall videoCall
            join videoCall.user caller
            join videoCall.clone clone
            join clone.user cloneOwner
            where videoCall.status = :status
              and videoCall.startedAt <= :startedAt
              and (
                    (caller.uuid = :firstUserUuid and cloneOwner.uuid = :secondUserUuid)
                 or (caller.uuid = :secondUserUuid and cloneOwner.uuid = :firstUserUuid)
              )
            """)
    long countCallsBetweenUsersThrough(
            @Param("firstUserUuid") UUID firstUserUuid,
            @Param("secondUserUuid") UUID secondUserUuid,
            @Param("status") VideoCallStatus status,
            @Param("startedAt") LocalDateTime startedAt
    );
}
