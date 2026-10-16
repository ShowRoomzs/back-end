package showroomz.api.seller.performance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.common.performance.dto.PerformanceFigure;
import showroomz.api.common.settlement.service.SettlementPartyViews;
import showroomz.api.seller.performance.dto.SellerPerformanceDto;
import showroomz.api.seller.settlement.service.SellerSettlementBlocks;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.service.GroupBuyFixedFeeText;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.performance.entity.GroupBuyCost;
import showroomz.domain.performance.entity.GroupBuyCostItem;
import showroomz.domain.performance.service.PerformanceFacts;
import showroomz.domain.performance.service.PerformanceFacts.BreakdownLine;
import showroomz.domain.performance.type.FigureState;
import showroomz.domain.performance.type.PerformanceAgeBand;
import showroomz.domain.performance.type.PerformanceCancelKind;
import showroomz.domain.performance.type.PerformanceExceptionKind;
import showroomz.domain.performance.type.PerformanceSource;
import showroomz.domain.performance.type.PerformanceStatus;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.service.SettlementCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * 파트너 성과 응답 조립(48 파트너 설계서 3절 · 5절) — {@link PerformanceFacts} 원천값에 <b>값 상태</b>(5절 표) · 산식 문장 · 원가를 입힌다.
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

    /** 연령 구간 인원이 이보다 적으면 「기타」로 합산(§47-2 · 소규모 공구의 특정 개인 식별 방지). */
    static final int MIN_BAND_SIZE = 5;
    static final List<String> LIMITS = List.of(
            "쇼룸 방문은 쇼룸 단위로 셉니다 — 같은 기간에 이 인플루언서의 다른 공구가 열려 있었다면 그 방문도 함께 셉니다.",
            "유입 경로는 딥링크 파라미터와 앱 내부 리퍼러로만 판정합니다 — 어느 SNS에서 왔는지는 알 수 없습니다.",
            "공유 수는 아직 집계하지 않습니다.");

    private final SellerSettlementBlocks settlementBlocks;
    private final SettlementCalculator settlementCalculator;

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
                payout(f),
                PerformanceFigure.fin(o.confirmedQuantity(), null),
                PerformanceFigure.fin(pct(o.confirmedCount(), o.orderCount()), null),
                true);
    }

    /** KPI — 지급 완료 공구의 합산 비율(Σ분자 ÷ Σ분모). 공구 평균(평균의 평균)이 아니다. */
    public SellerPerformanceDto.Summary summary(Collection<PerformanceFacts> paid, Map<PerformanceStatus, Long> counts) {
        long n = paid.size();
        if (n == 0) {
            String basis = "집계된 공구 없음";
            return new SellerPerformanceDto.Summary(new SellerPerformanceDto.Population(PerformanceStatus.PAID, 0),
                    PerformanceFigure.none(FigureState.FINAL, basis), PerformanceFigure.none(FigureState.FINAL, basis),
                    PerformanceFigure.none(FigureState.FINAL, basis), PerformanceFigure.none(FigureState.FINAL, basis),
                    PerformanceFigure.none(FigureState.FINAL, basis), new SellerPerformanceDto.GenderRatio(null, null, basis),
                    PerformanceFigure.none(FigureState.FINAL, basis), PerformanceFigure.none(FigureState.FINAL, basis),
                    counts);
        }
        long sales = sum(paid, f -> f.orders().confirmedSalesAmount());
        long visitors = sum(paid, f -> f.traffic().visitorCount());
        long confirmed = sum(paid, f -> f.orders().confirmedCount());
        long orders = sum(paid, f -> f.orders().orderCount());
        long roasSales = 0, spend = 0;
        for (PerformanceFacts f : paid) {
            long s = spendOf(f, f.settlement().getRewardAmount());
            if (s > 0) {
                spend += s;
                roasSales += f.orders().confirmedSalesAmount();
            }
        }
        long buyers = 0, repeat = 0, female = 0, male = 0;
        for (PerformanceFacts f : paid) {
            buyers += f.buyers().size();
            for (PerformanceFacts.Buyer b : f.buyers()) {
                repeat += b.repeatInBrand() ? 1 : 0;
                female += "FEMALE".equalsIgnoreCase(b.gender()) ? 1 : 0;
                male += "MALE".equalsIgnoreCase(b.gender()) ? 1 : 0;
            }
        }
        String scope = "지급 완료 %d건 합산".formatted(n);
        BigDecimal femaleRatio = pct(female, female + male);
        return new SellerPerformanceDto.Summary(new SellerPerformanceDto.Population(PerformanceStatus.PAID, n),
                PerformanceFigure.fin(sales, scope),
                PerformanceFigure.fin(visitors, "%d건 누적 · 고유 방문".formatted(n)),
                PerformanceFigure.fin(div(sales, confirmed), "확정 거래액 ÷ 확정 주문 %,d건".formatted(confirmed)),
                PerformanceFigure.fin(pct(confirmed, visitors),
                        "방문 %,d명 대비 구매확정 %,d건".formatted(visitors, confirmed)),
                PerformanceFigure.fin(roas(roasSales, spend), "확정 거래액 ÷ 총 집행비 %,d원".formatted(spend)),
                femaleRatio == null ? new SellerPerformanceDto.GenderRatio(null, null, scope)
                        : new SellerPerformanceDto.GenderRatio(femaleRatio.setScale(0, RoundingMode.HALF_UP).intValue(),
                        100 - femaleRatio.setScale(0, RoundingMode.HALF_UP).intValue(), scope),
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
                payout(f),
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

    private static SellerPerformanceDto.Traffic traffic(PerformanceFacts f) {
        PerformanceFacts.Traffic t = f.traffic();
        List<SellerPerformanceDto.Source> sources = new ArrayList<>();
        for (PerformanceSource source : PerformanceSource.values()) {
            long count = t.visitorsBySource().getOrDefault(source, 0L);
            sources.add(new SellerPerformanceDto.Source(source.name(), source.getLabel(), count,
                    pct(count, t.visitorCount())));
        }
        SellerPerformanceDto.Post post = null;
        if (f.post() != null) {
            PerformanceFacts.PostFacts p = f.post();
            post = new SellerPerformanceDto.Post(p.postId(), p.impressionCount(), p.uniqueViewerCount(),
                    PerformanceFigure.fin(p.productViewCount(), ratioText("조회 대비", p.productViewCount(), p.impressionCount())),
                    PerformanceFigure.fin(p.orderCount(), ratioText("상세 대비", p.orderCount(), p.productViewCount())),
                    p.likeCount(), PerformanceFigure.none(FigureState.NOT_AVAILABLE, "집계 준비 중"));
        }
        PerformanceFacts.Orders o = f.orders();
        FigureState sales = salesState(f);
        SellerPerformanceDto.Funnel funnel = new SellerPerformanceDto.Funnel(t.visitorCount(), o.orderCount(),
                Math.max(0, t.visitorCount() - o.orderCount()), pct(o.orderCount(), t.visitorCount()),
                o.shippedCount(), o.deliveredCount(), PerformanceFigure.of((long) o.confirmedCount(), sales, null),
                pct(o.shippedCount(), o.orderCount()), pct(o.deliveredCount(), o.orderCount()),
                pct(o.confirmedCount(), o.orderCount()), o.cancelledCount(), o.returnedCount(),
                o.deliveryExceptionCount(),
                PerformanceFigure.of(pct(o.confirmedCount(), t.visitorCount()), sales, "방문 대비 최종 구매전환율"));
        return new SellerPerformanceDto.Traffic(t.visitorCount(), t.visitCount(), sources, post, funnel, LIMITS);
    }

    private static SellerPerformanceDto.Sales sales(PerformanceFacts f) {
        FigureState state = salesState(f);
        List<SellerPerformanceDto.ProductRow> rows = new ArrayList<>();
        for (PerformanceFacts.ProductLine p : f.products()) {
            rows.add(new SellerPerformanceDto.ProductRow(p.contractItemId(), p.productId(), p.productName(),
                    p.rewardRate(), p.detailViewCount(), p.detailViewerCount(), p.orderCount(),
                    PerformanceFigure.fin(pct(p.orderCount(), p.detailViewerCount()), null),
                    PerformanceFigure.of(p.confirmedQuantity(), state, null),
                    PerformanceFigure.of(p.confirmedAmount(), state, null),
                    PerformanceFigure.of(pct(p.returnedQuantity(), p.confirmedQuantity()), state, null)));
        }
        long views = f.products().stream().mapToLong(PerformanceFacts.ProductLine::detailViewCount).sum();
        long returned = f.products().stream().mapToLong(PerformanceFacts.ProductLine::returnedQuantity).sum();
        long viewers = f.traffic().productViewerCount();
        PerformanceFacts.Orders o = f.orders();
        SellerPerformanceDto.ProductRow total = new SellerPerformanceDto.ProductRow(null, null, "합계", null, views,
                viewers, o.orderCount(), PerformanceFigure.fin(pct(o.orderCount(), viewers), null),
                PerformanceFigure.of(o.confirmedQuantity(), state, null),
                PerformanceFigure.of(o.confirmedSalesAmount(), state, null),
                PerformanceFigure.of(pct(returned, o.confirmedQuantity()), state,
                        "공구 전체 반품률 %s%% · 반품 %,d건 ÷ 주문 %,d건".formatted(
                                plain(pct(o.returnedCount(), o.orderCount())), o.returnedCount(), o.orderCount())));
        return new SellerPerformanceDto.Sales(rows, total, highlights(f.products(), views));
    }

    private static SellerPerformanceDto.Highlights highlights(List<PerformanceFacts.ProductLine> products, long views) {
        SellerPerformanceDto.Highlight mostViewed = products.stream().filter(p -> p.detailViewCount() > 0)
                .max(Comparator.comparingLong(PerformanceFacts.ProductLine::detailViewCount))
                .map(p -> new SellerPerformanceDto.Highlight(p.productName(), "상세 조회 %,d회 · 전체의 %s%%".formatted(
                        p.detailViewCount(), plain(pct(p.detailViewCount(), views))))).orElse(null);
        SellerPerformanceDto.Highlight bestConversion = products.stream().filter(p -> p.detailViewerCount() > 0)
                .max(Comparator.comparing(p -> pct(p.orderCount(), p.detailViewerCount())))
                .map(p -> new SellerPerformanceDto.Highlight(p.productName(), "조회자 %,d명 중 %,d건 · %s%%".formatted(
                        p.detailViewerCount(), p.orderCount(), plain(pct(p.orderCount(), p.detailViewerCount())))))
                .orElse(null);
        SellerPerformanceDto.Highlight highestReturn = products.stream()
                .filter(p -> p.returnedQuantity() > 0 && p.confirmedQuantity() > 0)
                .max(Comparator.comparing(p -> pct(p.returnedQuantity(), p.confirmedQuantity())))
                .map(p -> new SellerPerformanceDto.Highlight(p.productName(), "확정 %,d개 중 %,d개 · %s%%".formatted(
                        p.confirmedQuantity(), p.returnedQuantity(),
                        plain(pct(p.returnedQuantity(), p.confirmedQuantity()))))).orElse(null);
        return new SellerPerformanceDto.Highlights(mostViewed, bestConversion, highestReturn);
    }

    private static SellerPerformanceDto.Delivery delivery(PerformanceFacts f) {
        PerformanceFacts.Delivery d = f.delivery();
        PerformanceFacts.Orders o = f.orders();
        return new SellerPerformanceDto.Delivery(
                PerformanceFigure.fin(pct(d.shippedOnTimeCount(), d.shipDueTrackedCount()),
                        "발송 %,d건 중".formatted(d.shipDueTrackedCount())),
                PerformanceFigure.fin(d.avgShipDays(), "결제완료 → 송장 등록"),
                PerformanceFigure.of(pct(d.deliveryClaimCount(), o.orderCount()), salesState(f),
                        "반송·분실 %,d건".formatted(d.deliveryClaimCount())),
                PerformanceFigure.fin(d.avgInquiryHours(), "상품 문의 답변까지 · %,d건".formatted(d.answeredInquiryCount())));
    }

    private static SellerPerformanceDto.Breakdown breakdown(PerformanceFacts f) {
        PerformanceFacts.Breakdown b = f.breakdown();
        PerformanceFacts.Orders o = f.orders();
        List<SellerPerformanceDto.BreakdownLine> cancel = new ArrayList<>();
        b.cancel().forEach((kind, line) -> {
            if (kind != PerformanceCancelKind.OTHER || line.amount() > 0 || line.groupCount() > 0) {
                cancel.add(line(kind.name(), kind.getLabel(), line));
            }
        });
        List<SellerPerformanceDto.BreakdownLine> returned = new ArrayList<>();
        Map<ClaimReason, BreakdownLine> reasons = new EnumMap<>(ClaimReason.class);
        reasons.putAll(b.returned());
        reasons.forEach((reason, line) -> returned.add(line(reason.name(), reason.getLabel(), line)));
        List<SellerPerformanceDto.BreakdownLine> exception = new ArrayList<>();
        b.deliveryException().forEach((kind, line) -> {
            if (kind != PerformanceExceptionKind.OPERATOR_REFUND || line.amount() > 0 || line.groupCount() > 0) {
                exception.add(line(kind.name(), kind.getLabel(), line));
            }
        });
        return new SellerPerformanceDto.Breakdown(
                o.cancelledCount() + o.returnedCount() + o.deliveryExceptionCount(),
                PerformanceFigure.of(b.deductionTotal(), salesState(f), null),
                new SellerPerformanceDto.BreakdownGroup(o.cancelledCount(), amount(b.cancel()), cancel),
                new SellerPerformanceDto.BreakdownGroup(o.returnedCount(), amount(b.returned()), returned),
                new SellerPerformanceDto.BreakdownGroup(o.deliveryExceptionCount(), amount(b.deliveryException()),
                        exception),
                f.projected() && f.closure() != null ? f.closure().openReturnCount() : null);
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

    /** 연령대 × 성별 — 구간 인원 5명 미만은 「기타」 한 행으로 합산 · 미확인은 별도 행. 비율 분모 = 구매확정 소비자 전체. */
    static List<SellerPerformanceDto.AgeBand> ageBands(PerformanceFacts f) {
        Map<PerformanceAgeBand, long[]> counts = new EnumMap<>(PerformanceAgeBand.class);
        for (PerformanceAgeBand band : PerformanceAgeBand.values()) {
            counts.put(band, new long[2]);
        }
        for (PerformanceFacts.Buyer buyer : f.buyers()) {
            long[] c = counts.get(PerformanceAgeBand.of(buyer.birthday(), f.periodStart().toLocalDate()));
            if ("FEMALE".equalsIgnoreCase(buyer.gender())) {
                c[0]++;
            } else if ("MALE".equalsIgnoreCase(buyer.gender())) {
                c[1]++;
            }
        }
        long total = f.buyers().size();
        List<SellerPerformanceDto.AgeBand> bands = new ArrayList<>();
        long[] other = new long[2];
        boolean merged = false;
        long[] unknown = counts.get(PerformanceAgeBand.UNKNOWN);
        long unknownGenderless = total - counts.values().stream().mapToLong(c -> c[0] + c[1]).sum();
        for (PerformanceAgeBand band : PerformanceAgeBand.values()) {
            if (band == PerformanceAgeBand.UNKNOWN) {
                continue;
            }
            long[] c = counts.get(band);
            long size = c[0] + c[1];
            if (size == 0) {
                continue;
            }
            if (size < MIN_BAND_SIZE) {
                other[0] += c[0];
                other[1] += c[1];
                merged = true;
                continue;
            }
            bands.add(new SellerPerformanceDto.AgeBand(band.name(), band.getLabel(), c[0], c[1], size, pct(size, total),
                    false));
        }
        if (merged) {
            long size = other[0] + other[1];
            bands.add(new SellerPerformanceDto.AgeBand("OTHER", "기타", other[0], other[1], size, pct(size, total), true));
        }
        long unknownSize = unknown[0] + unknown[1] + unknownGenderless;
        if (unknownSize > 0) {
            bands.add(new SellerPerformanceDto.AgeBand(PerformanceAgeBand.UNKNOWN.name(),
                    PerformanceAgeBand.UNKNOWN.getLabel(), unknown[0], unknown[1], unknownSize, pct(unknownSize, total),
                    false));
        }
        return bands;
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
            long value = s.getBrandPayoutAmount() - total - f.fixedFeeAmount();
            Long original = originalPayout(f) == null ? null : originalPayout(f) - total - f.fixedFeeAmount();
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
                settlementBlocks.breakdownOf(s), payout(f), s.getReviewDueAt(), s.getPayoutDueDate(), s.getPaidAt(),
                settlementBlocks.adjustmentOf(s));
    }

    // ------------------------------------------------------------------ 돈 · 상태(5절)

    /** 판매 · 소비자 값 — 정산이 없으면 예정. */
    private static FigureState salesState(PerformanceFacts f) {
        return f.projected() ? FigureState.PROJECTED : FigureState.FINAL;
    }

    /** 합의가 반영돼 리워드가 바뀌었는가(D4) — 조정 협의 중은 아직 아니다. */
    private static boolean agreed(PerformanceFacts f) {
        Settlement s = f.settlement();
        return s != null && f.status() != PerformanceStatus.ADJUSTING
                && s.getOriginalRewardAmount() != s.getRewardAmount();
    }

    /** 합의 전 정산액 — 정산 계산기가 원래 리워드로 다시 낸다(수수료 · 부가세를 여기서 계산하지 않는다). */
    private Long originalPayout(PerformanceFacts f) {
        return agreed(f) ? settlementCalculator.preview(f.settlement(), f.settlement().getOriginalRewardAmount())
                .brandPayoutAmount() : null;
    }

    private PerformanceFigure<Long> payout(PerformanceFacts f) {
        Settlement s = f.settlement();
        if (s == null) {
            return PerformanceFigure.none(FigureState.PENDING_SETTLEMENT, "반품 처리가 끝나면 산출됩니다");
        }
        if (f.status() == PerformanceStatus.ADJUSTING) {
            return new PerformanceFigure<>(null, FigureState.NEGOTIATING, s.getBrandPayoutAmount(), "리워드 합의 후 확정");
        }
        Long original = originalPayout(f);
        return new PerformanceFigure<>(s.getBrandPayoutAmount(), FigureState.FINAL, original,
                original != null ? "합의 반영" : "PG·리워드·리워드 부가세 차감 후 실지급 · 정산 관리 기준");
    }

    private static PerformanceFigure<Long> reward(PerformanceFacts f) {
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

    private static PerformanceFigure<Long> totalSpend(PerformanceFacts f) {
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

    private static PerformanceFigure<Long> roasFigure(PerformanceFacts f) {
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

    private static PerformanceFigure<Long> acquisitionCost(PerformanceFacts f, long newBuyers) {
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
    private static long spendOf(PerformanceFacts f, long reward) {
        return f.fixedFeeAmount() + reward;
    }

    private static long repeatCount(PerformanceFacts f) {
        return f.buyers().stream().filter(PerformanceFacts.Buyer::repeatInBrand).count();
    }

    private static String rewardRateLabel(PerformanceFacts f) {
        List<SettlementPartyViews.RewardRate> rates = f.products().stream().filter(p -> p.rewardRate() != null)
                .map(p -> new SettlementPartyViews.RewardRate(p.productName(), p.rewardRate())).toList();
        return SettlementPartyViews.rewardRateLabel(rates);
    }

    private static SellerPerformanceDto.Influencer influencer(GroupBuy groupBuy) {
        return new SellerPerformanceDto.Influencer(groupBuy.getCreator().getId(), groupBuy.getCreator().getShowroomName());
    }

    // ------------------------------------------------------------------ 수치

    /** % 값 · 소수 1자리 · 분모 0 이면 null. */
    static BigDecimal pct(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    /** ROAS % 정수 · 집행비 0 이면 null(0-11). */
    static Long roas(long sales, long spend) {
        if (spend <= 0) {
            return null;
        }
        return BigDecimal.valueOf(sales).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(spend), 0, RoundingMode.HALF_UP).longValue();
    }

    static Long div(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    private static String ratioText(String prefix, long numerator, long denominator) {
        BigDecimal ratio = pct(numerator, denominator);
        return ratio == null ? null : "%s %s%%".formatted(prefix, plain(ratio));
    }

    private static String plain(BigDecimal value) {
        return value == null ? "—" : value.toPlainString();
    }

    private static long zero(Long value) {
        return value == null ? 0 : value;
    }

    private static <K> long amount(Map<K, BreakdownLine> lines) {
        return lines.values().stream().mapToLong(BreakdownLine::amount).sum();
    }

    private static SellerPerformanceDto.BreakdownLine line(String key, String label, BreakdownLine line) {
        return new SellerPerformanceDto.BreakdownLine(key, label, line.quantity(), line.amount(), line.groupCount());
    }

    private static long sum(Collection<PerformanceFacts> facts, ToLongFunction<PerformanceFacts> value) {
        return facts.stream().mapToLong(value).sum();
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
