package com.mirrorsoul.mirrorsoul_api.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mirrorsoul.mirrorsoul_api.domain.enums.Gender;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        },
        showSql = false
)
class UserRepositoryRecommendationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findRecommendationCandidatesReturnsEmptyListWithoutParameterBindingError() {
        List<?> candidates = userRepository.findRecommendationCandidates(
                1L,
                Gender.MALE,
                true,
                LocalDate.now().minusYears(19),
                LocalDateTime.now().minusDays(30),
                false,
                List.of(-1L)
        );

        assertThat(candidates).isEmpty();
    }
}
