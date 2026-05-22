package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VideoCallRepository extends JpaRepository<VideoCall, Long> {
    Optional<VideoCall> findByRoomId(String roomId);
}
