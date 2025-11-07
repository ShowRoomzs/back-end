package showroomz.event.entitiy;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class) // 생성/수정 시간 자동화
@Table(name = "banner")
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "banner_id")
    private Long id; 

    @Column(nullable = false, length = 100)
    private String title; 

    @Column(length = 255) // subtitle은 없을 수 있으므로 nullable (default)
    private String subtitle;

    // 요청 파라미터로 사용될 배너 위치
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private BannerPosition position; // HOME_MAIN, EVENT_LIST_TOP

    // 배너 정렬 순서 (낮은 숫자 -> 높은 우선순위)
    @Column(nullable = false)
    private Integer sortOrder = 100; // 기본값 설정

    // 배너 노출(활성화) 여부
    @Column(nullable = false)
    private boolean isActive = true;

    // 배너 게시 시작 시간 (null이면 즉시)
    private LocalDateTime startedAt;

    // 배너 게시 종료 시간 (null이면 무기한)
    private LocalDateTime endedAt;

    // JSON의 'image' 객체 매핑
    @Embedded
    private BannerImage image;

    // JSON의 'link' 객체 매핑
    @Embedded
    private BannerLink link;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}