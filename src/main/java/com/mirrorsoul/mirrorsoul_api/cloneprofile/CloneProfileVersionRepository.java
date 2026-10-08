package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CloneProfileVersionRepository extends JpaRepository<CloneProfileVersion, Long> {
    @Query("select coalesce(max(profileVersion.versionNumber), 0) from CloneProfileVersion profileVersion where profileVersion.clone.id = :cloneId")
    int findLatestVersionNumber(@Param("cloneId") Long cloneId);
}
