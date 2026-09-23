package showroomz.domain.contract.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.contract.type.SecondaryUsePeriodType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 계약 본체 — 파트너센터·스튜디오·어드민 3서피스가 공유하는 하나의 객체(§25-0).
 *
 * <p>작성중 계약은 필수값이 전부 비어 있는 행이므로 대부분의 컬럼이 nullable이다(설계서 0-3).
 * 「필수」 판정은 DB가 아니라 검토 요청 전이 시점의 애플리케이션 검증이 한다.
 *
 * <p>상태 전이에 딸린 부수 필드는 이 클래스가 채우고, 전이 자체의 경합 차단은
 * 리포지토리의 조건부 UPDATE가 한다(설계서 3-3). 둘은 같은 트랜잭션 안에 있다.
 */
@Entity
@Table(name = "contract")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Contract extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_id")
    private Long id;

    /** CTR-YYYYMMDD-NNN · 검토 요청 시점에 부여된다(설계서 1-7). 작성중에는 null이다. */
    @Column(name = "contract_number", length = 30, unique = true)
    private String contractNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    /** 계약 상대 — 작성중엔 미선택 가능. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private Creator creator;

    /** 작성 시점의 연결 근거 · 「스레드 열기」 딥링크의 출처. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id")
    private Connection connection;

    @Column(name = "title", length = 40)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ContractStatus status;

    @Column(name = "group_buy_start_at")
    private LocalDateTime groupBuyStartAt;

    @Column(name = "group_buy_end_at")
    private LocalDateTime groupBuyEndAt;

    @Column(name = "fixed_fee_amount")
    private Integer fixedFeeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "fixed_fee_trigger", length = 32)
    private FixedFeeTrigger fixedFeeTrigger;

    /** 고지 확인 체크 시각 — boolean이 아니라 시각으로 받는다(설계서 1-1). */
    @Column(name = "fixed_fee_notice_agreed_at")
    private LocalDateTime fixedFeeNoticeAgreedAt;

    @Column(name = "fixed_fee_paid_at")
    private LocalDateTime fixedFeePaidAt;

    @Column(name = "content_feed_count")
    private Integer contentFeedCount;

    @Column(name = "content_reels_count")
    private Integer contentReelsCount;

    @Column(name = "content_story_count")
    private Integer contentStoryCount;

    @Column(name = "content_due_date")
    private LocalDate contentDueDate;

    @Column(name = "secondary_use_allowed")
    private Boolean secondaryUseAllowed;

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_use_period_type", length = 16)
    private SecondaryUsePeriodType secondaryUsePeriodType;

    @Column(name = "secondary_use_months")
    private Integer secondaryUseMonths;

    @Column(name = "brand_pre_review")
    private Boolean brandPreReview;

    @Column(name = "note", length = 500)
    private String note;

    /** 표준 조항 스냅샷 — 검토 요청 시 현행 버전으로 고정된다(설계서 1-6). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clause_version_id")
    private ContractClauseVersion clauseVersion;

    /** 통과된 경고 W1~W6 CSV(설계서 2-3). */
    @Column(name = "warning_flags", length = 128)
    private String warningFlags;

    @Column(name = "review_requested_at")
    private LocalDateTime reviewRequestedAt;

    @Column(name = "review_approved_at")
    private LocalDateTime reviewApprovedAt;

    @Column(name = "review_rejected_at")
    private LocalDateTime reviewRejectedAt;

    @Column(name = "reject_reason_code", length = 64)
    private String rejectReasonCode;

    @Column(name = "reject_reason_detail", length = 1000)
    private String rejectReasonDetail;

    @Column(name = "signature_requested_at")
    private LocalDateTime signatureRequestedAt;

    /** 만료 판단의 유일한 기준(§25-3). 스케줄러가 아니라 어드민이 본다. */
    @Column(name = "signature_deadline_at")
    private LocalDateTime signatureDeadlineAt;

    @Column(name = "brand_signed_at")
    private LocalDateTime brandSignedAt;

    @Column(name = "creator_signed_at")
    private LocalDateTime creatorSignedAt;

    /** 서명 값이 사람 손으로 들어오므로 「언제 기준」인지를 함께 보관한다(§25-3 #2). */
    @Column(name = "signature_as_of")
    private LocalDateTime signatureAsOf;

    @Column(name = "creator_viewed_at")
    private LocalDateTime creatorViewedAt;

    @Column(name = "concluded_at")
    private LocalDateTime concludedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_actor_type", length = 16)
    private ContractActorType closeActorType;

    @Column(name = "close_reason_code", length = 64)
    private String closeReasonCode;

    @Column(name = "close_reason_memo", length = 1000)
    private String closeReasonMemo;

    /** 계약 1건 = 공구 1건. 공구 생성 게이트가 이 값의 NULL 여부로 중복을 막는다(설계서 1-8). */
    @Column(name = "group_buy_id")
    private Long groupBuyId;

    /** 재작성 출처 — 작성 화면 상단이 출처를 밝힌다(§26-5). */
    @Column(name = "source_contract_id")
    private Long sourceContractId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** 배열 index를 sort_order로 쓰는 전체 교체(PUT 시맨틱) 대상이다(설계서 4-2). */
    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<ContractItem> items = new ArrayList<>();

    /** 빈 초안. 스레드 경유 진입이면 상대가 여기서 고정되고 이후 변경이 거부된다(§25-5-1). */
    public static Contract createDraft(Market market, Creator creator, Connection connection) {
        return Contract.builder()
                .market(market)
                .creator(creator)
                .connection(connection)
                .status(ContractStatus.DRAFT)
                .secondaryUseAllowed(true)
                .secondaryUsePeriodType(SecondaryUsePeriodType.FIXED)
                .secondaryUseMonths(12)
                .brandPreReview(false)
                .items(new ArrayList<>())
                .build();
    }

    public boolean isOwnedBy(Long marketId) {
        return market != null && market.getId().equals(marketId);
    }

    public boolean isEditable() {
        return status.isEditable();
    }

    /** 스레드 경유로 상대가 고정된 계약인지 — 고정된 상대는 PUT에서 바꿀 수 없다(§25-5-1). */
    public boolean isCounterpartyFixed() {
        return connection != null;
    }

    public void changeCounterparty(Creator creator, Connection connection) {
        this.creator = creator;
        this.connection = connection;
    }

    public void replaceItems(List<ContractItem> newItems) {
        this.items.clear();
        for (int i = 0; i < newItems.size(); i++) {
            ContractItem item = newItems.get(i);
            item.attachTo(this, i);
            this.items.add(item);
        }
    }

    /**
     * 재작성 출처를 남긴다(§26-5). 계약번호·상태·서명 이력·경고는 승계하지 않는다 —
     * 승계 대상은 {@code updateTerms}로 옮겨 담은 조건뿐이다.
     */
    public void markDuplicatedFrom(Long sourceContractId) {
        this.sourceContractId = sourceContractId;
    }

    /** 임시저장 — 부분 저장을 허용하고 형식 위반만 막는다(설계서 0-3). */
    public void updateTerms(String title,
                            LocalDateTime groupBuyStartAt, LocalDateTime groupBuyEndAt,
                            Integer fixedFeeAmount, FixedFeeTrigger fixedFeeTrigger,
                            LocalDateTime fixedFeeNoticeAgreedAt,
                            Integer contentFeedCount, Integer contentReelsCount, Integer contentStoryCount,
                            LocalDate contentDueDate,
                            Boolean secondaryUseAllowed, SecondaryUsePeriodType secondaryUsePeriodType,
                            Integer secondaryUseMonths, Boolean brandPreReview, String note) {
        this.title = title;
        this.groupBuyStartAt = groupBuyStartAt;
        this.groupBuyEndAt = groupBuyEndAt;
        this.fixedFeeAmount = fixedFeeAmount;
        this.fixedFeeTrigger = fixedFeeTrigger;
        this.fixedFeeNoticeAgreedAt = fixedFeeNoticeAgreedAt;
        this.contentFeedCount = contentFeedCount;
        this.contentReelsCount = contentReelsCount;
        this.contentStoryCount = contentStoryCount;
        this.contentDueDate = contentDueDate;
        this.secondaryUseAllowed = secondaryUseAllowed;
        this.secondaryUsePeriodType = secondaryUsePeriodType;
        this.secondaryUseMonths = secondaryUseMonths;
        this.brandPreReview = brandPreReview;
        this.note = note;
    }

    // ── 상태 전이에 딸린 부수 필드 ────────────────────────────────────────────

    /** 검토 요청 — 계약번호 부여·조항 버전 고정·경고 저장이 전이와 같은 트랜잭션에서 일어난다. */
    public void applyReviewRequested(String contractNumber, ContractClauseVersion clauseVersion,
                                     String warningFlags, LocalDateTime now) {
        if (this.contractNumber == null) {
            this.contractNumber = contractNumber;
        }
        this.clauseVersion = clauseVersion;
        this.warningFlags = warningFlags;
        this.reviewRequestedAt = now;
        this.reviewRejectedAt = null;
        this.rejectReasonCode = null;
        this.rejectReasonDetail = null;
        this.status = ContractStatus.REVIEW_PENDING;
    }

    /**
     * 검토 요청 취소 — 작성중으로 되돌린다. 종결이 아니고 사유도 받지 않는다(설계서 3-2).
     *
     * <p>계약번호는 반납하지 않는다. 이미 어드민 큐·통지에 나간 번호를 재사용하면
     * 같은 번호가 서로 다른 계약을 가리키게 된다.
     */
    public void applyReviewRequestCanceled() {
        this.reviewRequestedAt = null;
        this.status = ContractStatus.DRAFT;
    }

    /** 브랜드의 [계약 취소] — 항상 종결이다(설계서 3-2). */
    public void applyCanceled(String reasonCode, String memo, LocalDateTime now) {
        this.closedAt = now;
        this.closeActorType = ContractActorType.SELLER;
        this.closeReasonCode = reasonCode;
        this.closeReasonMemo = memo;
        this.status = ContractStatus.CANCELED;
    }

    /** 고정 지급비 [지급 완료 기록](B5a) — 되돌리는 경로는 만들지 않는다(설계서 미결 #4). */
    public void applyFixedFeePaid(LocalDateTime now) {
        this.fixedFeePaidAt = now;
    }

    public boolean hasFixedFee() {
        return fixedFeeAmount != null && fixedFeeAmount > 0;
    }

    /**
     * 공구 기간(일수) — 시작·종료 「일자」 기준의 양끝 포함 계산이다.
     * 화면의 「(17일)」과 하드 검증 H4(3~30일)가 같은 값을 봐야 하므로 한 곳에 둔다.
     */
    public Integer periodDays() {
        if (groupBuyStartAt == null || groupBuyEndAt == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(groupBuyStartAt.toLocalDate(), groupBuyEndAt.toLocalDate()) + 1;
    }

    public int totalContentCount() {
        return nullToZero(contentFeedCount) + nullToZero(contentReelsCount) + nullToZero(contentStoryCount);
    }

    /** 지급 의무 소멸 — 종결 3종에서 화면이 바뀌는 근거(설계서 4-5 obligationAlive). */
    public boolean isObligationAlive() {
        return status != ContractStatus.DECLINED
                && status != ContractStatus.EXPIRED
                && status != ContractStatus.CANCELED;
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
