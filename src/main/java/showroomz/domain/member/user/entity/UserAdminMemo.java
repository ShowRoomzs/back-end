package showroomz.domain.member.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 소비자 관리자 메모(§25-6) — 조치하지 않은 판단의 기록 · 누적 기입 · 항목당 300자 · 작성자 본인 24시간 내 수정 · 삭제 · 소비자 비노출.
 * 삭제는 soft — 삭제 본문 보관 여부가 미정이라 하드 삭제하면 소급할 수 없다.
 *
 * <p>열 길이가 300이 아니라 500인 것은 옛 단일 메모({@code USERS.ADMIN_MEMO} · 500자)를 자르지 않고 옮기기 위해서다(V204). 신규 입력은
 * 서버가 300자로 검증한다. 옮겨 온 행은 작성자가 없어({@code author_id = null}) 누구도 고치지 못한다.
 */
@Entity
@Table(name = "user_admin_memo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAdminMemo {

    public static final int MAX_LENGTH = 300;
    public static final Duration EDIT_WINDOW = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "memo_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 운영자 seller_id — 옛 메모 이관 행만 null. */
    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "author_name", nullable = false, length = 64)
    private String authorName;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static UserAdminMemo write(Long userId, Long authorId, String authorName, String content, LocalDateTime now) {
        UserAdminMemo memo = new UserAdminMemo();
        memo.userId = userId;
        memo.authorId = authorId;
        memo.authorName = authorName;
        memo.content = content;
        memo.createdAt = now;
        return memo;
    }

    public boolean isAuthor(Long operatorId) {
        return authorId != null && authorId.equals(operatorId);
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
