package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.ChatRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    boolean existsByParticipantPairKey(String participantPairKey);
    Optional<ChatRoom> findByParticipantPairKey(String participantPairKey);
    Optional<ChatRoom> findByCreatedFromMeetingRequestId(Long meetingRequestId);
}
