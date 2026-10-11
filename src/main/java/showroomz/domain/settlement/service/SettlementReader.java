package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.repository.SettlementClawbackRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 정산 → 다른 모듈(44 어드민 설계서 8-5) — 정산이 생긴 공구는 따로 계산하지 않고 이 값을 그대로 쓴다(성과 관리 · 「판매 리워드 실지급」).
 * 탈퇴 반려(「정산이 끝나지 않았습니다」)는 {@link #hasUnsettledForCreator} · {@link #hasUnsettledForMarket}으로 판정한다 — 호출처는
 * 탈퇴 API 가 생길 때 붙는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementReader {

    private final SettlementRepository settlementRepository;
    private final SettlementClawbackRepository clawbackRepository;

    /** 공구의 정산 요약 — 정산 전이면 empty. 금액은 정산 행의 스냅샷(합의 후 · 차감 반영 후)이다. */
    public record SettlementSummary(Long settlementId, String settlementNumber, SettlementStatus status,
                                    long confirmedSalesAmount, long rewardAmount, long rewardAfterClawback,
                                    long brandPayoutAmount, long creatorPayoutAmount, LocalDateTime confirmedAt,
                                    LocalDateTime paidAt) {
    }

    public Optional<SettlementSummary> findByGroupBuy(Long groupBuyId) {
        return settlementRepository.findByGroupBuyId(groupBuyId).map(SettlementReader::summary);
    }

    /** 인플루언서 — 지급 완료 전 정산 또는 회수 대기(PENDING) 차감이 있으면 true. */
    public boolean hasUnsettledForCreator(Long creatorId) {
        return settlementRepository.existsUnpaidForCreator(creatorId)
                || clawbackRepository.existsPendingForCreator(creatorId);
    }

    /** 브랜드(마켓) — 지급 완료 전 정산 또는 회수 대기(PENDING) 차감이 있으면 true. */
    public boolean hasUnsettledForMarket(Long marketId) {
        return settlementRepository.existsUnpaidForMarket(marketId)
                || clawbackRepository.existsPendingForMarket(marketId);
    }

    private static SettlementSummary summary(Settlement s) {
        return new SettlementSummary(s.getId(), s.getSettlementNumber(), s.getStatus(), s.getConfirmedSalesAmount(),
                s.getRewardAmount(), s.getRewardAfterClawback(), s.getBrandPayoutAmount(), s.getCreatorPayoutAmount(),
                s.getConfirmedAt(), s.getPaidAt());
    }
}
