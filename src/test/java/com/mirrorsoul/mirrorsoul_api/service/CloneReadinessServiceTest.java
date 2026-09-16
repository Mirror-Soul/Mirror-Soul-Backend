package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class CloneReadinessServiceTest {
    @ParameterizedTest
    @CsvSource({"false,false,false,PENDING", "false,false,true,PENDING", "false,true,false,PENDING",
            "false,true,true,PENDING", "true,false,false,PENDING", "true,false,true,PENDING",
            "true,true,false,PENDING", "true,true,true,READY"})
    void requiresAllThreeConditions(boolean voice, boolean face, boolean personality, String expected) {
        Clone clone = Clone.builder().id(1L).build();
        var clones = mock(CloneRepository.class);
        var voices = mock(AiVoiceProfileRepository.class);
        var faces = mock(AiFaceProfileRepository.class);
        when(clones.findLockedById(1L)).thenReturn(Optional.of(clone));
        when(voices.existsByCloneIdAndActiveTrueAndStatus(1L, "ACTIVE")).thenReturn(voice);
        when(faces.existsByCloneIdAndActiveTrueAndStatus(1L, "READY")).thenReturn(face);
        new CloneReadinessService(clones, voices, faces).updatePersonalityTraining(1L, personality);
        assertThat(clone.getStatus()).isEqualTo(expected);
    }

    @Test
    void callbackRequiresConfiguredMatchingSecret() {
        var readiness = mock(CloneReadinessService.class);
        var callback = new CloneTrainingCallbackService(readiness);
        ReflectionTestUtils.setField(callback, "secret", "");
        assertThatThrownBy(() -> callback.completePersonality(1L, "")).isInstanceOf(RuntimeException.class);
        ReflectionTestUtils.setField(callback, "secret", "expected-secret");
        assertThatThrownBy(() -> callback.completePersonality(1L, "wrong")).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(readiness);
        callback.completePersonality(1L, "expected-secret");
        verify(readiness).updatePersonalityTraining(1L, true);
    }
}
