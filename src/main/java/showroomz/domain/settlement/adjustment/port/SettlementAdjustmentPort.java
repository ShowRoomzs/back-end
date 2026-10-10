package showroomz.domain.settlement.adjustment.port;

import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.domain.settlement.type.SettlementEventType;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 조정 협의 → 정산 본체 포트(44 이슈 스레드 설계서 1-7) — 이 모듈이 정산에서 읽는 것 · 쓰는 것을 한 곳에 모은다. 구현은
 * {@code domain.settlement.service.SettlementAdjustmentPortAdapter}(어드민 설계서 4-2)다. 통합 테스트는 가짜 포트로 돈다(0-2).
 *
 * <p>세 쓰기 메서드는 <b>멱등</b> · 호출자 트랜잭션에 합류 · <b>DB 상태 전이만</b> 한다 — 분배(지급) 호출은 정산 배치다.
 */
public interface SettlementAdjustmentPort {

    /** 조정 가능 여부 판정에 필요한 정산 사실 — 정산이 없으면 empty. */
    Optional<AdjustableSettlement> find(Long settlementId);

    /** 금액을 넣으면 보이는 자동 계산(§42-1) — 원천징수 · 부가세 · 브랜드 수취액. 산식은 정산의 것이다. */
    AmountPreview preview(Long settlementId, long rewardAmount);

    /** 정산 확인 중 → 조정 협의(전액 보류 · 전 수취자 HELD). 0행이면 SETTLEMENT_ADJUSTMENT_WINDOW_CLOSED. */
    void holdForAdjustment(Long settlementId, Long adjustmentId, LocalDateTime now);

    /** 조정 협의 → 지급 예정 · 리워드 = 합의 금액 · 리워드 이하 행 재계산 · 보류 해제 · 지급 예정일 = 확정일 + N영업일. */
    void confirmByAgreement(Long settlementId, Long adjustmentId, long agreedRewardAmount, LocalDateTime now);

    /** 조정 협의 → 지급 예정 · 원래 금액 그대로 · 확정 시각 = 기한 다음 날 00:00. */
    void confirmByExpiry(Long settlementId, Long adjustmentId, LocalDateTime now);

    /** 제안 · 반대 · D-1 의 정산 이력. {@code actor}가 null 이면 시스템. */
    void recordEvent(Long settlementId, SettlementEventType type, SettlementParty actor, Long actorId, String detail);

    record AdjustableSettlement(Long settlementId, String settlementNumber, Long groupBuyId, String groupBuyTitle,
                                Long contractId, String contractNumber, Long marketId, Long creatorId,
                                boolean inReviewWindow, LocalDateTime reviewEndsAt,
                                long rewardAmount, long maxRewardAmount, long brandPayoutAmount) {
    }

    record AmountPreview(long rewardAmount, long rewardVatAmount, long withholdingAmount,
                         long creatorNetAmount, long brandPayoutAmount) {
    }
}
