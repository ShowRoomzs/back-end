package showroomz.api.seller.performance.service;

import showroomz.api.common.performance.dto.FigureState;
import showroomz.api.common.performance.dto.PerformanceFigure;
import showroomz.api.seller.performance.dto.SellerPerformanceDto;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.performance.service.PerformanceFacts.BreakdownLine;
import showroomz.domain.performance.service.PerformanceFacts;
import showroomz.domain.performance.type.PerformanceAgeBand;
import showroomz.domain.performance.type.PerformanceCancelKind;
import showroomz.domain.performance.type.PerformanceExceptionKind;
import showroomz.domain.performance.type.PerformanceSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static showroomz.api.seller.performance.service.SellerPerformanceMoney.salesState;
import static showroomz.api.seller.performance.service.SellerPerformanceNumbers.pct;
import static showroomz.api.seller.performance.service.SellerPerformanceNumbers.plain;
import static showroomz.api.seller.performance.service.SellerPerformanceNumbers.ratioText;

/**
 * 파트너 성과 상세의 사실 섹션 — 유입 · 퍼널 · 상품별 판매 · 배송 품질 · 취소 · 반품 · 예외 분해 · 연령대(48 파트너 설계서 3-3 · 정합 계획서
 * R4-4). 돈과 무관한 값이라 정산 계산기를 부르지 않는다 — 값 상태는 {@link SellerPerformanceMoney#salesState}만 본다.
 */
final class SellerPerformanceSections {

    /** 연령 구간 인원이 이보다 적으면 「기타」로 합산(§47-2 · 소규모 공구의 특정 개인 식별 방지). */
    static final int MIN_BAND_SIZE = 5;
    static final List<String> LIMITS = List.of(
            "쇼룸 방문은 쇼룸 단위로 셉니다 — 같은 기간에 이 인플루언서의 다른 공구가 열려 있었다면 그 방문도 함께 셉니다.",
            "유입 경로는 딥링크 파라미터와 앱 내부 리퍼러로만 판정합니다 — 어느 SNS에서 왔는지는 알 수 없습니다.",
            "공유 수는 아직 집계하지 않습니다.");

    private SellerPerformanceSections() {
    }

    static SellerPerformanceDto.Traffic traffic(PerformanceFacts f) {
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

    static SellerPerformanceDto.Sales sales(PerformanceFacts f) {
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

    static SellerPerformanceDto.Highlights highlights(List<PerformanceFacts.ProductLine> products, long views) {
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

    static SellerPerformanceDto.Delivery delivery(PerformanceFacts f) {
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

    static SellerPerformanceDto.Breakdown breakdown(PerformanceFacts f) {
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

    static <K> long amount(Map<K, BreakdownLine> lines) {
        return lines.values().stream().mapToLong(BreakdownLine::amount).sum();
    }

    static SellerPerformanceDto.BreakdownLine line(String key, String label, BreakdownLine line) {
        return new SellerPerformanceDto.BreakdownLine(key, label, line.quantity(), line.amount(), line.groupCount());
    }
}
