package showroomz.api.seller.performance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.common.performance.dto.FigureState;
import showroomz.api.common.performance.dto.PerformanceFigure;
import showroomz.api.common.settlement.service.SettlementPartyViews;
import showroomz.api.seller.performance.dto.SellerPerformanceDto;
import showroomz.api.seller.settlement.service.SellerSettlementBlocks;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.service.GroupBuyFixedFeeText;
import showroomz.domain.performance.entity.GroupBuyCost;
import showroomz.domain.performance.entity.GroupBuyCostItem;
import showroomz.domain.performance.service.PerformanceFacts;
import showroomz.domain.performance.type.PerformanceStatus;
import showroomz.domain.performance.vo.GenderSplit;
import showroomz.domain.settlement.entity.Settlement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static showroomz.api.seller.performance.service.SellerPerformanceMoney.acquisitionCost;
import static showroomz.api.seller.performance.service.SellerPerformanceMoney.repeatCount;
import static showroomz.api.seller.performance.service.SellerPerformanceMoney.reward;
import static showroomz.api.seller.performance.service.SellerPerformanceMoney.roasFigure;
import static showroomz.api.seller.performance.service.SellerPerformanceMoney.salesState;
import static showroomz.api.seller.performance.service.SellerPerformanceMoney.spendOf;
import static showroomz.api.seller.performance.service.SellerPerformanceMoney.totalSpend;
import static showroomz.api.seller.performance.service.SellerPerformanceNumbers.div;
import static showroomz.api.seller.performance.service.SellerPerformanceNumbers.pct;
import static showroomz.api.seller.performance.service.SellerPerformanceNumbers.plain;
import static showroomz.api.seller.performance.service.SellerPerformanceNumbers.roas;
import static showroomz.api.seller.performance.service.SellerPerformanceNumbers.sum;
import static showroomz.api.seller.performance.service.SellerPerformanceNumbers.zero;
import static showroomz.api.seller.performance.service.SellerPerformanceSections.ageBands;
import static showroomz.api.seller.performance.service.SellerPerformanceSections.breakdown;
import static showroomz.api.seller.performance.service.SellerPerformanceSections.delivery;
import static showroomz.api.seller.performance.service.SellerPerformanceSections.sales;
import static showroomz.api.seller.performance.service.SellerPerformanceSections.traffic;

/**
 * 파트너 성과 응답 조립(48 파트너 설계서 3절 · 5절) — {@link PerformanceFacts} 원천값에 <b>값 상태</b>(5절 표) · 산식 문장 · 원가를 입힌다.
 * 돈 · 값 상태 규칙은 {@link SellerPerformanceMoney}, 사실 섹션은 {@link SellerPerformanceSections}, 수치 표기는
 * {@link SellerPerformanceNumbers}에 나눠 두었다(정합 계획서 R4-4).
 *
 * <ul>
 *     <li>판매 · 소비자 값 — 집계 중이면 {@code PROJECTED}(예정), 정산이 있으면 {@code FINAL}.</li>
 *     <li>돈에 걸린 값(정산액 · 리워드 · 총 집행비 · ROAS · 획득 단가 · 수익 · 수익률) — 집계 중 {@code PENDING_SETTLEMENT}, 조정 협의
 *     {@code NEGOTIATING} + 원래 값, 합의 반영(지급 예정 · 완료 + 리워드가 바뀜)은 {@code FINAL} + 원래 값.</li>
 *     <li>방문 · 유입 · 게시물 · 주문 · 발송 — 줄지 않는 값이라 언제나 {@code FINAL}.</li>
 * </ul>
 *
 * 원래 값(합의 전 정산액)은 정산 계산기가 원래 리워드로 다시 낸 값이다 — 여기서 수수료 · 부가세를 따로 계산하지 않는다(0-1).
 */
@Component
@RequiredArgsConstructor
public class SellerPerformanceAssembler {

    private final SellerSettlementBlocks settlementBlocks;
    private final SellerPerformanceMoney money;

