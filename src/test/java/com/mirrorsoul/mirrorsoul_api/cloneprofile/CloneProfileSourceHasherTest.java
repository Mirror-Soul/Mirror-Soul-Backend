package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.CloneProfileGenerationInput;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.InterviewAnswer;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.MbtiAxisScores;
import com.mirrorsoul.mirrorsoul_api.config.CloneProfileGenerationProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class CloneProfileSourceHasherTest {
    private final CloneProfileGenerationProperties properties = new CloneProfileGenerationProperties();
    private final CloneProfileSourceHasher hasher = new CloneProfileSourceHasher(
            JsonMapper.builder().findAndAddModules().build(), properties);

    @Test
    void sameCanonicalSourceAlwaysProducesSameHash() {
        CloneProfileGenerationInput input = input("clone-profile-v1");
        assertEquals(hasher.hash(input), hasher.hash(input));
        assertEquals(64, hasher.hash(input).length());
    }

    @Test
    void promptVersionChangesHash() {
        assertNotEquals(hasher.hash(input("clone-profile-v1")), hasher.hash(input("clone-profile-v2")));
    }

    private CloneProfileGenerationInput input(String promptVersion) {
        return new CloneProfileGenerationInput(
                "음악과 산책을 좋아합니다.", "ENFP", new MbtiAxisScores(70, 65, 60, 55),
                List.of(new InterviewAnswer(1L, "주말에는 무엇을 하나요?", "영화를 봅니다.")),
                "새로운 경험을 선호합니다.", promptVersion);
    }
}
