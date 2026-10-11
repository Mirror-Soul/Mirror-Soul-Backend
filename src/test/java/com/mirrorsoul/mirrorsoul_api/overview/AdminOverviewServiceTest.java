package com.mirrorsoul.mirrorsoul_api.overview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mirrorsoul.mirrorsoul_api.access.*;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import static com.mirrorsoul.mirrorsoul_api.overview.OverviewDtos.*;

class AdminOverviewServiceTest {
    AdminPermissionService permissions;
    OverviewCountReader reader;
    AdminOverviewService service;
    @BeforeEach void setup() {
        permissions=mock(AdminPermissionService.class);reader=mock(OverviewCountReader.class);
        service=new AdminOverviewService(permissions,reader,mock(OverviewTelemetry.class));
        ReflectionTestUtils.setField(service,"stallSeconds",900L);
    }
    @Test void forbiddenCardsAreNeverCountedAndAreNotReturned() {
        when(permissions.permissions(1L)).thenReturn(Set.of(AdminPermission.support_read));
        var overview=service.get(1L,null,null);
        assertThat(overview.cards()).extracting(Card::key).containsExactly(Key.UNANSWERED_SUPPORT);
        assertThat(overview.cards().get(0).count()).isZero();
        verify(reader,times(1)).count(eq(Key.UNANSWERED_SUPPORT),any(),any());
        verifyNoMoreInteractions(reader);
    }
    @Test void failureIsUnavailableWithNullCountWhileSuccessfulZeroRemainsReady() {
        when(permissions.permissions(1L)).thenReturn(Set.of(AdminPermission.support_read,AdminPermission.moderation_read));
        when(reader.count(eq(Key.UNANSWERED_SUPPORT),any(),any()))
                .thenThrow(new DataAccessResourceFailureException("private SQL user data"));
        var overview=service.get(1L,null,null);
        assertThat(overview.dataState()).isEqualTo(State.PARTIAL);
        var failed=overview.cards().stream().filter(c->c.key()==Key.UNANSWERED_SUPPORT).findFirst().orElseThrow();
        var ready=overview.cards().stream().filter(c->c.key()==Key.URGENT_REPORTS).findFirst().orElseThrow();
        assertThat(failed.count()).isNull();assertThat(failed.dataState()).isEqualTo(DataState.UNAVAILABLE);
        assertThat(failed.failureCode()).isEqualTo("QUERY_FAILED");
        assertThat(ready.count()).isZero();assertThat(ready.dataState()).isEqualTo(DataState.READY);
        assertThat(overview.toString()).doesNotContain("private SQL user data");
    }
    @Test void allFailuresRemainUnavailableAndNoPermissionYieldsAnEmptyCompleteResponse() {
        when(permissions.permissions(1L)).thenReturn(Set.of(AdminPermission.clone_training_read));
        when(reader.count(any(),any(),any())).thenThrow(new IllegalStateException("secret"));
        assertThat(service.get(1L,null,null).dataState()).isEqualTo(State.UNAVAILABLE);
        when(permissions.permissions(1L)).thenReturn(Set.of());
        var empty=service.get(1L,null,null);
        assertThat(empty.dataState()).isEqualTo(State.COMPLETE);assertThat(empty.cards()).isEmpty();
    }
    @Test void defaultPeriodAndStalledDrilldownUseOneReferenceInstant() {
        when(permissions.permissions(1L)).thenReturn(Set.of(AdminPermission.clone_training_read));
        var overview=service.get(1L,null,null);
        assertThat(overview.period().from()).isNull();assertThat(overview.period().to()).isEqualTo(overview.asOf());
        var card=overview.cards().stream().filter(c->c.key()==Key.STALLED_TRAINING_JOBS).findFirst().orElseThrow();
        assertThat(OffsetDateTime.parse(card.drilldown().query().get("stalledBefore"))).isEqualTo(overview.asOf().minusSeconds(900));
        assertThat(overview.consistency()).isEqualTo("LIVE_PER_CARD");
    }
    @Test void invalidPeriodNeverQueriesCounts() {
        when(permissions.permissions(1L)).thenReturn(Set.of(AdminPermission.support_read));
        var now=OffsetDateTime.now();
        assertThatThrownBy(()->service.get(1L,now,now.minusDays(1)))
                .isInstanceOf(com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException.class);
        verifyNoInteractions(reader);
    }
}