    // ------------------------------------------------------------------ 목록 · 요약

    public SellerPerformanceDto.ListItem listItem(GroupBuy groupBuy, PerformanceFacts f) {
        PerformanceStatus status = f.status();
        if (f.projected()) {
            PerformanceFigure<Long> none = PerformanceFigure.none(FigureState.PROJECTED, null);
            PerformanceFigure<BigDecimal> noneRate = PerformanceFigure.none(FigureState.PROJECTED, null);
            return new SellerPerformanceDto.ListItem(groupBuy.getId(), groupBuy.getContract().getTitle(),
                    influencer(groupBuy), f.periodStart(), f.periodEnd(), status, status.getLabel(), status.getTone(),
                    none, noneRate, none, none, none, noneRate, true);
        }
        PerformanceFacts.Orders o = f.orders();
        return new SellerPerformanceDto.ListItem(groupBuy.getId(), groupBuy.getContract().getTitle(),
                influencer(groupBuy), f.periodStart(), f.periodEnd(), status, status.getLabel(), status.getTone(),
                PerformanceFigure.fin(f.traffic().visitorCount(), null),
                PerformanceFigure.fin(pct(o.confirmedCount(), f.traffic().visitorCount()), null),
                PerformanceFigure.fin(o.confirmedSalesAmount(), null),
                money.payout(f),
                PerformanceFigure.fin(o.confirmedQuantity(), null),
                PerformanceFigure.fin(pct(o.confirmedCount(), o.orderCount()), null),
                true);
    }

    /**
     * KPI — 모집단(지급 예정 + 지급 완료 · {@link PerformanceStatus#KPI_POPULATION}) 공구의 합산 비율(Σ분자 ÷ Σ분모). 공구 평균(평균의
     * 평균)이 아니다. 근거 문장은 「정산 N건 합산(지급 예정 a · 지급 완료 b)」 — 한쪽이 0건이어도 둘 다 적는다(55 계획서 P1).
     *
     * @param settled 모집단(금액이 확정된 정산) 공구의 원천값
     * @param counts  상태 칩 숫자 — 모집단 내역(breakdown)은 여기서 두 키만 뗀다(쿼리를 늘리지 않는다)
     */
    public SellerPerformanceDto.Summary summary(Collection<PerformanceFacts> settled, Map<PerformanceStatus, Long> counts) {
        long n = settled.size();
        SellerPerformanceDto.Population population = population(n, counts);
        if (n == 0) {
            String basis = "집계된 공구 없음";
            return new SellerPerformanceDto.Summary(population,
                    PerformanceFigure.none(FigureState.FINAL, basis), PerformanceFigure.none(FigureState.FINAL, basis),
                    PerformanceFigure.none(FigureState.FINAL, basis), PerformanceFigure.none(FigureState.FINAL, basis),
                    PerformanceFigure.none(FigureState.FINAL, basis), new SellerPerformanceDto.GenderRatio(null, null, basis),
                    PerformanceFigure.none(FigureState.FINAL, basis), PerformanceFigure.none(FigureState.FINAL, basis),
                    counts);
        }
        long sales = sum(settled, f -> f.orders().confirmedSalesAmount());
        long visitors = sum(settled, f -> f.traffic().visitorCount());
        long confirmed = sum(settled, f -> f.orders().confirmedCount());
        long orders = sum(settled, f -> f.orders().orderCount());
        long roasSales = 0, spend = 0;
        for (PerformanceFacts f : settled) {
            long s = spendOf(f, f.settlement().getRewardAmount());
            if (s > 0) {
                spend += s;
                roasSales += f.orders().confirmedSalesAmount();
            }
        }
        long buyers = 0, repeat = 0, female = 0, male = 0;
        for (PerformanceFacts f : settled) {
            buyers += f.buyers().size();
            for (PerformanceFacts.Buyer b : f.buyers()) {
                repeat += b.repeatInBrand() ? 1 : 0;
                female += "FEMALE".equalsIgnoreCase(b.gender()) ? 1 : 0;
                male += "MALE".equalsIgnoreCase(b.gender()) ? 1 : 0;
            }
        }
        String scope = "정산 %d건 합산(지급 예정 %d · 지급 완료 %d)".formatted(n,
                population.breakdown().get(PerformanceStatus.PAYOUT_SCHEDULED), population.breakdown().get(PerformanceStatus.PAID));
        GenderSplit split = GenderSplit.of(female, male);
        return new SellerPerformanceDto.Summary(population,
                PerformanceFigure.fin(sales, scope),
                PerformanceFigure.fin(visitors, "%d건 누적 · 고유 방문".formatted(n)),
                PerformanceFigure.fin(div(sales, confirmed), "확정 거래액 ÷ 확정 주문 %,d건".formatted(confirmed)),
                PerformanceFigure.fin(pct(confirmed, visitors),
                        "방문 %,d명 대비 구매확정 %,d건".formatted(visitors, confirmed)),
                PerformanceFigure.fin(roas(roasSales, spend), "확정 거래액 ÷ 총 집행비 %,d원".formatted(spend)),
                split == null ? new SellerPerformanceDto.GenderRatio(null, null, scope)
                        : new SellerPerformanceDto.GenderRatio(split.femalePercent(), split.malePercent(), scope),
                PerformanceFigure.fin(pct(confirmed, orders), "주문 %,d건 중 %,d건 확정".formatted(orders, confirmed)),
                PerformanceFigure.fin(pct(repeat, buyers), "구매확정 %,d명 중 재구매 %,d명".formatted(buyers, repeat)),
                counts);
    }

