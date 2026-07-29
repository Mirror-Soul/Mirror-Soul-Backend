package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Gender;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUuid(UUID uuid);

    Boolean existsByName(String name);

    boolean existsByEmail(String email);

    @Query("""
        select candidate
        from User candidate
        where candidate.id <> :currentUserId

          and candidate.status =
              com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus.ACTIVE

          and candidate.matchingEnabled = true

          and candidate.gender <> :currentUserGender

          and candidate.birthDate is not null
          and (
                (:adult = true
                    and candidate.birthDate <= :adultBirthDateCutoff)
             or (:adult = false
                    and candidate.birthDate > :adultBirthDateCutoff)
          )

          and candidate.lastActiveAt is not null
          and candidate.lastActiveAt >= :activeSince

          and not exists (
                select 1
                from MeetingRequest request
                where request.status =
                    com.mirrorsoul.mirrorsoul_api.domain.enums.MeetingRequestStatus.ACCEPTED
                  and (
                        (
                            request.sender.id = :currentUserId
                            and request.receiver.id = candidate.id
                        )
                     or (
                            request.receiver.id = :currentUserId
                            and request.sender.id = candidate.id
                        )
                  )
          )

          and not exists (
                select 1
                from SwipeHistory swipe
                where swipe.swiper.id = :currentUserId
                  and swipe.target.id = candidate.id
                  and swipe.createdAt >= :swipedSince
          )

        """)
    List<User> findRecommendationCandidates(
            @Param("currentUserId") Long currentUserId,
            @Param("currentUserGender") Gender currentUserGender,
            @Param("adult") boolean adult,
            @Param("adultBirthDateCutoff") LocalDate adultBirthDateCutoff,
            @Param("activeSince") LocalDateTime activeSince,
            @Param("swipedSince") LocalDateTime swipedSince
    );

    @Modifying
    @Query("""
            update User user set user.lastActiveAt = :now
            where user.id = :userId
              and (user.lastActiveAt is null or user.lastActiveAt < :threshold)
            """)
    int touchLastActiveAt(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now,
            @Param("threshold") LocalDateTime threshold
    );
}
