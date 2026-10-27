package com.mirrorsoul.mirrorsoul_api.support;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
public interface SupportRetryRepository extends JpaRepository<SupportRetry,Long> {Optional<SupportRetry> findByEntryIdAndRequestKey(Long entryId,String key);}