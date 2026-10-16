package showroomz.domain.member.creator.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 인플루언서 관리자 메모(§53-1 · §52-2 와 같은 규칙) — 조치하지 않은 판단의 기록 · 누적 기입 · 항목당 300자 · 작성자 본인 24시간 내
 * 수정 · 삭제 · 인플루언서 비노출. 삭제는 soft — 삭제 본문 보관 여부가 미정이라 하드 삭제하면 소급할 수 없다.
 */
@Entity
@Table(name = "creator_admin_memo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatorAdminMemo {

    public static final int MAX_LENGTH = 300;
    public static final Duration EDIT_WINDOW = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "memo_id")
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "author_name", nullable = false, length = 64)
    private String authorName;

    @Column(name = "content", nullable = false, length = MAX_LENGTH)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static CreatorAdminMemo write(Long creatorId, Long authorId, String authorName, String content,
                                         LocalDateTime now) {
        CreatorAdminMemo memo = new CreatorAdminMemo();
        memo.creatorId = creatorId;
        memo.authorId = authorId;
        memo.authorName = authorName;
        memo.content = content;
        memo.createdAt = now;
        return memo;
    }

    public boolean isAuthor(Long operatorId) {
        return authorId.equals(operatorId);
    }

    public boolean isWithinEditWindow(LocalDateTime now) {
        return !createdAt.plus(EDIT_WINDOW).isBefore(now);
    }

    public boolean isEditableBy(Long operatorId, LocalDateTime now) {
        return isAuthor(operatorId) && isWithinEditWindow(now);
    }

    public void edit(String content, LocalDateTime now) {
        this.content = content;
        this.modifiedAt = now;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }
}
