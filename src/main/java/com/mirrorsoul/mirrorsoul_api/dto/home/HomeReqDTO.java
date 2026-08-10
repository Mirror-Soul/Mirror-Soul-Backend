package com.mirrorsoul.mirrorsoul_api.dto.home;

import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class HomeReqDTO {

    private HomeReqDTO() {
    }

    public record RefillTalkTimeDTO(
            @NotNull(message = "충전할 대화 시간은 필수입니다.")
            @Min(value = 1, message = "충전할 대화 시간은 1초 이상이어야 합니다.")
            Integer seconds
    ) {
    }

    public record UpdatePreferredRegionsDTO(
            @NotEmpty(message = "선호 지역을 1개 이상 선택해야 합니다.")
            @Size(max = 3, message = "선호 지역은 최대 3개까지 선택할 수 있습니다.")
            List<@NotNull(message = "선호 지역 정보는 필수입니다.") @Valid PreferredRegionDTO> regions
    ) {
    }

    public record PreferredRegionDTO(
            @NotBlank(message = "시도명은 필수입니다.") String sidoName,
            @NotBlank(message = "시군구명은 필수입니다.") String sigunguName
    ) {
    }
}
