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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "clone_personality_tags",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_clone_personality_tags_clone_content",
                        columnNames = {"clone_id", "content"}
                ),
                @UniqueConstraint(
                        name = "uk_clone_personality_tags_clone_order",
                        columnNames = {"clone_id", "display_order"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClonePersonalityTag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "clone_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_clone_personality_tags_clone")
    )
    private Clone clone;

    @Column(nullable = false, length = 20)
    private String content;

    @Column(name = "display_order", nullable = false)
    private Byte displayOrder;
}
