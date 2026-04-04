package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionService {

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
}
