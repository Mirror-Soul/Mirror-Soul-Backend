package com.mirrorsoul.mirrorsoul_api.dto.region;

import java.math.BigDecimal;

public final class RegionResDTO {

    private RegionResDTO() {
    }

    public record RegionCoordinateDTO(
            Long regionId,
            String sidoName,
            String sigunguName,
            String eupmyeondongName,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }

    public record RegionSearchResultDTO(
            Long regionId,
            String sidoName,
            String sigunguName,
            String eupmyeondongName
    ) {
    }
}
