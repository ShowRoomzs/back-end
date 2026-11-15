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

/**
 * 소비자 → 쇼룸 차단(App Store 심사 1.2). 차단한 쇼룸은 그 소비자의 피드 · 쇼룸 목록 · 검색에서 빠진다(V224).
 * {@link CreatorFollow}와 같은 구성이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "creator_block",
        uniqueConstraints = @UniqueConstraint(name = "uk_creator_block", columnNames = {"user_id", "creator_id"})
)
public class CreatorBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "block_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private Creator creator;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public CreatorBlock(Users user, Creator creator) {
        this.user = user;
        this.creator = creator;
    }
}
