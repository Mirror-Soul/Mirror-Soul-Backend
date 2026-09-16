package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.dto.region.RegionResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionService {

    private static final int SEARCH_RESULT_LIMIT = 20;

    private final RegionRepository regionRepository;

    @Transactional(readOnly = true)
    public List<String> getSidoList() {
        return regionRepository.findDistinctSidoNames();
    }

    @Transactional(readOnly = true)
    public List<String> getSigunguList(String sidoName) {
        return regionRepository.findDistinctSigunguNamesBySidoName(sidoName);
    }

    @Transactional(readOnly = true)
    public List<String> getEupmyeondongList(String sidoName, String sigunguName) {
        return regionRepository.findDistinctEupmyeondongNamesBySidoNameAndSigunguName(
                sidoName, sigunguName
        );
    }

    @Transactional(readOnly = true)
    public List<RegionResDTO.RegionCoordinateDTO> getCoordinates() {
        return regionRepository.findAllByLatitudeIsNotNullAndLongitudeIsNotNullOrderByIdAsc().stream()
                .map(region -> new RegionResDTO.RegionCoordinateDTO(
                        region.getId(),
                        region.getSidoName(),
                        region.getSigunguName(),
                        region.getEupmyeondongName(),
                        region.getLatitude(),
                        region.getLongitude()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RegionResDTO.RegionSearchResultDTO> search(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "검색어를 입력해주세요."
            );
        }

        return regionRepository.searchWithCoordinates(
                        normalizedKeyword,
                        PageRequest.of(0, SEARCH_RESULT_LIMIT)
                ).stream()
                .map(region -> new RegionResDTO.RegionSearchResultDTO(
                        region.getId(),
                        region.getSidoName(),
                        region.getSigunguName(),
                        region.getEupmyeondongName()
                ))
                .toList();
    }
}
