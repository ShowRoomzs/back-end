package showroomz.domain.recentSearch.entitiy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = true)
    private Instant createdAt;

    /**
     * 최근 검색어 생성 (팩토리 메서드)
     */
    public static RecentSearch create(Users user, String term) {
        RecentSearch recentSearch = new RecentSearch();
        recentSearch.user = user;
        recentSearch.term = term;
        return recentSearch;
    }

    /**
     * 타임스탬프를 현재 시간으로 업데이트
     */
    public void updateTimestamp() {
        this.createdAt = Instant.now();
    }
}