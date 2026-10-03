package com.mirrorsoul.mirrorsoul_api.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.ExceptionAdvice;
import com.mirrorsoul.mirrorsoul_api.dto.ClonePersonalityCompleteRequest;
import com.mirrorsoul.mirrorsoul_api.dto.CloneVoiceCompleteRequest;
import com.mirrorsoul.mirrorsoul_api.service.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CloneTrainingControllerTest {
    private final CloneReadinessService readiness = mock(CloneReadinessService.class);
    private final CloneSimilarityService similarity = mock(CloneSimilarityService.class);
    private MockMvc mvc;
    private CloneTrainingCallbackService callback;
    private static final String URL = "/internal/clone-training/1/personality/complete";
    private static final String HEADER = "X-Clone-Training-Callback-Secret";

    @BeforeEach
    void setup() {
        callback = new CloneTrainingCallbackService(readiness, similarity);
        ReflectionTestUtils.setField(callback, "secret", "test-secret");
        mvc = MockMvcBuilders.standaloneSetup(new CloneTrainingController(callback))
                .setControllerAdvice(new ExceptionAdvice()).build();
    }

    @Test
    void oldBodylessAndNewScoredCallbacksAreBothSupported() throws Exception {
        mvc.perform(post(URL).header(HEADER, "test-secret")).andExpect(status().isOk());
        verify(readiness).updatePersonalityTraining(1L, true);
        mvc.perform(post(URL).header(HEADER, "test-secret").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"calculationVersion":"clone-similarity-v1","profileScore":64.25,
                         "dataReliabilityScore":91.5,"penaltyScore":1.5}
                        """)).andExpect(status().isOk());
        var request = ArgumentCaptor.forClass(ClonePersonalityCompleteRequest.class);
        verify(similarity).completePersonality(eq(1L), request.capture());
        assertThat(request.getValue().profileScore()).isEqualTo(new BigDecimal("64.25"));
        assertThat(request.getValue().sourceRevision()).isNull();
    }

    @Test
    void revisionAndVoiceJobIdentifiersAreForwarded() throws Exception {
        mvc.perform(post(URL).header(HEADER, "test-secret").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"calculationVersion":"clone-similarity-v1","profileScore":64.25,
                         "dataReliabilityScore":91.5,"penaltyScore":1.5,"sourceRevision":2}
                        """)).andExpect(status().isOk());
        verify(similarity).completePersonality(1L, new ClonePersonalityCompleteRequest(
                "clone-similarity-v1", new BigDecimal("64.25"), new BigDecimal("91.5"), new BigDecimal("1.5"), 2L));
        mvc.perform(post("/internal/clone-training/1/voice/complete").header(HEADER, "test-secret")
                .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":3,\"voiceScore\":82.35}"))
                .andExpect(status().isOk());
        verify(similarity).completeVoice(1L, new CloneVoiceCompleteRequest(3L, new BigDecimal("82.35")));
    }

    @Test
    void secretProtectsBothCallbacks() throws Exception {
        mvc.perform(post(URL)).andExpect(status().isForbidden());
        mvc.perform(post(URL).header(HEADER, "wrong")).andExpect(status().isForbidden());
        mvc.perform(post("/internal/clone-training/1/voice/complete")
                .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":3,\"voiceScore\":82.35}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/clone-training/1/voice/complete").header(HEADER, "wrong")
                .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":3,\"voiceScore\":82.35}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(readiness, similarity);
    }

    @Test
    void voiceCallbackIsDisabledWhenQueueConsumerIsEnabled() throws Exception {
        ReflectionTestUtils.setField(callback, "voiceResultConsumerEnabled", true);
        mvc.perform(post("/internal/clone-training/1/voice/complete").header(HEADER, "test-secret")
                .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":3,\"voiceScore\":82.35}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(similarity);
        mvc.perform(post(URL).header(HEADER, "test-secret")).andExpect(status().isOk());
        verify(readiness).updatePersonalityTraining(1L, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"calculationVersion\":\"future-version\",\"profileScore\":64,\"dataReliabilityScore\":91,\"penaltyScore\":0}",
            "{\"calculationVersion\":\"clone-similarity-v1\",\"profileScore\":101,\"dataReliabilityScore\":91,\"penaltyScore\":0}",
            "{\"calculationVersion\":\"clone-similarity-v1\",\"profileScore\":64.251,\"dataReliabilityScore\":91,\"penaltyScore\":0}",
            "{\"calculationVersion\":\"clone-similarity-v1\",\"profileScore\":64,\"dataReliabilityScore\":91,\"penaltyScore\":-1}",
            "{\"calculationVersion\":\"clone-similarity-v1\",\"profileScore\":64,\"dataReliabilityScore\":91,\"penaltyScore\":0,\"sourceRevision\":0}"})
    void invalidScoreBodiesDoNotReachPersistence(String body) throws Exception {
        mvc.perform(post(URL).header(HEADER, "test-secret").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(readiness, similarity);
    }

    @Test
    void voiceRequiresBodyAndAllFields() throws Exception {
        mvc.perform(post("/internal/clone-training/1/voice/complete").header(HEADER, "test-secret"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/internal/clone-training/1/voice/complete").header(HEADER, "test-secret")
                .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":3}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(readiness, similarity);
    }
}
