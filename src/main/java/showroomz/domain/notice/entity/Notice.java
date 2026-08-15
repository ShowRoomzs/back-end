package showroomz.domain.notice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.notice.type.NoticeStatus;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "NOTICE",
        indexes = {
                // 목록 정렬은 중요 고정 상단 + 등록일 최신순이다 (기획 §20-3)
                @Index(name = "idx_notice_status_pinned_created", columnList = "STATUS, IS_PINNED, CREATED_AT")
        }
)
public class Notice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NOTICE_ID")
    private Long id;

    @Column(name = "TITLE", nullable = false)
    private String title;

    @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private NoticeStatus status;

    /** 중요 — 상태가 아니라 분류다. 목록 상단에 고정 노출된다 (기획 §20-1) */
    @Column(name = "IS_PINNED", nullable = false)
    private boolean pinned;

    /** 작성자(운영자) ID — 목록에는 두지 않고 수정 페이지에서만 확인한다 (기획 §20-3) */
    @Column(name = "AUTHOR_ID")
    private Long authorId;

    /** 게시 종료 일시 — 재게시하면 비운다 (기획 §20-4) */
    @Column(name = "ENDED_AT")
    private LocalDateTime endedAt;

    @Builder
    public Notice(String title, String content, boolean pinned, Long authorId) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.authorId = authorId;
        // 등록 = 즉시 게시. 초안 상태를 두지 않는다 (기획 §20-2)
        this.status = NoticeStatus.PUBLISHED;
    }

    /**
     * 저장은 상태를 건드리지 않는다 (기획 §20-2).
     * 게시 종료 상태에서 저장해도 재게시되지 않으며, 재게시는 목록의 게시 버튼에서만 일어난다.
     */
    public void update(String title, String content, boolean pinned) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
    }

    public boolean isPublished() {
        return this.status == NoticeStatus.PUBLISHED;
    }
}
