package com.mirrorsoul.mirrorsoul_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirrorsoul.mirrorsoul_api.config.AwsSqsProperties;
import com.mirrorsoul.mirrorsoul_api.domain.ValueBalanceAnalysisJob;
import com.mirrorsoul.mirrorsoul_api.domain.enums.ValueBalanceAnalysisJobStatus;
import com.mirrorsoul.mirrorsoul_api.dto.valuebalance.ValueBalanceAnalysisMessageDTO;
import com.mirrorsoul.mirrorsoul_api.repository.ValueBalanceAnalysisJobRepository;
import com.mirrorsoul.mirrorsoul_api.repository.ValueBalanceAnswerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValueBalanceAnalysisJobPublisher {
    private static final int SET_SIZE = 8;

    private final ValueBalanceAnalysisJobRepository jobRepository;
    private final ValueBalanceAnswerRepository answerRepository;
    private final AwsSqsProperties properties;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(Long jobId) {
        ValueBalanceAnalysisJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Value balance job not found: " + jobId));
        List<ValueBalanceAnalysisMessageDTO.Answer> answers = answerRepository
                .findByUserIdOrderByAnsweredAtAscIdAsc(job.getUser().getId(),
                        PageRequest.of(job.getSetNumber() - 1, SET_SIZE))
                .stream().map(ValueBalanceAnalysisMessageDTO.Answer::from).toList();
        String previousSummary = jobRepository
                .findFirstByUserIdAndStatusAndSetNumberLessThanOrderBySetNumberDesc(
                        job.getUser().getId(), ValueBalanceAnalysisJobStatus.COMPLETED, job.getSetNumber())
                .map(ValueBalanceAnalysisJob::getPersonalitySummary).orElse(null);
        var message = new ValueBalanceAnalysisMessageDTO(job.getId(), job.getUser().getUuid(),
                job.getSetNumber(), previousSummary, answers);
        try {
            String body = objectMapper.writeValueAsString(message);
            SendMessageResponse response = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(properties.getValueBalanceAnalysisQueueUrl()).messageBody(body).build());
            job.markMessageSent(response.messageId());
            log.info("Published value balance analysis job. jobId={}, set={}", jobId, job.getSetNumber());
        } catch (JsonProcessingException | SqsException | SdkClientException e) {
            job.markDispatchFailed(e.getMessage());
            log.error("Failed to publish value balance analysis job. jobId={}", jobId, e);
        }
    }
}