    // ------------------------------------------------------------------ 상세

    public SellerPerformanceDto.Detail detail(GroupBuy groupBuy, PerformanceFacts f, GroupBuyCost cost) {
        Contract contract = groupBuy.getContract();
        PerformanceStatus status = f.status();
        return new SellerPerformanceDto.Detail(groupBuy.getId(), contract.getTitle(), influencer(groupBuy),
                contract.getConnection() == null ? null : contract.getConnection().getId(),
                f.periodStart(), f.periodEnd(), status, status.getLabel(), status.getTone(),
                aggregating(f), contractBar(contract, f), kpi(f), traffic(f), sales(f), delivery(f), breakdown(f),
                consumers(f), cost(f, cost), settlementLink(f));
    }

    private static SellerPerformanceDto.Aggregating aggregating(PerformanceFacts f) {
        if (!f.projected() || f.closure() == null) {
            return null;
        }
        return new SellerPerformanceDto.Aggregating(f.closure().unclosedCount(), f.closure().openReturnCount(),
                f.closure().stages().stream()
                        .map(s -> new SellerPerformanceDto.Stage(s.stage(), s.label(), s.count())).toList());
    }

    private SellerPerformanceDto.ContractBar contractBar(Contract contract, PerformanceFacts f) {
        return new SellerPerformanceDto.ContractBar(contract.getId(),
                contract.getFixedFeeAmount() == null ? null : contract.getFixedFeeAmount().longValue(),
                contract.getFixedFeeTrigger() == null ? null : contract.getFixedFeeTrigger().getLabel(),
                GroupBuyFixedFeeText.of(contract), rewardRateLabel(f), reward(f), totalSpend(f),
                GroupBuy.daysInclusive(f.periodStart(), f.periodEnd()));
    }

