package com.mirrorsoul.mirrorsoul_api.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.dto.member.*;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
class AdminMemberServiceTest {
    private final UserRepository users=mock(UserRepository.class);
    private final MemberActionHistoryRepository history=mock(MemberActionHistoryRepository.class);
    private final MemberDeletionService deletion=mock(MemberDeletionService.class);
    private final AdminMemberService service=new AdminMemberService(users,history,deletion);
    private User user(Long id,UserRole role) {
        return User.builder().id(id).uuid(UUID.randomUUID()).email("a@example.com")
            .passwordHash("hash").role(role).status(UserStatus.ACTIVE).build();
    }
    private void actors(UserRole role,User target) {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L,role)));
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(history.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }
    private MemberActionRequest request(MemberAction action,MemberReasonCode reason,long version) {
        return new MemberActionRequest(action,reason,version,"request-1");
    }
    @Test void staleVersionCannotSuspend() {
        User target=user(2L,UserRole.USER); actors(UserRole.ADMIN,target);
        assertThatThrownBy(() -> service.act(1L,2L,request(MemberAction.SUSPEND,MemberReasonCode.SECURITY_RISK,9)))
            .isInstanceOf(GeneralException.class);
        assertThat(target.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(history,never()).saveAndFlush(any());
    }
    @Test void duplicateRequestReturnsOriginalResultEvenAfterLaterChanges() {
        User target=user(2L,UserRole.USER); actors(UserRole.ADMIN,target);
        MemberActionHistory original=new MemberActionHistory(2L,1L,MemberAction.SUSPEND,MemberReasonCode.SECURITY_RISK,
            UserStatus.ACTIVE,UserStatus.SUSPENDED,0L,1L,"request-1",LocalDateTime.now());
        when(history.findByMemberIdAndIdempotencyKey(2L,"request-1")).thenReturn(Optional.of(original));
        assertThat(service.act(1L,2L,request(MemberAction.SUSPEND,MemberReasonCode.SECURITY_RISK,0)).version()).isEqualTo(1L);
        verify(users,never()).flush();
        verify(history,never()).saveAndFlush(any());
        assertThatThrownBy(() -> service.act(1L,2L,request(MemberAction.WITHDRAW,MemberReasonCode.USER_REQUEST,0)))
            .isInstanceOf(GeneralException.class);
    }
    @Test void onlySuperAdminCanConfirmWithdrawalAndQueueDeletion() {
        User target=user(2L,UserRole.USER);target.deactivate(LocalDateTime.now());actors(UserRole.ADMIN,target);
        var request=request(MemberAction.CONFIRM_WITHDRAWAL,MemberReasonCode.USER_REQUEST,0);
        assertThatThrownBy(() -> service.act(1L,2L,request)).isInstanceOf(GeneralException.class);
        actors(UserRole.SUPER_ADMIN,target);
        service.act(1L,2L,request);
        assertThat(target.getStatus()).isEqualTo(UserStatus.WITHDRAWAL_CONFIRMED);
        verify(deletion).enqueue(eq(target),any());
    }
    @Test void viewerCannotChangeAccount() {
        actors(UserRole.ADMIN_VIEWER,user(2L,UserRole.USER));
        assertThatThrownBy(() -> service.act(1L,2L,request(MemberAction.SUSPEND,MemberReasonCode.SECURITY_RISK,0)))
            .isInstanceOf(GeneralException.class);
    }
    @Test void reasonMustMatchActionAndSelfActionIsForbidden() {
        User target=user(2L,UserRole.USER);actors(UserRole.ADMIN,target);
        assertThatThrownBy(() -> service.act(1L,2L,request(MemberAction.SUSPEND,MemberReasonCode.USER_REQUEST,0)))
            .isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> service.act(1L,1L,request(MemberAction.SUSPEND,MemberReasonCode.SECURITY_RISK,0)))
            .isInstanceOf(GeneralException.class);
        assertThat(target.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
