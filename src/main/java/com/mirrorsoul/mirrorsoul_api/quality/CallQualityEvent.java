package com.mirrorsoul.mirrorsoul_api.quality;

import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import static com.mirrorsoul.mirrorsoul_api.quality.CallQualityDtos.*;
import com.mirrorsoul.mirrorsoul_api.quality.CallQualityDtos.Error;

@Entity @Table(name="call_quality_events")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CallQualityEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="call_id",nullable=false) private VideoCall call;
    @Column(name="event_key",unique=true,nullable=false,length=64) private String eventKey;
    @Column(name="request_hash",nullable=false,length=64) private String requestHash;
    @Column(name="turn_key",nullable=false,length=64) private String turnKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Step step;
    @Column(name="occurred_at",nullable=false) private LocalDateTime occurredAt;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
    @Enumerated(EnumType.STRING) @Column(name="connection_quality",length=20) private Connection connectionQuality;
    @Column(name="packet_loss_percent") private Double packetLossPercent;
    @Column(name="jitter_ms") private Double jitterMs;
    @Column(name="round_trip_ms") private Double roundTripMs;
    @Column(name="bitrate_kbps") private Double bitrateKbps;
    @Enumerated(EnumType.STRING) @Column(name="error_code",length=30) private Error errorCode;
    public CallQualityEvent(VideoCall call,String key,String hash,Event event) {
        this.call=call;eventKey=key;requestHash=hash;turnKey=QualitySupport.hash(event.turnId());
        step=event.step();occurredAt=QualitySupport.local(event.occurredAt());errorCode=event.errorCode();
        if(event.connection()!=null){
            var link=event.connection();connectionQuality=link.quality();packetLossPercent=link.packetLossPercent();
            jitterMs=link.jitterMs();roundTripMs=link.roundTripMs();bitrateKbps=link.bitrateKbps();
        }
    }
    public Link link() { return connectionQuality==null?null:new Link(connectionQuality,packetLossPercent,jitterMs,roundTripMs,bitrateKbps); }
}
