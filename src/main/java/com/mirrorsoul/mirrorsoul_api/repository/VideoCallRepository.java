package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VideoCallRepository extends JpaRepository<VideoCall, Long> {
    Optional<VideoCall> findByRoomId(String roomId);

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
}
