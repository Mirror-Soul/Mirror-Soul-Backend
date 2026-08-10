package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.Sigungu;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigunguRepository extends JpaRepository<Sigungu, Long> {

    Optional<Sigungu> findBySidoNameAndSigunguName(String sidoName, String sigunguName);

    List<Sigungu> findAllByOrderBySidoNameAscSigunguNameAsc();
}
