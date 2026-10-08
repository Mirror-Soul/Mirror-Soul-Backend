package com.mirrorsoul.mirrorsoul_api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "talk_log_revisions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TalkLogRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "talk_log_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_talk_log_revisions_talk_log"))
    private TalkLog talkLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "editor_user_id",
            foreignKey = @ForeignKey(name = "fk_talk_log_revisions_editor"))
    private User editor;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "previous_message", nullable = false, columnDefinition = "TEXT")
    private String previousMessage;

    @Column(name = "new_message", nullable = false, columnDefinition = "TEXT")
    private String newMessage;

    @Column(name = "edited_at", nullable = false)
    private LocalDateTime editedAt;

    private TalkLogRevision(TalkLog talkLog, User editor, String previousMessage) {
        this.talkLog = talkLog;
        this.editor = editor;
        this.revisionNumber = talkLog.getRevisionNumber();
        this.previousMessage = previousMessage;
        this.newMessage = talkLog.getMessage();
        this.editedAt = talkLog.getEditedAt();
    }

    public static TalkLogRevision record(TalkLog talkLog, User editor, String previousMessage) {
        return new TalkLogRevision(talkLog, editor, previousMessage);
    }
}
