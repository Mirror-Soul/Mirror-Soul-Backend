package com.mirrorsoul.mirrorsoul_api.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mirrorsoul.mirrorsoul_api.repository.RagProfileJobRepository;
import com.mirrorsoul.mirrorsoul_api.scheduler.RagProfileScheduler;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagProfileSchedulerTest {
    @Test
    void failedDeliveryIsPersistedAndDoesNotBlockNextJob() {
        var jobs = mock(RagProfileJobRepository.class);
        var service = mock(RagProfileJobService.class);
        var client = mock(RagProfileClient.class);
        var first = new RagProfileJobService.ClaimedProfile(1L, 1, "first", null);
        var second = new RagProfileJobService.ClaimedProfile(2L, 1, "second", null);
        when(jobs.findDue(any(), any())).thenReturn(List.of(1L, 2L));
        when(service.claim(1L)).thenReturn(first);
        when(service.claim(2L)).thenReturn(second);
        doThrow(new IllegalStateException("sensitive response must not be recorded"))
                .doNothing().when(client).send(null);
        new RagProfileScheduler(jobs, service, client).publish();
        verify(service).finish(first, "IllegalStateException");
        verify(service).finish(second, null);
    }
}
