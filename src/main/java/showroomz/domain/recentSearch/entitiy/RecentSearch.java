package showroomz.domain.recentSearch.entitiy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.user.entity.Users;

import java.time.Instant;

@Entity
@Table(name = "recent_search")
@Getter
@NoArgsConstructor
public class RecentSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recent_search_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(nullable = false, length = 255)
    private String term;

    // @CreationTimestamp 제거: 클라이언트 시간을 우선 사용
    @Column(name = "created_at", nullable = false, updatable = true)
    private Instant createdAt;

    /**
     * 최근 검색어 생성 (팩토리 메서드)
     * - createdAt이 null이면 현재 시간 사용
     */
    public static RecentSearch create(Users user, String term, Instant createdAt) {
        RecentSearch recentSearch = new RecentSearch();
        recentSearch.user = user;
        recentSearch.term = term;
        recentSearch.createdAt = createdAt != null ? createdAt : Instant.now();
        return recentSearch;
    }

    /**
     * 타임스탬프를 전달받은 시간으로 업데이트
     * - createdAt이 null이면 현재 시간으로 설정
     */
    public void updateTimestamp(Instant createdAt) {
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }
}