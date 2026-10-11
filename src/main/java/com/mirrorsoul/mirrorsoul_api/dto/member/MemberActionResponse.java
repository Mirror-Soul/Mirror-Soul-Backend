package com.mirrorsoul.mirrorsoul_api.dto.member;
import com.mirrorsoul.mirrorsoul_api.domain.MemberActionHistory;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import java.time.LocalDateTime;
public record MemberActionResponse(Long id, Long memberId, Long actorId, MemberAction action,
        MemberReasonCode reasonCode, UserStatus beforeStatus, UserStatus afterStatus,
        Long version, LocalDateTime actedAt) {
    public static MemberActionResponse from(MemberActionHistory h) {
        return new MemberActionResponse(h.getId(), h.getMemberId(), h.getActorId(), h.getAction(),
                h.getReasonCode(), h.getBeforeStatus(), h.getAfterStatus(), h.getResultVersion(), h.getActedAt());
    }
}
