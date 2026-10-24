package showroomz.domain.member.brand.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.brand.type.BrandAdminActorType;
import showroomz.domain.member.brand.type.BrandAdminEventType;

import java.time.LocalDateTime;

/**
 * 브랜드 처리 이력 — <b>이 모듈이 일으킨 사건만</b> 저장한다(52 설계서 0-6). 가입 · 입점 승인 · 변경 요청은 원래 테이블에서 읽어 합친다.
 * 처리자 이름은 스냅샷이다 — 뒤에 계정이 사라져도 「김운영」이 남는다. 파기 대상이 아니다.
 */
@Entity
@Table(name = "brand_admin_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandAdminHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private BrandAdminEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private BrandAdminActorType actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name", length = 64)
    private String actorName;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "ref_type", length = 32)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static BrandAdminHistory of(Long marketId, BrandAdminEventType eventType, BrandAdminActorType actorType,
                                       Long actorId, String actorName, String detail, String refType, Long refId,
                                       LocalDateTime now) {
        BrandAdminHistory history = new BrandAdminHistory();
        history.marketId = marketId;
        history.eventType = eventType;
        history.actorType = actorType;
        history.actorId = actorId;
        history.actorName = actorName;
        history.detail = detail == null || detail.length() <= 500 ? detail : detail.substring(0, 500);
        history.refType = refType;
        history.refId = refId;
        history.createdAt = now;
        return history;
    }
}
