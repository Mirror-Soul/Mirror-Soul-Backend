package com.mirrorsoul.mirrorsoul_api.safety;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyDtos.*;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class SafetyReportValidationTest {
    private final SafetyReportRepository reports=mock(SafetyReportRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    private final VideoCallRepository calls=mock(VideoCallRepository.class);
    private final ChatMessageRepository messages=mock(ChatMessageRepository.class);
    private final ChatRoomMemberRepository memberships=mock(ChatRoomMemberRepository.class);
    private final SafetyReportService service=new SafetyReportService(reports,mock(SafetyDecisionRepository.class),mock(SafetyAppealRepository.class),
        users,calls,messages,memberships,new SafetyPolicy(10,50,90,7,14));
    private User user(long id) { return User.builder().id(id).uuid(UUID.randomUUID()).status(UserStatus.ACTIVE).build(); }
    private User target() {
        User target=user(2L);when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(users.findByUuid(target.getUuid())).thenReturn(Optional.of(target));return target;
    }
    @Test void cannotAttachOtherMembersCall() {
        User target=target();Clone clone=Clone.builder().user(target).build();
        when(calls.findByIdWithParticipants(42L)).thenReturn(Optional.of(VideoCall.builder().user(user(3L)).clone(clone).roomId("call").build()));
        assertThatThrownBy(() -> service.submit(1L,new Submit(target.getUuid(),ReportType.THREATS,"reason",42L,null,"key"))).isInstanceOf(GeneralException.class);
        verify(reports,never()).saveAndFlush(any());
    }
    @Test void cannotAttachMessagesFromAChatRoomReporterNeverJoined() {
        User target=target();ChatRoom room=mock(ChatRoom.class);when(room.getId()).thenReturn(7L);
        when(messages.findById(42L)).thenReturn(Optional.of(ChatMessage.builder().sender(target).chatRoom(room)
            .content("private").createdAt(java.time.LocalDateTime.now()).build()));
        assertThatThrownBy(() -> service.submit(1L,new Submit(target.getUuid(),ReportType.HARASSMENT,"reason",null,42L,"key"))).isInstanceOf(GeneralException.class);
        verify(reports,never()).saveAndFlush(any());
    }
    @Test void hourlyLimitRejectsExcessiveReports() {
        User target=target();when(reports.countByReporterIdAndCreatedAtAfter(eq(1L),any())).thenReturn(10L);
        assertThatThrownBy(() -> service.submit(1L,new Submit(target.getUuid(),ReportType.SPAM,"reason",null,null,"key")))
            .isInstanceOf(GeneralException.class).extracting(e -> ((GeneralException)e).getCode().getCode()).isEqualTo("SAFETY_4290");
        verify(reports,never()).saveAndFlush(any());
    }
}
