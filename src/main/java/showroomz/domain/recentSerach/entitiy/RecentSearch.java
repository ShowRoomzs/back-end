package showroomz.domain.recentSerach.entitiy;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.user.entity.Users;

import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recent_search")
@Getter
@NoArgsConstructor
public class RecentSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id; // "id": "search_uuid_12345"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user; // (필수 추가) 사용자 참조

    @Column(nullable = false, length = 255)
    private String term; // "term": "화이트 린넨 셔츠"

    @CreationTimestamp // 엔티티 생성 시 자동으로 현재 시간 매핑
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt; // "createdAt": "..." (UTC 기준 Instant 타입)

}