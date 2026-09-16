package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.dto.region.RegionResDTO;
import com.mirrorsoul.mirrorsoul_api.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Region", description = "지역 검색 및 좌표 API")
@RestController
@RequestMapping("/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @Operation(summary = "전국 읍면동 좌표 조회")
    @GetMapping("/coordinates")
    public ApiResponse<List<RegionResDTO.RegionCoordinateDTO>> getCoordinates() {
        return ApiResponse.onSuccess(
                "지역 좌표 목록 조회에 성공했습니다.",
                regionService.getCoordinates()
        );
    }

    @Operation(summary = "읍면동 검색", description = "읍면동명 또는 시군구명으로 최대 20건을 검색합니다.")
    @GetMapping("/search")
    public ApiResponse<List<RegionResDTO.RegionSearchResultDTO>> search(
            @RequestParam String keyword
    ) {
        return ApiResponse.onSuccess(
                "지역 검색에 성공했습니다.",
                regionService.search(keyword)
        );
    }
}
