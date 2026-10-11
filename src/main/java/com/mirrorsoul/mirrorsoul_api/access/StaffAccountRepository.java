package com.mirrorsoul.mirrorsoul_api.access;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.LocalDateTime;
public interface StaffAccountRepository extends JpaRepository<StaffAccount,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from StaffAccount s where s.userId=:id") Optional<StaffAccount> lock(@Param("id") Long id);
}