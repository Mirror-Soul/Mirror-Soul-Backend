package com.mirrorsoul.mirrorsoul_api.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import org.junit.jupiter.api.Test;

class JobKoreanLabelMapperTest {

    private final JobKoreanLabelMapper mapper = new JobKoreanLabelMapper();

    @Test
    void mapsJobEnumToKoreanCategory() {
        assertEquals("기술 및 IT", mapper.toKoreanLabel(Job.IT_TECH));
        assertEquals("교육", mapper.toKoreanLabel(Job.EDUCATION));
        assertEquals("의료 및 헬스케어", mapper.toKoreanLabel(Job.MEDICAL_HEALTHCARE));
    }
}
