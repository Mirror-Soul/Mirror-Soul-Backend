package com.mirrorsoul.mirrorsoul_api.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "face_files")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaceFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_face_files_user"))
    private User user;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private FaceFile(User user, String fileUrl, String objectKey) {
        this.user = user;
        this.fileUrl = fileUrl;
        this.objectKey = objectKey;
    }

    public static FaceFile create(User user, String fileUrl, String objectKey) {
        return new FaceFile(user, fileUrl, objectKey);
    }

    public void updateFile(String fileUrl, String objectKey) {
        this.fileUrl = fileUrl;
        this.objectKey = objectKey;
    }
}
