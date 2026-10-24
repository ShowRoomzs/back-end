package showroomz.domain.member.brand.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.common.entity.AdminMemo;
import showroomz.domain.member.common.service.AdminMemoPolicy;

import java.time.LocalDateTime;

/**
 * 브랜드 관리자 메모(§52-2 · 52 설계서 0-7) — <b>조치하지 않은 판단의 기록</b> · 누적 기입 · 항목당 300자 · 작성자 본인 24시간 내 수정 · 삭제 ·
 * 브랜드 비노출. 삭제는 soft — 삭제 본문 보관 여부가 미정이라 하드 삭제하면 소급할 수 없다.
 *
 * <p>기존 {@code MARKET.admin_memo}(덮어쓰기)는 기획 제외 컨트롤러의 컬럼이라 읽지도 옮기지도 않는다.
 */
@Entity
@Table(name = "brand_admin_memo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandAdminMemo implements AdminMemo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "memo_id")
    private Long id;

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "author_name", nullable = false, length = 64)
    private String authorName;

    @Column(name = "content", nullable = false, length = AdminMemoPolicy.MAX_LENGTH)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static BrandAdminMemo write(Long marketId, Long authorId, String authorName, String content,
                                       LocalDateTime now) {
        BrandAdminMemo memo = new BrandAdminMemo();
        memo.marketId = marketId;
        memo.authorId = authorId;
        memo.authorName = authorName;
        memo.content = content;
        memo.createdAt = now;
        return memo;
    }

    public void edit(String content, LocalDateTime now) {
        this.content = content;
        this.modifiedAt = now;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }
}
