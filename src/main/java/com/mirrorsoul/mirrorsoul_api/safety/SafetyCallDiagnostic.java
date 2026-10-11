package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.DiagnosticCode;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="safety_call_diagnostics",uniqueConstraints=@UniqueConstraint(columnNames={"call_id","event_key"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SafetyCallDiagnostic {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="call_id",nullable=false) private Long callId;
    @Column(name="event_key",nullable=false,length=100) private String eventKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private DiagnosticCode code;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    public SafetyCallDiagnostic(Long callId,String key,DiagnosticCode code,LocalDateTime now) {
        this.callId=callId;eventKey=key;this.code=code;createdAt=now;
    }
}
