package com.mirrorsoul.mirrorsoul_api.quality;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity @Table(name="training_retry_commands")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class TrainingRetryCommand {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="operation_key",nullable=false,unique=true,length=64) private String operationKey;
    @Column(name="source_job_id",nullable=false,unique=true,length=80) private String sourceJobId;
    @Column(name="new_job_id",nullable=false,length=80) private String newJobId;
    @Column(name="actor_id",nullable=false) private Long actorId;
    @Column(name="retry_count",nullable=false) private int retryCount;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt = LocalDateTime.now();
    public TrainingRetryCommand(String key,String original,String next,Long actor,int count) {
        operationKey=key;sourceJobId=original;newJobId=next;actorId=actor;retryCount=count;
    }
    public TrainingDtos.RetryResult result() { return new TrainingDtos.RetryResult(sourceJobId,newJobId,retryCount); }
}
