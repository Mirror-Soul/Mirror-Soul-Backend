package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.ValueBalanceAnalysisJob;
import com.mirrorsoul.mirrorsoul_api.dto.valuebalance.ValueBalanceAnalysisCallbackDTO;
import com.mirrorsoul.mirrorsoul_api.repository.ValueBalanceAnalysisJobRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ValueBalanceAnalysisService {
    private final ValueBalanceAnalysisJobRepository jobRepository;

    @Value("${value-balance.callback-secret}")
    private String callbackSecret;

    @Transactional
    public void complete(Long jobId, String suppliedSecret, ValueBalanceAnalysisCallbackDTO request) {
        if (suppliedSecret == null || !MessageDigest.isEqual(
                callbackSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        ValueBalanceAnalysisJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.VALUE_BALANCE_ANALYSIS_JOB_NOT_FOUND));
        job.complete(request.personalitySummary());
    }
}
