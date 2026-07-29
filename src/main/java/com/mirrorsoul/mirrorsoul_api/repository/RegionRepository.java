package com.mirrorsoul.mirrorsoul_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.mirrorsoul.mirrorsoul_api.domain.Region;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RegionRepository extends JpaRepository<Region, Long> {
    Region findBySidoNameAndSigunguNameAndEupmyeondongName(String sidoName, String sigunguName, String eupmyeondongName);

    List<Region> findAllByLatitudeIsNullOrLongitudeIsNullOrderByIdAsc();

    @Query("select distinct r.sidoName from Region r order by r.sidoName asc")
    List<String> findDistinctSidoNames();

    @Query("""
        select distinct r.sigunguName
        from Region r
        where r.sidoName = :sidoName
        order by r.sigunguName asc
    """)
    List<String> findDistinctSigunguNamesBySidoName(@Param("sidoName") String sidoName);

    @Query("""
        select distinct r.eupmyeondongName
        from Region r
        where r.sidoName = :sidoName
          and r.sigunguName = :sigunguName
        order by r.eupmyeondongName asc
    """)
    List<String> findDistinctEupmyeondongNamesBySidoNameAndSigunguName(
            @Param("sidoName") String sidoName,@Param("sigunguName") String sigunguName
    );
}
