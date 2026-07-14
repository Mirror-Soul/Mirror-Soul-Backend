package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.MeetingRequest;
import com.mirrorsoul.mirrorsoul_api.domain.enums.MeetingRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRequestRepository extends JpaRepository<MeetingRequest, Long> {

    @Query("""
            select mr from MeetingRequest mr
            join fetch mr.sender sender
            join fetch mr.videoCall videoCall
            where mr.receiver.uuid = :receiverUuid
              and mr.status = :status
            order by mr.createdAt desc, mr.id desc
            """)
    List<MeetingRequest> findAllReceivedByStatus(
            @Param("receiverUuid") UUID receiverUuid,
            @Param("status") MeetingRequestStatus status
    );

    boolean existsByActivePairKey(String activePairKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select mr from MeetingRequest mr
            join fetch mr.sender
            join fetch mr.receiver
            where mr.id = :id
            """)
    Optional<MeetingRequest> findByIdForUpdate(@Param("id") Long id);
}
