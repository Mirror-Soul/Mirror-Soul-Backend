package com.mirrorsoul.mirrorsoul_api.access;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.LocalDateTime;
public interface AdminCommandRepository extends JpaRepository<AdminCommand,Long> {Optional<AdminCommand> findByActorIdAndRequestKey(Long actor,String key);}