package showroomz.domain.member.creator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import showroomz.domain.member.user.entity.Users;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "CREATOR_FOLLOW",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_CREATOR_FOLLOW",
                        columnNames = {"USER_ID", "CREATOR_ID"}
                )
        }
)
public class CreatorFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FOLLOW_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CREATOR_ID", nullable = false)
    private Creator creator;

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * §24-7 라스트 터치 귀속 — 팔로우 직전 24시간 안에 마지막으로 본 이 쇼룸의 게시물. FK는 걸지 않는다.
     *
     * <p>알려진 구멍 하나 — 언팔로우하면 이 행이 삭제되므로 <b>과거에 귀속된 팔로우 수가 줄어든다.</b>
     * 인사이트는 시점 성과라 줄면 안 되지만, 정확히 하려면 팔로우 이벤트 로그가 필요하고 그것은
     * 쇼룸 관리(§22-4 팔로워 행동)와 공유 자산이라 그쪽 작업과 함께 설계한다.
     */
    @Column(name = "attributed_post_id")
    private Long attributedPostId;

    public CreatorFollow(Users user, Creator creator) {
        this.user = user;
        this.creator = creator;
    }

    /** 적재 직후 한 번만 채운다 */
    public void attributeTo(Long postId) {
        this.attributedPostId = postId;
    }
}
