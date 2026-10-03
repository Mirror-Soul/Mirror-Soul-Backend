package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.config.AwsSqsProperties;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.FaceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.FaceTrainingJobFile;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.FaceTrainingJobSource;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.FaceTrainingJobRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

class FaceTrainingJobPublisherTest {

    @Test
    void publishesFaceUpdateToExistingFaceTrainingQueue() throws Exception {
        UUID uuid = UUID.randomUUID();
        String objectKey = "face-images/" + uuid + "/new-face.jpg";
        User user = User.builder().id(1L).uuid(uuid).build();
        FaceTrainingJob job = mock(FaceTrainingJob.class);
        FaceTrainingJobFile file = mock(FaceTrainingJobFile.class);
        Clone clone = mock(Clone.class);
        FaceTrainingJobRepository jobs = mock(FaceTrainingJobRepository.class);
        CloneRepository clones = mock(CloneRepository.class);
        SqsClient sqs = mock(SqsClient.class);
        AwsS3Properties storage = new AwsS3Properties();
        storage.setBucket("face-bucket");
        AwsSqsProperties queues = new AwsSqsProperties();
        queues.setFaceTrainingQueueUrl("https://sqs.example.com/face-training");
        when(jobs.findLockedById(42L)).thenReturn(Optional.of(job));
        when(job.getId()).thenReturn(42L);
        when(job.getUser()).thenReturn(user);
        when(job.getSource()).thenReturn(FaceTrainingJobSource.FACE_UPDATE);
        when(job.getFiles()).thenReturn(List.of(file));
        when(file.getObjectKey()).thenReturn(objectKey);
        when(clones.findByUserUuid(uuid)).thenReturn(Optional.of(clone));
        when(clone.getId()).thenReturn(7L);
        when(sqs.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("message-1").build());

        new FaceTrainingJobPublisher(jobs, clones, storage, queues, sqs, new ObjectMapper()).publish(42L);

        ArgumentCaptor<SendMessageRequest> request = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(request.capture());
        assertThat(request.getValue().queueUrl()).isEqualTo("https://sqs.example.com/face-training");
        var body = new ObjectMapper().readTree(request.getValue().messageBody());
        assertThat(body.get("jobType").asText()).isEqualTo("FACE_PROFILE_BUILD");
        assertThat(body.get("source").asText()).isEqualTo("FACE_UPDATE");
        assertThat(body.get("jobId").asLong()).isEqualTo(42L);
        assertThat(body.get("objectKeys").get(0).asText()).isEqualTo(objectKey);
        verify(job).markMessageSent("message-1");
    }
}
