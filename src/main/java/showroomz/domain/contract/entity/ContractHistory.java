package showroomz.domain.contract.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractEventType;

import java.time.LocalDateTime;

/**
 * 계약 이력 — append-only. 화면 「이력」 카드가 그대로 읽는다.
 *
 * <p>수정 메서드를 두지 않는다(설계서 1-6). 이력이 비면 분쟁에서 근거가 없다는 뜻이라
 * 전이와 같은 트랜잭션에서 append하고 이벤트 리스너로 분리하지 않는다(설계서 3-5).
 *
 * <p>{@code actorDisplayName}은 서피스와 무관하게 같은 값(브랜드명·쇼룸명)에만 쓴다.
 * 운영자 호칭은 파트너 「어드민」 · 스튜디오 「운영자」 · 어드민 실명으로 갈리므로(§25-9)
 * 서버는 {@code actorType}만 내리고 문구는 읽는 서피스가 고른다.
 */
@Entity
@Table(name = "contract_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ContractHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private ContractEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private ContractActorType actorType;

    @Column(name = "actor_id")
    private Long actorId;

    /** 스냅샷 — 브랜드명·쇼룸명. 지금 이름이 바뀌어도 이력의 이름은 그대로 남아야 한다. */
    @Column(name = "actor_display_name", length = 100)
    private String actorDisplayName;

    @Column(name = "detail", length = 2000)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    public static ContractHistory of(Contract contract, ContractEventType eventType,
                                     ContractActorType actorType, Long actorId,
                                     String actorDisplayName, String detail, LocalDateTime occurredAt) {
        return ContractHistory.builder()
                .contract(contract)
                .eventType(eventType)
                .actorType(actorType)
                .actorId(actorId)
                .actorDisplayName(actorDisplayName)
                .detail(detail)
                .occurredAt(occurredAt)
                .build();
    }
}