    private SellerPerformanceDto.Kpi kpi(PerformanceFacts f) {
        PerformanceFacts.Orders o = f.orders();
        FigureState sales = salesState(f);
        long visitors = f.traffic().visitorCount();
        return new SellerPerformanceDto.Kpi(
                PerformanceFigure.of(o.confirmedSalesAmount(), sales,
                        "구매확정 %s%,d건 기준".formatted(f.projected() ? "예정 " : "", o.confirmedCount())),
                money.payout(f),
                PerformanceFigure.of(pct(o.confirmedCount(), visitors), sales,
                        "쇼룸 방문 %,d명 대비 구매확정".formatted(visitors)),
                PerformanceFigure.of(pct(o.confirmedCount(), o.orderCount()), sales,
                        "주문 %,d건 중 %,d건 확정".formatted(o.orderCount(), o.confirmedCount())),
                PerformanceFigure.of(o.confirmedQuantity(), sales, "주문 수량 %,d개".formatted(o.orderedQuantity())),
                o.orderedQuantity(),
                PerformanceFigure.of(div(o.confirmedSalesAmount(), o.confirmedCount()), sales,
                        "확정 거래액 ÷ 확정 주문 %,d건".formatted(o.confirmedCount())),
                roasFigure(f),
                PerformanceFigure.of(pct(repeatCount(f), f.buyers().size()), sales,
                        "구매확정 %,d명 중 재구매 %,d명".formatted(f.buyers().size(), repeatCount(f))));
    }

    private SellerPerformanceDto.Consumers consumers(PerformanceFacts f) {
        FigureState sales = salesState(f);
        List<PerformanceFacts.Buyer> buyers = f.buyers();
        long repeat = repeatCount(f);
        long newBuyers = buyers.size() - repeat;
        long followers = buyers.stream().filter(PerformanceFacts.Buyer::followerAtPurchase).count();
        long female = buyers.stream().filter(b -> "FEMALE".equalsIgnoreCase(b.gender())).count();
        long male = buyers.stream().filter(b -> "MALE".equalsIgnoreCase(b.gender())).count();
        return new SellerPerformanceDto.Consumers(f.projected(), f.orders().confirmedCount(), buyers.size(),
                PerformanceFigure.of(newBuyers, sales, "구매확정 소비자 중 %s%%".formatted(plain(pct(newBuyers, buyers.size())))),
                PerformanceFigure.of(repeat, sales, "이 브랜드 공구를 이전에 구매한 소비자"),
                PerformanceFigure.fin(pct(followers, buyers.size()), "구매확정 소비자 중 결제 시점에 이 쇼룸을 팔로우한 비율"),
                acquisitionCost(f, newBuyers),
                new SellerPerformanceDto.Gender(female, male, buyers.size() - female - male, pct(female, female + male),
                        pct(male, female + male)),
                ageBands(f));
    }

