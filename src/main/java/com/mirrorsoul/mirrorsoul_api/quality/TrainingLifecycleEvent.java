package com.mirrorsoul.mirrorsoul_api.quality;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity @Table(name="training_lifecycle_events")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class TrainingLifecycleEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="event_key",unique=true,nullable=false,length=64) private String eventKey;
    @Column(name="training_job_id",nullable=false,length=80) private String trainingJobId;
    @Column(name="request_hash",nullable=false,length=64) private String requestHash;
    @Column(name="event_sequence",nullable=false) private long sequence;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private TrainingDtos.State state;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private TrainingDtos.Stage stage;
    @Column(nullable=false) private boolean accepted;
    @Column(name="occurred_at",nullable=false) private LocalDateTime occurredAt;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt = LocalDateTime.now();
    public TrainingLifecycleEvent(String key,String job,String hash,TrainingDtos.Lifecycle event,boolean accepted) {
        eventKey=key;trainingJobId=job;requestHash=hash;sequence=event.sequence();state=event.state();
        stage=event.stage();occurredAt=QualitySupport.local(event.occurredAt());this.accepted=accepted;
    }
}
