package showroomz.domain.contract.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.contract.type.ContractActorType;

import java.time.LocalDateTime;

/**
 * [서명 안내 다시 받기] 요청.
 *
 * <p>요청은 발송이 아니다 — 이 행이 생겨도 계약 상태는 변하지 않는다.
 * 실제 재발송은 어드민이 모두싸인에서 한다(설계서 1-6).
 *
 * <p>횟수 제한·SLA는 미정(§28-8 D #7)이라 만들지 않되, 미처리 요청이 이미 있으면
 * 새 행을 만들지 않아 어드민 큐에 같은 계약이 여러 줄 쌓이는 것만 막는다(설계서 3-6).
 */
@Entity
@Table(name = "contract_resend_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ContractResendRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_resend_request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(name = "requester_type", nullable = false, length = 16)
    private ContractActorType requesterType;

    @Column(name = "requester_id")
    private Long requesterId;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "handled_by")
    private Long handledBy;

    public static ContractResendRequest of(Contract contract, ContractActorType requesterType,
                                           Long requesterId, LocalDateTime requestedAt) {
        return ContractResendRequest.builder()
                .contract(contract)
                .requesterType(requesterType)
                .requesterId(requesterId)
                .requestedAt(requestedAt)
                .build();
    }

    public boolean isHandled() {
        return handledAt != null;
    }
}
