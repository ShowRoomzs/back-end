package showroomz.domain.member.brand.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.brand.type.BrandWithdrawalReason;
import showroomz.domain.member.brand.type.BrandWithdrawalRejectReason;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;

import java.time.LocalDateTime;

/**
 * 브랜드 탈퇴 신청 1건(52 설계서 0-5 · 1-3). 마켓당 열린(PENDING) 신청은 1건이다(운영 DB 생성 컬럼 {@code pending_key} UNIQUE · 엔티티
 * 미매핑). 반려 · 취소 뒤 재신청은 새 행이다 — 이력이 보존된다.
 *
 * <p>신청 시점의 조건 스냅샷은 두지 않는다 — 승인은 그 시점에 다시 판정한다(「신청 때는 0건이었다」로 바뀐 현실을 승인하지 않는다).
 * 상태 전이는 서비스가 행을 잠근 뒤({@code findForUpdate}) 상태를 다시 보고 한다.
 */
@Entity
@Table(name = "brand_withdrawal_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandWithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberWithdrawalStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 40)
    private BrandWithdrawalReason reason;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    /** 신청 시점 담당자명 — 파기 뒤에도 「신청자」가 남는다. */
    @Column(name = "requester_name", nullable = false, length = 64)
    private String requesterName;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "processed_by_name", length = 64)
    private String processedByName;

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_reason", length = 40)
    private BrandWithdrawalRejectReason rejectReason;

    @Column(name = "reject_note", length = 500)
    private String rejectNote;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    public static BrandWithdrawalRequest open(Long marketId, BrandWithdrawalReason reason, String message,
                                              String requesterName, LocalDateTime now) {
        BrandWithdrawalRequest request = new BrandWithdrawalRequest();
        request.marketId = marketId;
        request.status = MemberWithdrawalStatus.PENDING;
        request.reason = reason;
        request.message = message;
        request.requesterName = requesterName == null || requesterName.isBlank() ? "브랜드 담당자" : requesterName;
        request.requestedAt = now;
        return request;
    }

    public boolean isPending() {
        return status == MemberWithdrawalStatus.PENDING;
    }

    public void approve(Long operatorId, String operatorName, LocalDateTime now) {
        this.status = MemberWithdrawalStatus.APPROVED;
        this.processedAt = now;
        this.processedBy = operatorId;
        this.processedByName = operatorName;
    }

    public void reject(BrandWithdrawalRejectReason reason, String note, Long operatorId, String operatorName,
                       LocalDateTime now) {
        this.status = MemberWithdrawalStatus.REJECTED;
        this.rejectReason = reason;
        this.rejectNote = note;
        this.processedAt = now;
        this.processedBy = operatorId;
        this.processedByName = operatorName;
    }

    public void cancel(LocalDateTime now) {
        this.status = MemberWithdrawalStatus.CANCELED;
        this.canceledAt = now;
    }

    public String reasonLabel() {
        return reason == null ? "선택하지 않음" : reason.getLabel();
    }
}
