package showroomz.domain.history.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.member.creator.type.CreatorApplicationStatus;

@Entity
@Table(name = "creator_application_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatorApplicationHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "creator_application_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_application_id", nullable = false)
    private CreatorApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false)
    private CreatorApplicationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private CreatorApplicationStatus newStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Builder
    public CreatorApplicationHistory(
            CreatorApplication application,
            CreatorApplicationStatus previousStatus,
            CreatorApplicationStatus newStatus,
            String reason) {
        this.application = application;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
    }
}
