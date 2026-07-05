package com.mirrorsoul.mirrorsoul_api.dto.evolve;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class EvolveResDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class twinSyncDTO {
        Integer syncRate;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class speechLineDTO {
        String speechLine;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class voiceUpdateJobDTO {
        Long jobId;
        String status;
    }
}
