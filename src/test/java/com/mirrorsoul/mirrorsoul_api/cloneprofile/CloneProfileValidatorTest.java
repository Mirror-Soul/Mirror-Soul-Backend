package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.GeneratedCloneProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class CloneProfileValidatorTest {
    private final CloneProfileValidator validator = new CloneProfileValidator();

    @Test
    void normalizesHashPrefixAndAcceptsThreeKoreanTags() {
        GeneratedCloneProfile result = validator.validate(new GeneratedCloneProfile(
                "  새로운 경험을 즐기며 상대의 이야기를 잘 듣습니다. 대화에서는 따뜻한 공감을 중요하게 생각합니다.  ",
                List.of("#호기심", " 따뜻한 공감 ", "경청하는 사람")));

        assertEquals(List.of("호기심", "따뜻한 공감", "경청하는 사람"), result.personalityTags());
    }

    @Test
    void rejectsDuplicateOrNonKoreanTags() {
        assertThrows(CloneProfileGenerationException.class, () -> validator.validate(
                new GeneratedCloneProfile("차분하게 대화합니다. 상대의 관점을 존중합니다.",
                        List.of("차분함", "차분함", "ENFP"))));
    }

    @Test
    void rejectsMarkdownSummary() {
        assertThrows(CloneProfileGenerationException.class, () -> validator.validate(
                new GeneratedCloneProfile("# 성격\n따뜻한 사람입니다.",
                        List.of("차분함", "호기심", "공감 능력"))));
    }
}
