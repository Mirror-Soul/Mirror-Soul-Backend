package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.PushDevicePlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "push_devices",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_push_devices_installation",
                        columnNames = "installation_id"
                ),
                @UniqueConstraint(
                        name = "uk_push_devices_token",
                        columnNames = "push_token"
                )
        }
)
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDevice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "installation_id", nullable = false, length = 36)
    private UUID installationId;

    @Column(name = "push_token", nullable = false, length = 512)
    private String pushToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private PushDevicePlatform platform;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    public void register(
            User user,
            UUID installationId,
            String pushToken,
            PushDevicePlatform platform,
            LocalDateTime lastSeenAt) {
        this.user = user;
        this.installationId = installationId;
        this.pushToken = pushToken;
        this.platform = platform;
        this.enabled = true;
        this.lastSeenAt = lastSeenAt;
    }
}
