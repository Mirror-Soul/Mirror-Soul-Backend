package com.mirrorsoul.mirrorsoul_api.domain;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CloneSimilarityTest {
    private static BigDecimal n(String value) { return value == null ? null : new BigDecimal(value); }

    @ParameterizedTest
    @CsvSource({"100,100,100,100,0,95.0", "100,,,,0,28.5", ",,,,0,0.0",
            "90,80,64.25,91.5,1.5,74.0", "10,0,0,20,0,4.8", "0,0,0,0,1,0.0",
            "100,100,100,100,999.99,0.0"})
    void appliesFixedWeightsPenaltyClampingAndHalfUp(String face, String voice, String profile,
            String reliability, String penalty, String expected) {
        assertThat(CloneSimilarityCalculator.calculate(n(face), n(voice), n(profile), n(reliability), n(penalty)))
                .isEqualTo(n(expected));
    }

    @Test
    void legacyTotalIsPreservedUntilAllFourComponentsAreKnown() {
        Clone clone = Clone.builder().syncRate(n("82.0")).build();
        clone.updateFaceSimilarity(1L, n("100"));
        clone.updateProfileSimilarity(null, n("100"), n("100"), n("0"));
        assertThat(clone.getSyncRate()).isEqualTo(n("82.0"));
        assertThat(clone.getSimilarityScoreVersion()).isNull();
        assertThat(clone.getVoiceSimilarityScore()).isNull();
        clone.updateVoiceSimilarity(2L, n("100"));
        assertThat(clone.getSyncRate()).isEqualTo(n("95.0"));
        assertThat(clone.getSimilarityScoreVersion()).isEqualTo(CloneSimilarityCalculator.VERSION);
    }

    @Test
    void newMemberUsesZeroForMissingScoresAndHidesPartialTotal() {
        Clone clone = Clone.builder().similarityScoreVersion(CloneSimilarityCalculator.VERSION).build();
        clone.updateFaceSimilarity(1L, n("100"));
        assertThat(clone.getSyncRate()).isEqualTo(n("28.5"));
        assertThat(clone.getVisibleSyncRate()).isNull();
        clone.updatePersonalityTrainingCompleted(true);
        clone.refreshReadiness(true, true);
        assertThat(clone.getVisibleSyncRate()).isEqualTo(n("28.5"));
        clone.refreshReadiness(false, true);
        assertThat(clone.getVisibleSyncRate()).isNull();
    }

    @Test
    void duplicateAndOlderSourcesCannotOverwriteScores() {
        Clone clone = Clone.builder().similarityScoreVersion(CloneSimilarityCalculator.VERSION).build();
        clone.updateFaceSimilarity(2L, n("90"));
        clone.updateVoiceSimilarity(2L, n("80"));
        clone.updateProfileSimilarity(2L, n("70"), n("60"), n("1"));
        BigDecimal score = clone.getSyncRate();
        assertThat(clone.updateFaceSimilarity(1L, n("0"))).isFalse();
        assertThat(clone.updateVoiceSimilarity(2L, n("0"))).isFalse();
        assertThat(clone.updateProfileSimilarity(1L, n("0"), n("0"), n("0"))).isFalse();
        assertThat(clone.updateProfileSimilarity(null, n("0"), n("0"), n("0"))).isFalse();
        assertThat(clone.getSyncRate()).isEqualTo(score);
    }

    @Test
    void unversionedProfileScoresAreAcceptedOnceAndCanBeUpgradedToARevision() {
        Clone clone = Clone.builder().build();
        assertThat(clone.updateProfileSimilarity(null, n("64.25"), n("91.5"), n("1.5"))).isTrue();
        assertThat(clone.updateProfileSimilarity(null, n("10"), n("10"), n("0"))).isFalse();
        assertThat(clone.updateProfileSimilarity(1L, n("80"), n("90"), n("0"))).isTrue();
        assertThat(clone.getProfileSimilarityScore()).isEqualByComparingTo("80");
    }

    @Test
    void newerUnscoredFaceResultClearsPreviousComponentWithoutInventingQuality() {
        Clone clone = Clone.builder().similarityScoreVersion(CloneSimilarityCalculator.VERSION).build();
        clone.updateFaceSimilarity(1L, n("100"));
        clone.updateFaceSimilarity(2L, null);
        assertThat(clone.getFaceSimilarityScore()).isNull();
        assertThat(clone.getSyncRate()).isEqualTo(n("0.0"));
        assertThat(clone.updateFaceSimilarity(1L, n("100"))).isFalse();
    }

    @Test
    void rejectsInvalidScoresWithoutChangingSourceOrTotal() {
        Clone clone = Clone.builder().build();
        assertThatThrownBy(() -> clone.updateFaceSimilarity(1L, n("100.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> clone.updateVoiceSimilarity(1L, n("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> clone.updateProfileSimilarity(1L, n("1.001"), n("10"), n("0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(clone.getSimilarityFaceJobId()).isNull();
        assertThat(clone.getSimilarityVoiceJobId()).isNull();
        assertThat(clone.getSimilarityProfileRevision()).isNull();
    }
}