    private SellerPerformanceDto.Cost cost(PerformanceFacts f, GroupBuyCost cost) {
        boolean editable = !f.projected();
        FigureState state = salesState(f);
        List<SellerPerformanceDto.CostItem> items = new ArrayList<>();
        boolean complete = cost != null && !f.products().isEmpty();
        long productCost = 0;
        for (PerformanceFacts.ProductLine p : f.products()) {
            Long unitCost = cost == null ? null
                    : cost.itemOf(p.contractItemId()).map(GroupBuyCostItem::getUnitCost).orElse(null);
            Long subtotal = unitCost == null || f.projected() ? null : unitCost * p.confirmedQuantity();
            complete &= unitCost != null;
            productCost += subtotal == null ? 0 : subtotal;
            items.add(new SellerPerformanceDto.CostItem(p.contractItemId(), p.productName(),
                    PerformanceFigure.of(p.confirmedQuantity(), state, null), unitCost, subtotal));
        }
        Long packaging = cost == null ? null : cost.getPackagingCost();
        Long shipping = cost == null ? null : cost.getShippingCost();
        Long disposal = cost == null ? null : cost.getReturnDisposalCost();
        Long etc = cost == null ? null : cost.getEtcCost();

        PerformanceFigure<Long> totalCost;
        PerformanceFigure<Long> profit;
        PerformanceFigure<BigDecimal> profitRate;
        if (f.projected()) {
            totalCost = PerformanceFigure.none(FigureState.PENDING_SETTLEMENT, "확정 수량이 확정되면 산출됩니다");
            profit = PerformanceFigure.none(FigureState.PENDING_SETTLEMENT, "정산액이 확정되면 산출됩니다");
            profitRate = PerformanceFigure.none(FigureState.PENDING_SETTLEMENT, "정산액이 확정되면 산출됩니다");
        } else if (!complete) {
            String basis = "개당 원가를 입력하면 수익이 계산됩니다";
            totalCost = PerformanceFigure.none(FigureState.FINAL, basis);
            profit = PerformanceFigure.none(FigureState.FINAL, basis);
            profitRate = PerformanceFigure.none(FigureState.FINAL, basis);
        } else {
            long total = productCost + zero(packaging) + zero(shipping) + zero(disposal) + zero(etc);
            totalCost = PerformanceFigure.fin(total, "상품 원가 + 부자재 + 배송비 + 반품 폐기 + 기타");
            Settlement s = f.settlement();
            long sales = f.orders().confirmedSalesAmount();
            long value = SellerPerformanceMoney.payoutAmount(s) - total - f.fixedFeeAmount();
            Long original = money.originalPayout(f) == null ? null : money.originalPayout(f) - total - f.fixedFeeAmount();
            String basis = "정산액 − 총 원가 − 고정 지급비";
            if (f.status() == PerformanceStatus.ADJUSTING) {
                profit = new PerformanceFigure<>(null, FigureState.NEGOTIATING, value, "정산액 확정 후 산출");
                profitRate = new PerformanceFigure<>(null, FigureState.NEGOTIATING, pct(value, sales), "정산액 확정 후 산출");
            } else {
                profit = new PerformanceFigure<>(value, FigureState.FINAL, original, basis);
                profitRate = new PerformanceFigure<>(pct(value, sales), FigureState.FINAL,
                        original == null ? null : pct(original, sales), "확정 거래액 대비");
            }
        }
        return new SellerPerformanceDto.Cost(editable, editable ? null : PerformanceStatus.AGGREGATING.name(), items,
                packaging, shipping, disposal, etc, totalCost, profit, profitRate,
                cost == null ? null : cost.getUpdatedAt());
    }

    private SellerPerformanceDto.SettlementLink settlementLink(PerformanceFacts f) {
        Settlement s = f.settlement();
        if (s == null) {
            return null;
        }
        PerformanceStatus status = f.status();
        return new SellerPerformanceDto.SettlementLink(s.getId(), status, status.getLabel(), status.getTone(),
                settlementBlocks.breakdownOf(s), money.payout(f), s.getReviewDueAt(), s.getPayoutDueDate(), s.getPaidAt(),
                settlementBlocks.adjustmentOf(s));
    }

    // ------------------------------------------------------------------ 표기

    private static String rewardRateLabel(PerformanceFacts f) {
        List<SettlementPartyViews.RewardRate> rates = f.products().stream().filter(p -> p.rewardRate() != null)
                .map(p -> new SettlementPartyViews.RewardRate(p.productName(), p.rewardRate())).toList();
        return SettlementPartyViews.rewardRateLabel(rates);
    }

    private static SellerPerformanceDto.Influencer influencer(GroupBuy groupBuy) {
        return new SellerPerformanceDto.Influencer(groupBuy.getCreator().getId(), groupBuy.getCreator().getShowroomName());
    }

    /** KPI 모집단 — 상태 칩 숫자에서 모집단 상태만 뗀다(두 키가 항상 있다 · 0 포함). */
    private static SellerPerformanceDto.Population population(long count, Map<PerformanceStatus, Long> counts) {
        Map<PerformanceStatus, Long> breakdown = new LinkedHashMap<>();
        for (PerformanceStatus status : PerformanceStatus.KPI_POPULATION) {
            breakdown.put(status, counts.getOrDefault(status, 0L));
        }
        return new SellerPerformanceDto.Population(count, List.copyOf(PerformanceStatus.KPI_POPULATION), breakdown);
    }

    /** 상태 칩 — 0 인 상태도 키가 있다. */
    static Map<PerformanceStatus, Long> emptyCounts() {
        Map<PerformanceStatus, Long> counts = new LinkedHashMap<>();
        for (PerformanceStatus status : PerformanceStatus.values()) {
            counts.put(status, 0L);
        }
        return counts;
    }
}
