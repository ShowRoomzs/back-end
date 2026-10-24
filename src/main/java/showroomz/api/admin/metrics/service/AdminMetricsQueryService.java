package showroomz.api.admin.metrics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.metrics.dto.AdminMetricsDto;
import showroomz.api.admin.metrics.service.MetricsFactsCalculator.Facts;
import showroomz.api.admin.metrics.service.MetricsFactsCalculator.Value;
import showroomz.api.admin.metrics.type.MetricKey;
import showroomz.api.admin.metrics.type.MetricsPreset;
import showroomz.api.admin.metrics.type.MetricsTab;
import showroomz.api.admin.metrics.type.MetricsUnit;
import showroomz.domain.metrics.repository.MetricsQueryRepository.ClaimLine;
import showroomz.domain.metrics.repository.MetricsQueryRepository.GroupBuyLine;
import showroomz.domain.metrics.repository.MetricsQueryRepository.PaidLine;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 어드민 운영 지표(1017 설계서 7절) — <b>집계 전용 · 저장 없음</b>. 같은 기간의 계산은 10분 캐시한다(인메모리 · 단일 인스턴스 전제).
 * 탭 배지 「나빠진 지표 수」는 지표 방향({@link MetricKey.BetterWhen})으로 서버가 센다 — 핵심 탭은 4칸도 센다({@link #badgeKeys}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMetricsQueryService {

    static final Duration CACHE_TTL = Duration.ofMinutes(10);
    static final int TOP = 5;

    static final Map<MetricsTab, List<MetricKey>> TAB_ROWS = new EnumMap<>(Map.of(
            MetricsTab.CORE, List.of(MetricKey.GMV, MetricKey.ORDERS, MetricKey.GMV_MEDIAN, MetricKey.FEE_REVENUE,
                    MetricKey.RETURN_EXCHANGE_RATE),
            MetricsTab.SUPPLY, List.of(MetricKey.ACTIVE_CREATORS, MetricKey.ACTIVE_BRANDS, MetricKey.CREATOR_REGROUPBUY_RATE,
                    MetricKey.BRAND_RECONTRACT_RATE, MetricKey.CONNECTION_CONVERSION, MetricKey.CONCLUDE_TO_OPEN_DAYS,
                    MetricKey.NEW_ONBOARDING),
            MetricsTab.GROUP_BUY, List.of(MetricKey.GROUP_BUYS_RUN, MetricKey.GMV_MEDIAN, MetricKey.MIN_QUANTITY_RATE,
                    MetricKey.BRAND_ROI, MetricKey.SUSPENSIONS, MetricKey.OPEN_REVIEW_DAYS),
            MetricsTab.DEMAND, List.of(MetricKey.BUYERS, MetricKey.BUYERS_MEDIAN, MetricKey.FOLLOWER_CONVERSION,
                    MetricKey.REPURCHASE_RATE, MetricKey.NEW_CONSUMERS, MetricKey.CONSUMER_WITHDRAWALS),
            MetricsTab.OPERATION, List.of(MetricKey.BRAND_FAULT_RETURN_RATE, MetricKey.ADJUSTMENT_REQUEST_RATE,
                    MetricKey.POST_REPORT_RATE, MetricKey.PROCESSED, MetricKey.SLA_RATE,
                    MetricKey.INQUIRY_FIRST_ANSWER_HOURS, MetricKey.REFUND_FAILURES)));
    static final List<MetricKey> CORE_CARDS = List.of(MetricKey.ACTIVE_CREATORS, MetricKey.MIN_QUANTITY_RATE,
            MetricKey.FOLLOWER_CONVERSION, MetricKey.BRAND_FAULT_RETURN_RATE);

    private final MetricsPeriodResolver periodResolver;
    private final MetricsFactsCalculator calculator;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public AdminMetricsDto.Response getMetrics(MetricsPreset preset, LocalDate from, LocalDate to, MetricsTab tab) {
        LocalDateTime now = LocalDateTime.now();
        MetricsPeriodResolver.Resolved period = periodResolver.resolve(preset, from, to, now);
        MetricsTab target = tab == null ? MetricsTab.CORE : tab;
        Facts current = facts(period.from(), period.to(), now);
        Facts previous = facts(period.compareFrom(), period.compareTo(), now);
        List<AdminMetricsDto.Row> rows = TAB_ROWS.get(target).stream().map(k -> row(k, current, previous)).toList();
        List<AdminMetricsDto.Row> cards = target == MetricsTab.CORE
                ? CORE_CARDS.stream().map(k -> row(k, current, previous)).toList() : null;
        return new AdminMetricsDto.Response(period.preset().name(), target.name(),
                new AdminMetricsDto.Period(period.from(), period.to(), period.label()),
                new AdminMetricsDto.Period(period.compareFrom(), period.compareTo(), period.compareLabel()),
                chart(target, period, current, previous), cards, rows, breakdown(target, current), now);
    }

    public List<AdminMetricsDto.TabBadge> getTabs(MetricsPreset preset, LocalDate from, LocalDate to) {
        LocalDateTime now = LocalDateTime.now();
        MetricsPeriodResolver.Resolved period = periodResolver.resolve(preset, from, to, now);
        Facts current = facts(period.from(), period.to(), now);
        Facts previous = facts(period.compareFrom(), period.compareTo(), now);
        List<AdminMetricsDto.TabBadge> badges = new ArrayList<>();
        for (MetricsTab tab : MetricsTab.values()) {
            long worsened = badgeKeys(tab).stream()
                    .filter(k -> "BAD".equals(row(k, current, previous).deltaKind())).count();
            badges.add(new AdminMetricsDto.TabBadge(tab.name(), tab.getLabel(), worsened));
        }
        return badges;
    }

    /** 탭 배지가 세는 지표 — 표 줄 + 핵심 탭은 4칸까지(시안 21 「4칸 포함」). 표 줄과 4칸에 같은 지표가 있으면 한 번만 센다. */
    static List<MetricKey> badgeKeys(MetricsTab tab) {
        Set<MetricKey> keys = new LinkedHashSet<>(TAB_ROWS.get(tab));
        if (tab == MetricsTab.CORE) {
            keys.addAll(CORE_CARDS);
        }
        return List.copyOf(keys);
    }

    private Facts facts(LocalDateTime from, LocalDateTime to, LocalDateTime now) {
        String key = from + "|" + to;
        Cached cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.facts();
        }
        cache.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(now));
        Facts facts = calculator.compute(from, to, now);
        cache.put(key, new Cached(facts, now.plus(CACHE_TTL)));
        return facts;
    }

    /**
     * 증감 판정 — 지표 방향이 없거나 비교 값이 없으면 NEUTRAL. 설명은 계산이 만든 한 줄(정의 + 이번 기간 값)이 있으면 그것, 없으면 정의 문구다.
     */
    static AdminMetricsDto.Row row(MetricKey key, Facts current, Facts previous) {
        Value cur = current.get(key);
        Value prev = previous.get(key);
        Double delta = cur.value() == null || prev.value() == null ? null
                : MetricsFactsCalculator.round1(cur.value() - prev.value());
        String kind = "NEUTRAL";
        if (delta != null && delta != 0 && key.getBetterWhen() != MetricKey.BetterWhen.NONE) {
            boolean up = delta > 0;
            kind = (up == (key.getBetterWhen() == MetricKey.BetterWhen.UP)) ? "GOOD" : "BAD";
        }
        String note = cur.note() != null ? cur.note() : key.getDefinition();
        return new AdminMetricsDto.Row(key.name(), key.getLabel(), note, key.getValueType().name(), cur.value(),
                prev.value(), delta, kind, cur.countText(), prev.countText(), cur.breakdown(), prev.breakdown());
    }

    // ------------------------------------------------------------------ 차트

    private AdminMetricsDto.Chart chart(MetricsTab tab, MetricsPeriodResolver.Resolved period, Facts current,
                                       Facts previous) {
        MetricsUnit unit = period.unit();
        List<LocalDateTime[]> buckets;
        MetricKey key;
        Function<LocalDateTime[], Double> value;
        switch (tab) {
            case SUPPLY -> {
                key = MetricKey.CONTRACTS_CONCLUDED;
                if (unit == MetricsUnit.DAY && period.days() >= 14) {
                    unit = MetricsUnit.WEEK;
                }
                value = b -> (double) current.concluded.stream()
                        .map(r -> (LocalDateTime) r[1]).filter(at -> in(at, b)).count();
            }
            case GROUP_BUY -> {
                key = MetricKey.SELLING_GROUP_BUYS;
                value = b -> (double) current.groupBuys.stream().filter(g -> g.sellingDuring(b[0], b[1])).count();
            }
            case DEMAND -> {
                key = MetricKey.BUYERS;
                value = b -> (double) current.lines.stream().filter(l -> in(l.paidAt(), b))
                        .map(PaidLine::userId).distinct().count();
            }
            case OPERATION -> {
                key = MetricKey.PROCESSED;
                value = b -> (double) current.processedTimes.stream().filter(t -> in(t, b)).count();
            }
            default -> {
                key = MetricKey.GMV;
                value = b -> (double) current.lines.stream().filter(l -> in(l.paidAt(), b)).mapToLong(PaidLine::amount).sum();
            }
        }
        buckets = MetricsPeriodResolver.buckets(period.from(), period.to(), unit);
        MetricsUnit labelUnit = unit;
        List<AdminMetricsDto.Point> points = buckets.stream()
                .map(b -> new AdminMetricsDto.Point(b[0], MetricsPeriodResolver.bucketLabel(b[0], labelUnit), value.apply(b)))
                .toList();
        MetricKey totalKey = tab == MetricsTab.GROUP_BUY ? MetricKey.GROUP_BUYS_RUN : key;
        double total = nz(current.get(totalKey).value());
        double delta = total - nz(previous.get(totalKey).value());
        return new AdminMetricsDto.Chart(key.name(), key.getLabel(), unit.name(), total, delta, points);
    }

    private static boolean in(LocalDateTime at, LocalDateTime[] bucket) {
        return at != null && !at.isBefore(bucket[0]) && at.isBefore(bucket[1]);
    }

    private static double nz(Double value) {
        return value == null ? 0 : value;
    }

    // ------------------------------------------------------------------ 분해 표

    private AdminMetricsDto.Breakdown breakdown(MetricsTab tab, Facts f) {
        Map<Long, GroupBuyLine> groupBuys = f.groupBuys.stream()
                .collect(Collectors.toMap(GroupBuyLine::id, Function.identity(), (a, b) -> a));
        return switch (tab) {
            case SUPPLY -> creatorsTop(f, groupBuys);
            case GROUP_BUY -> groupBuyPerformance(f, groupBuys);
            case DEMAND -> showroomConversion(f);
            case OPERATION -> queueBreakdown(f);
            default -> groupBuysTop(f, groupBuys);
        };
    }

    /** 핵심 — 공구별 거래액 상위 5(주문 · 거래액 · 반품·교환율). */
    private AdminMetricsDto.Breakdown groupBuysTop(Facts f, Map<Long, GroupBuyLine> groupBuys) {
        Map<Long, Set<Long>> orders = new HashMap<>();
        f.lines.forEach(l -> orders.computeIfAbsent(l.groupBuyId(), k -> new HashSet<>()).add(l.orderId()));
        Map<Long, Long> claims = f.claims.stream().filter(c -> c.groupBuyId() != null)
                .collect(Collectors.groupingBy(ClaimLine::groupBuyId, Collectors.counting()));
        List<List<String>> rows = f.gmvByGroupBuy.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed()).limit(TOP)
                .map(e -> {
                    GroupBuyLine g = groupBuys.get(e.getKey());
                    long delivered = f.delivered.getOrDefault(e.getKey(), 0L);
                    return List.of(g == null ? "#" + e.getKey() : g.name(), pair(g),
                            String.valueOf(orders.getOrDefault(e.getKey(), Set.of()).size()), won(e.getValue()),
                            delivered == 0 ? "—" : percent(claims.getOrDefault(e.getKey(), 0L) * 100.0 / delivered));
                })
                .toList();
        return new AdminMetricsDto.Breakdown("공구별 거래액 상위 5", List.of("공구", "브랜드 × 인플루언서", "주문", "거래액", "반품·교환율"),
                rows);
    }

    /** 공급 — 인플루언서별 공구 상위 5(공구 수 · 거래액 · 누적 공구 · 첫/재공구). */
    private AdminMetricsDto.Breakdown creatorsTop(Facts f, Map<Long, GroupBuyLine> groupBuys) {
        Map<Long, Long> gmv = f.lines.stream()
                .collect(Collectors.groupingBy(PaidLine::creatorId, Collectors.summingLong(PaidLine::amount)));
        Map<Long, Set<Long>> runs = new HashMap<>();
        f.lines.forEach(l -> runs.computeIfAbsent(l.creatorId(), k -> new HashSet<>()).add(l.groupBuyId()));
        Map<Long, Long> cumulative = f.groupBuys.stream().filter(g -> g.openedAt().isBefore(f.to))
                .collect(Collectors.groupingBy(GroupBuyLine::creatorId, Collectors.counting()));
        Map<Long, String> names = f.groupBuys.stream()
                .collect(Collectors.toMap(GroupBuyLine::creatorId, GroupBuyLine::creatorName, (a, b) -> a));
        List<List<String>> rows = gmv.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed()).limit(TOP)
                .map(e -> List.of(names.getOrDefault(e.getKey(), "#" + e.getKey()),
                        String.valueOf(runs.getOrDefault(e.getKey(), Set.of()).size()), won(e.getValue()),
                        String.valueOf(cumulative.getOrDefault(e.getKey(), 0L)),
                        f.returningCreators.contains(e.getKey()) ? "재공구" : "첫 공구"))
                .toList();
        return new AdminMetricsDto.Breakdown("인플루언서별 공구 상위 5", List.of("인플루언서", "공구 수", "거래액", "누적 공구", "구분"),
                rows);
    }

    /**
     * 공구 — 공구별 성과 상위 5(브랜드 × 인플루언서 · 거래액 · 물량 달성 · ROI). 둘째 열은 핵심 탭 「공구별 거래액」과 같다(시안 21 K3).
     * 물량 · ROI 는 기간 중 종료된 공구만 판정한다.
     */
    private AdminMetricsDto.Breakdown groupBuyPerformance(Facts f, Map<Long, GroupBuyLine> groupBuys) {
        List<List<String>> rows = f.gmvByGroupBuy.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed()).limit(TOP)
                .map(e -> {
                    GroupBuyLine g = groupBuys.get(e.getKey());
                    Double achievement = f.achievement.get(e.getKey());
                    Double roi = f.roi.get(e.getKey());
                    return List.of(g == null ? "#" + e.getKey() : g.name(), pair(g), won(e.getValue()),
                            achievement == null ? "—" : percent(achievement) + (achievement < 100 ? " · 미달" : ""),
                            roi == null ? "—" : roi + "배");
                })
                .toList();
        return new AdminMetricsDto.Breakdown("공구별 성과 상위 5",
                List.of("공구", "브랜드 × 인플루언서", "거래액", "물량 달성", "ROI"), rows);
    }

    /** 「브랜드 × 인플루언서」 — 공구를 모르면 빈 칸. */
    private static String pair(GroupBuyLine g) {
        return g == null ? "" : g.brandName() + " × " + g.creatorName();
    }

    /**
     * 수요 — 쇼룸별 전환(팔로워 · 구매 소비자 · 전환율) · <b>구매 소비자 순</b> 상위 5곳(시안 21 · 55 계획서 A14). 한 사람이 여러 쇼룸에서
     * 사면 쇼룸마다 센다. 같은 수면 인플루언서 id 순이다.
     */
    static AdminMetricsDto.Breakdown showroomConversion(Facts f) {
        Map<Long, Set<Long>> buyers = new HashMap<>();
        f.lines.forEach(l -> buyers.computeIfAbsent(l.creatorId(), k -> new HashSet<>()).add(l.userId()));
        Map<Long, String> names = f.groupBuys.stream()
                .collect(Collectors.toMap(GroupBuyLine::creatorId, GroupBuyLine::creatorName, (a, b) -> a));
        List<List<String>> rows = buyers.keySet().stream()
                .map(id -> new Object[]{id, f.followers.getOrDefault(id, 0L), (long) buyers.get(id).size(),
                        f.convertedByCreator.getOrDefault(id, 0L)})
                .sorted(Comparator.comparingLong((Object[] r) -> (long) r[2]).reversed()
                        .thenComparingLong(r -> (Long) r[0]))
                .limit(TOP)
                .map(r -> List.of(names.getOrDefault((Long) r[0], "#" + r[0]), String.valueOf(r[1]), String.valueOf(r[2]),
                        (long) r[1] == 0 ? "—" : percent((long) r[3] * 100.0 / (long) r[1])))
                .toList();
        return new AdminMetricsDto.Breakdown("쇼룸별 전환 · 구매 소비자 순 · 상위 5곳",
                List.of("인플루언서(쇼룸)", "팔로워", "구매 소비자", "전환율"), rows);
    }

    /** 운영 — 큐별 처리(처리 수 · 평균 소요 · 기한 초과). 배송 예외는 처리 시각이 없어 「—」. */
    private AdminMetricsDto.Breakdown queueBreakdown(Facts f) {
        Map<String, String> labels = new java.util.LinkedHashMap<>();
        labels.put("INQUIRY", "1:1 문의");
        labels.put("SELLER_APPLICATION", "브랜드 입점 심사");
        labels.put("CREATOR_APPLICATION", "인플루언서 입점 심사");
        labels.put("OPEN_REVIEW", "오픈 승인");
        labels.put("POST_REPORT", "신고 게시물");
        labels.put("CHANGE_REQUEST", "변경 요청");
        List<List<String>> rows = new ArrayList<>();
        labels.forEach((queue, label) -> {
            List<LocalDateTime[]> pairs = f.pairs.getOrDefault(queue, List.of());
            String avg = pairs.isEmpty() ? "—" : MetricsFactsCalculator.round1(pairs.stream()
                    .mapToLong(p -> Duration.between(p[0], p[1]).toMinutes()).average().orElse(0) / 60.0) + "시간";
            long over = "POST_REPORT".equals(queue) ? 0 : pairs.stream().filter(p -> !calculator.withinSla(queue, p)).count();
            rows.add(List.of(label, String.valueOf(pairs.size()), avg, "POST_REPORT".equals(queue) ? "—" : String.valueOf(over)));
        });
        rows.add(List.of("배송 예외", "—", "—", "—"));
        return new AdminMetricsDto.Breakdown("큐별 처리", List.of("큐", "처리", "평균 소요", "기한 초과"), rows);
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    private static String percent(double value) {
        return MetricsFactsCalculator.round1(value) + "%";
    }

    private record Cached(Facts facts, LocalDateTime expiresAt) {
    }
}
