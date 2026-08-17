package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.ClonePersonalityTag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClonePersonalityTagRepository
        extends JpaRepository<ClonePersonalityTag, Long> {

    List<ClonePersonalityTag> findAllByCloneIdOrderByDisplayOrderAsc(Long cloneId);

    @Query("""
            select tag
            from ClonePersonalityTag tag
            join fetch tag.clone clone
            join fetch clone.user user
            where user.id in :userIds
            order by user.id, tag.displayOrder
            """)
    List<ClonePersonalityTag> findAllByUserIds(
            @Param("userIds") Collection<Long> userIds
    );
}
