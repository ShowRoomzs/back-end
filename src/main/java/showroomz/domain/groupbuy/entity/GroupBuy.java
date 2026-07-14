package showroomz.domain.groupbuy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 공동구매(공구) — 계약 체결완료 시 시스템이 생성(계약 1:1 공구).
 * 공구가·대상 상품·리워드율은 복사하지 않고 계약(상품 항목)을 참조한다(계약은 체결 후 불변이라 정합).
 * 기간(시작·종료일)은 상태 전이 배치(자동 시작/종료) 편의를 위해 생성 시점에 계약에서 상속해 보관.
 * <ul>
 *   <li>준비완료 = 운영자 승인 AND 공구 게시물 등록 완료 (운영자 통제 지점 ② — 오픈 승인 시 문구 점검 결합)</li>
 *   <li>진행중 = 준비완료 AND 시작일 도래(자동). 진행중에서만 소비자 구매 가능</li>
 *   <li>공구 종료 → 게시물 자동 종료·장바구니 항목 자동 제거 트리거</li>
 *   <li>정산 이체완료 시 정산완료</li>
 * </ul>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "group_buy")
public class GroupBuy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_buy_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false, unique = true)
    private Contract contract;

    /** 공구가 진행되는 쇼룸(인플루언서) — 쇼룸은 인플루언서 계정 1:1 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private Creator creator;

    /** 배송·정산 상대 브랜드 — 조회 편의를 위한 비정규화 참조 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GroupBuyStatus status = GroupBuyStatus.PREPARING;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 준비완료(오픈) 승인 운영자 식별자 */
    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    public GroupBuy(Contract contract, Creator creator, Seller seller, LocalDate startDate, LocalDate endDate) {
        this.contract = contract;
        this.creator = creator;
        this.seller = seller;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = GroupBuyStatus.PREPARING;
    }

    /** 운영자 오픈 승인 → 준비완료. 공구 게시물 등록 완료가 전제(서비스 검증) */
    public void approve(String adminIdentifier) {
        this.status = GroupBuyStatus.READY;
        this.approvedBy = adminIdentifier;
        this.approvedAt = LocalDateTime.now();
    }

    /** 시작일 도래(자동) → 진행중 */
    public void start() {
        this.status = GroupBuyStatus.IN_PROGRESS;
    }

    /** 기간 만료 또는 운영자 조기 마감 → 종료 */
    public void close() {
        this.status = GroupBuyStatus.CLOSED;
    }

    /** 정산 이체완료 → 정산완료 */
    public void settle() {
        this.status = GroupBuyStatus.SETTLED;
    }

    /** 운영자 취소(준비중·준비완료·진행중) — 미확정 주문 일괄 환불 유발 */
    public void cancel(String reason) {
        this.status = GroupBuyStatus.CANCELED;
        this.cancelReason = reason;
    }

    /** 진행중에서만 소비자 구매 가능 */
    public boolean isPurchasable() {
        return status == GroupBuyStatus.IN_PROGRESS;
    }

    public boolean isActive() {
        return status == GroupBuyStatus.PREPARING
                || status == GroupBuyStatus.READY
                || status == GroupBuyStatus.IN_PROGRESS;
    }
}
