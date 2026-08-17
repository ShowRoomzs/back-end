package showroomz.domain.recentSearch.entitiy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.recentSearch.type.RecentSearchType;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private RecentSearchType type = RecentSearchType.TERM;

    @Column(nullable = false, length = 255)
    private String term;

    /**
     * SHOWROOM 행이 가리키는 쇼룸. 아바타·이름·핸들은 표시할 때 이 쇼룸에서 그대로 읽는다
     * (쇼룸이 이름을 바꾸면 최근 검색 목록도 따라 바뀐다). TERM 행에서는 null이다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private Creator creator;

    // @CreationTimestamp 제거: 클라이언트 시간을 우선 사용
    @Column(name = "created_at", nullable = false, updatable = true)
    private Instant createdAt;

    /**
     * 검색어 기록 생성 (팩토리 메서드)
     * - createdAt이 null이면 현재 시간 사용
     */
    public static RecentSearch create(Users user, String term, Instant createdAt) {
        RecentSearch recentSearch = new RecentSearch();
        recentSearch.user = user;
        recentSearch.type = RecentSearchType.TERM;
        recentSearch.term = term;
        recentSearch.createdAt = createdAt != null ? createdAt : Instant.now();
        return recentSearch;
    }

    /**
     * 쇼룸 기록 생성 — 검색 결과에서 쇼룸을 눌러 들어간 경우.
     * term에는 저장 시점의 쇼룸명을 남겨 두지만, 화면 표시는 creator에서 읽는다.
     */
    public static RecentSearch createShowroom(Users user, Creator creator, Instant createdAt) {
        RecentSearch recentSearch = new RecentSearch();
        recentSearch.user = user;
        recentSearch.type = RecentSearchType.SHOWROOM;
        recentSearch.creator = creator;
        recentSearch.term = creator.getShowroomName() != null
                ? creator.getShowroomName()
                : creator.getShowroomAddress();
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

    /** 같은 쇼룸을 다시 눌렀을 때 — 시간과 함께 쇼룸명 스냅샷도 최신으로 맞춘다. */
    public void refreshShowroomTerm() {
        if (this.creator != null && this.creator.getShowroomName() != null) {
            this.term = this.creator.getShowroomName();
        }
    }
}
