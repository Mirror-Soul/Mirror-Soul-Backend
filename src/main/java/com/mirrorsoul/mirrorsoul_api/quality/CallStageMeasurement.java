package com.mirrorsoul.mirrorsoul_api.quality;

import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity @Table(name="call_stage_measurements",uniqueConstraints=@UniqueConstraint(columnNames={"call_id","turn_key","stage"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CallStageMeasurement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="call_id",nullable=false) private VideoCall call;
    @Column(name="turn_key",nullable=false,length=64) private String turnKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private CallQualityDtos.Stage stage;
    @Column(name="latency_ms",nullable=false) private long latencyMs;
    @Column(name="measured_at",nullable=false) private LocalDateTime measuredAt;
    public CallStageMeasurement(VideoCall call,String turn,CallQualityDtos.Stage stage,long ms,LocalDateTime at) {
        this.call=call;turnKey=turn;this.stage=stage;latencyMs=ms;measuredAt=at;
    }
}
