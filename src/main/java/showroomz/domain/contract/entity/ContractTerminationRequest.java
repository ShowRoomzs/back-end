package showroomz.domain.contract.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractTerminationRequestStatus;
import showroomz.domain.groupbuy.type.SuspensionReasonCode;

import java.time.LocalDateTime;

/**
 * 계약 해지 요청(1021 설계서 1-4 · 결정 노트 기-05 【잠정】) — 성립 뒤 · 공구 오픈 전에만 받는다.
 *
 * <p>요청은 해지가 아니다 — 이 행이 생겨도 계약은 「체결완료 · 공구 생성 대기」 그대로다. 운영자가 상대방 동의를 운영팀 채널에서
 * 확인한 뒤 [해지 처리]를 하면 그때 종결된다. 사유는 공구 중단 요청과 같은 4종({@link SuspensionReasonCode})이다(파트너 09 C4).
 */
@Entity
@Table(name = "contract_termination_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractTerminationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(name = "requester_type", nullable = false, length = 16)
    private ContractActorType requesterType;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 40)
    private SuspensionReasonCode reasonCode;

    @Column(name = "memo", length = 1000)
    private String memo;

    @Column(name = "group_buy_id")
    private Long groupBuyId;

    /** 상대방 운영팀 채널의 「동의 확인」 카드. */
    @Column(name = "consent_card_id")
    private Long consentCardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ContractTerminationRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    public static ContractTerminationRequest of(Contract contract, ContractActorType requesterType, Long requesterId,
                                                SuspensionReasonCode reasonCode, String memo, Long groupBuyId,
                                                LocalDateTime now) {
        ContractTerminationRequest r = new ContractTerminationRequest();
        r.contract = contract;
        r.requesterType = requesterType;
        r.requesterId = requesterId;
        r.reasonCode = reasonCode;
        r.memo = memo;
        r.groupBuyId = groupBuyId;
        r.status = ContractTerminationRequestStatus.PENDING;
        r.requestedAt = now;
        return r;
    }

    public boolean isPending() {
        return status == ContractTerminationRequestStatus.PENDING;
    }

    public void attachConsentCard(Long messageId) {
        this.consentCardId = messageId;
    }

    public void close(ContractTerminationRequestStatus target, Long actorId, LocalDateTime now) {
        this.status = target;
        this.closedAt = now;
        this.closedBy = actorId;
    }
}
