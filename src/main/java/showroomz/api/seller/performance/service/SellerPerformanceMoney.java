package showroomz.api.seller.performance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.common.performance.dto.FigureState;
import showroomz.api.common.performance.dto.PerformanceFigure;
import showroomz.domain.performance.service.PerformanceFacts;
import showroomz.domain.performance.type.PerformanceStatus;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.service.SettlementCalculator;

import static showroomz.api.seller.performance.service.SellerPerformanceNumbers.div;
import static showroomz.api.seller.performance.service.SellerPerformanceNumbers.roas;

/**
 * 파트너 성과의 돈 · 값 상태 규칙(48 파트너 설계서 5절 표 · 정합 계획서 R4-4) — 정산액 · 리워드 · 총 집행비 · ROAS · 획득 단가가 집계 중
 * {@code PENDING_SETTLEMENT}, 조정 협의 {@code NEGOTIATING} + 원래 값, 합의 반영 {@code FINAL} + 원래 값이다. 원래 값(합의 전 정산액)은 정산
 * 계산기가 원래 리워드로 다시 낸 값이다 — 여기서 수수료 · 부가세를 따로 계산하지 않는다(0-1).
 */
@Component
@RequiredArgsConstructor
class SellerPerformanceMoney {

    private final SettlementCalculator settlementCalculator;

    /** 판매 · 소비자 값 — 정산이 없으면 예정. */
    static FigureState salesState(PerformanceFacts f) {
        return f.projected() ? FigureState.PROJECTED : FigureState.FINAL;
    }

    /** 합의가 반영돼 리워드가 바뀌었는가(D4) — 조정 협의 중은 아직 아니다. */
    static boolean agreed(PerformanceFacts f) {
        Settlement s = f.settlement();
        return s != null && f.status() != PerformanceStatus.ADJUSTING
                && s.getOriginalRewardAmount() != s.getRewardAmount();
    }

    static PerformanceFigure<Long> reward(PerformanceFacts f) {
        Settlement s = f.settlement();
        if (s == null) {
            return PerformanceFigure.of(f.orders().contractRewardAmount(), FigureState.PROJECTED, "상품별 리워드율 × 확정 수량");
        }
        if (f.status() == PerformanceStatus.ADJUSTING) {
            return new PerformanceFigure<>(null, FigureState.NEGOTIATING, s.getOriginalRewardAmount(), "조정 협의 중");
        }
        return new PerformanceFigure<>(s.getRewardAmount(), FigureState.FINAL,
                agreed(f) ? s.getOriginalRewardAmount() : null, agreed(f) ? "합의 반영" : null);
    }

    static PerformanceFigure<Long> totalSpend(PerformanceFacts f) {
        Settlement s = f.settlement();
        if (s == null) {
            return PerformanceFigure.none(FigureState.PENDING_SETTLEMENT, "정산 후 확정");
        }
        long original = spendOf(f, s.getOriginalRewardAmount());
        if (f.status() == PerformanceStatus.ADJUSTING) {
            return new PerformanceFigure<>(null, FigureState.NEGOTIATING, original, "합의 후 변동");
        }
        return new PerformanceFigure<>(spendOf(f, s.getRewardAmount()), FigureState.FINAL,
                agreed(f) ? original : null, "고정 지급비 + 리워드");
    }

    static PerformanceFigure<Long> roasFigure(PerformanceFacts f) {
        Settlement s = f.settlement();
        if (s == null) {
            return PerformanceFigure.none(FigureState.PENDING_SETTLEMENT, "확정 거래액이 확정되면 산출됩니다");
        }
        long sales = f.orders().confirmedSalesAmount();
        long originalSpend = spendOf(f, s.getOriginalRewardAmount());
        if (f.status() == PerformanceStatus.ADJUSTING) {
            return new PerformanceFigure<>(null, FigureState.NEGOTIATING, roas(sales, originalSpend),
                    "총 집행비가 리워드 합의로 바뀜");
        }
        long spend = spendOf(f, s.getRewardAmount());
        return new PerformanceFigure<>(roas(sales, spend), FigureState.FINAL,
                agreed(f) ? roas(sales, originalSpend) : null,
                spend == 0 ? "고정 지급비 · 리워드가 모두 0원" : "확정 거래액 ÷ 총 집행비 %,d원".formatted(spend));
    }

    static PerformanceFigure<Long> acquisitionCost(PerformanceFacts f, long newBuyers) {
        Settlement s = f.settlement();
        if (s == null) {
            return PerformanceFigure.none(FigureState.PENDING_SETTLEMENT, "총 집행비가 확정되면 산출됩니다");
        }
        long originalSpend = spendOf(f, s.getOriginalRewardAmount());
        if (f.status() == PerformanceStatus.ADJUSTING) {
            return new PerformanceFigure<>(null, FigureState.NEGOTIATING, div(originalSpend, newBuyers), "합의 후 확정");
        }
        long spend = spendOf(f, s.getRewardAmount());
        return new PerformanceFigure<>(div(spend, newBuyers), FigureState.FINAL,
                agreed(f) ? div(originalSpend, newBuyers) : null,
                "총 집행비 %,d원 ÷ 신규 소비자 %,d명".formatted(spend, newBuyers));
    }

    /** 총 집행비 = 고정 지급비 + 리워드(0-11) — 리워드 부가세는 넣지 않는다(§55 B-3 잠정). */
    static long spendOf(PerformanceFacts f, long reward) {
        return f.fixedFeeAmount() + reward;
    }

    static long repeatCount(PerformanceFacts f) {
        return f.buyers().stream().filter(PerformanceFacts.Buyer::repeatInBrand).count();
    }

    /**
     * 성과의 정산액 = 이 공구의 브랜드 수취액(<b>차감 전</b>). {@code brand_payout_amount} 는 이전 정산의 회수분까지 뺀 「이번 지급액」이라 다른 공구의
     * 환불이 이 공구 성과로 잡힌다 — 시안 12 rev.8 「정산액 4,775,800 · 이번 지급액 4,751,650」(1021 변경내역 ④ · 56 P1). 이번 지급액은 정산 연결
     * {@code breakdown} 이 따로 싣는다.
     */
    static long payoutAmount(Settlement s) {
        return s.getBrandPayoutBeforeClawback();
    }

    /** 합의 전 정산액 — 정산 계산기가 원래 리워드로 다시 낸다(수수료 · 부가세를 여기서 계산하지 않는다). 차감 전 기준은 {@link #payoutAmount}와 같다. */
    Long originalPayout(PerformanceFacts f) {
        return agreed(f) ? settlementCalculator.preview(f.settlement(), f.settlement().getOriginalRewardAmount())
                .brandPayoutBeforeClawback() : null;
    }

    PerformanceFigure<Long> payout(PerformanceFacts f) {
        Settlement s = f.settlement();
        if (s == null) {
            return PerformanceFigure.none(FigureState.PENDING_SETTLEMENT, "반품 처리가 끝나면 산출됩니다");
        }
        if (f.status() == PerformanceStatus.ADJUSTING) {
            return new PerformanceFigure<>(null, FigureState.NEGOTIATING, payoutAmount(s), "리워드 합의 후 확정");
        }
        Long original = originalPayout(f);
        return new PerformanceFigure<>(payoutAmount(s), FigureState.FINAL, original,
                original != null ? "합의 반영" : "PG·리워드·리워드 부가세 차감 후 실지급 · 정산 관리 기준");
    }
}
