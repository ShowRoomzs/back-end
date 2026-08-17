package showroomz.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import showroomz.domain.member.user.entity.Users;

import java.time.LocalDateTime;

/**
 * 게시물 좋아요 (구 {@code PostWishlist}).
 *
 * <p>기획 용어가 "좋아요"이므로 테이블·클래스·API 용어를 전부 그쪽으로 맞췄다. 상품 위시리스트와
 * 같은 말을 쓰면 인사이트의 "좋아요율"이 무엇의 비율인지 흐려진다.
 *
 * <p>{@code createdAt}이 <b>NOT NULL</b>이다 — 구 스키마는 nullable이라 "최근 30일 좋아요" 같은
 * 기간 집계가 원리적으로 불가능했다(§24-7 ① 반응 지표).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "post_like",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_post_like", columnNames = {"user_id", "post_id"})
        },
        indexes = {
                @Index(name = "idx_post_like_post_time", columnList = "post_id, created_at")
        }
)
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_like_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private Users user;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PostLike(Users user, Post post) {
        this.user = user;
        this.post = post;
    }
}
