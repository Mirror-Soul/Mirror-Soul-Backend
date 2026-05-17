package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvolveService {

    private final CloneRepository cloneRepository;

    public EvolveResDTO.twinSyncDTO twinSync(UUID uuid) {

        Integer syncRate = cloneRepository.findSyncRateByUserUuid(uuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CLONE_NOT_FOUND));

        return EvolveResDTO.twinSyncDTO.builder()
                .syncRate(syncRate)
                .build();
    }

    public EvolveResDTO.speechLineDTO speechLine(UUID uuid) {

        //문장 생성 로직
        String speechLine = "Dummy Data";

        return EvolveResDTO.speechLineDTO.builder()
                .speechLine(speechLine)
                .build();
    }


}
