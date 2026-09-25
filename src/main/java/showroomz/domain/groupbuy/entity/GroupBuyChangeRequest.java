package showroomz.domain.groupbuy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.groupbuy.type.ChangeRequestStatus;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.CreatorSuspensionReason;
import showroomz.domain.groupbuy.type.EarlyCloseReasonCode;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.SuspensionReasonCode;

import java.time.LocalDateTime;

/**
 * 중단 · 조기 마감 요청(설계서 1-6). 요청만으로 공구 상태는 바뀌지 않는다 — 승인 하나만 상태를 바꾼다.
 *
 * <p>「검토 중 추가 요청 불가」는 운영 DB의 생성 컬럼 {@code pending_group_buy_id} UNIQUE가 막는다.
 * 생성 컬럼은 엔티티에 매핑하지 않는다 — DB가 계산하는 값이고, 통합 테스트(H2)에는 존재하지 않는다.
 */
@Entity
@Table(name = "group_buy_change_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GroupBuyChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "change_request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_buy_id", nullable = false)
    private GroupBuy groupBuy;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 16)
    private ChangeRequestType requestType;

    /** 중단만 양측이 요청할 수 있다(제16조①). 조기 마감은 SELLER만. */
    @Enumerated(EnumType.STRING)
    @Column(name = "requester_type", nullable = false, length = 16)
    private GroupBuyActorType requesterType;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    /** VARCHAR로 둔다 — 인플루언서 중단 사유 코드가 미정이라(§33-1 #9) 스키마 변경 없이 받게 한다. */
    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(name = "memo", length = 1000)
    private String memo;

    /** C2·C4 문구 분기와 어드민 승인 모달(M2) 고지의 근거. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_at_request", nullable = false, length = 32)
    private GroupBuyStatus statusAtRequest;

    /** 어드민 B3 「요청 후 증가분」의 기준점. 판매 포트가 비어 있으면 null — 0이 아니다(설계서 0-6). */
    @Column(name = "sales_order_count_at_request")
    private Integer salesOrderCountAtRequest;

    @Column(name = "sales_amount_at_request")
    private Long salesAmountAtRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ChangeRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    public boolean isPending() {
        return status == ChangeRequestStatus.PENDING;
    }

    public boolean isRequestedBy(GroupBuyActorType actorType) {
        return requesterType == actorType;
    }

    /**
     * 사유 코드의 표시 라벨 — {@code reason_code}의 해석은 요청자와 요청 유형이 정한다. 모르는 코드는 null이다 —
     * 그럴듯한 문구를 지어내지 않는다. 3서피스와 이력 재구성(31 설계 6-2)이 이 메서드 하나를 쓴다.
     */
    public String reasonLabel() {
        try {
            if (requesterType == GroupBuyActorType.CREATOR) {
                return requestType == ChangeRequestType.SUSPEND
                        ? CreatorSuspensionReason.valueOf(reasonCode).getLabel() : null;
            }
            return requestType == ChangeRequestType.SUSPEND
                    ? SuspensionReasonCode.valueOf(reasonCode).getLabel()
                    : EarlyCloseReasonCode.valueOf(reasonCode).getLabel();
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    /** 운영자 판정(32 설계 7절) — 조건부 UPDATE가 경합을 막은 뒤 같은 트랜잭션의 응답이 새 값을 읽게 한다. */
    public void applyDecision(ChangeRequestStatus result, String decisionReason, Long operatorId, LocalDateTime now) {
        this.status = result;
        this.decisionReason = decisionReason;
        this.decidedAt = now;
        this.decidedBy = operatorId;
    }

    /** 판정 대상이 사라졌다 — 검토 중 요청을 남긴 채 공구가 기간 만료로 끝났다(설계서 1-6). */
    public void lapse(LocalDateTime now) {
        this.status = ChangeRequestStatus.LAPSED;
        this.decidedAt = now;
    }
}
