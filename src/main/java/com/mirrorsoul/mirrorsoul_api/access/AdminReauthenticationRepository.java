package com.mirrorsoul.mirrorsoul_api.access;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.LocalDateTime;
public interface AdminReauthenticationRepository extends JpaRepository<AdminReauthentication,String> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from AdminReauthentication r where r.tokenHash=:hash") Optional<AdminReauthentication> lock(@Param("hash") String hash);
 @Modifying @Query("delete from AdminReauthentication r where r.expiresAt<:cutoff") int purge(@Param("cutoff") LocalDateTime cutoff);
}