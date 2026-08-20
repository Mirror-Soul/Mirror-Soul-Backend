package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.MbtiProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MbtiProfileRepository extends JpaRepository<MbtiProfile, Long> {

    Optional<MbtiProfile> findByUser_Id(Long userId);

    List<MbtiProfile> findAllByUser_IdIn(Collection<Long> userIds);
}
