package showroomz.api.admin.metrics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.metrics.type.MetricKey;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.metrics.repository.AdminOperationsQueryRepository;
import showroomz.domain.metrics.repository.MetricsQueryRepository;
import showroomz.domain.metrics.repository.MetricsQueryRepository.ClaimLine;
import showroomz.domain.metrics.repository.MetricsQueryRepository.GroupBuyLine;
import showroomz.domain.metrics.repository.MetricsQueryRepository.PaidLine;
import showroomz.domain.post.type.PostPolicyClause;
import showroomz.global.config.properties.AdminProperties;
import showroomz.global.config.properties.GroupBuyProperties;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.utils.BusinessCalendar;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 운영 지표 계산(1017 설계서 7-2) — 기간 [from, to) 의 원천 행을 읽어 지표 값을 만든다. 공구당 지표는 중앙값(ⓒ), 재공구 판정
 * 60일(ⓐ · {@code admin.metrics.regroupbuy-window-days}). 탭 조립(차트 · 분해 표)이 같은 원천을 다시 쓰도록 {@link Facts}에 함께 담는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetricsFactsCalculator {

    /** 이 이상 걸린 계산은 WARN 으로 단계별 소요를 남긴다. */
    static final long SLOW_LOG_MILLIS = 3_000;
    static final List<String> SLA_QUEUES = List.of("INQUIRY", "OPEN_REVIEW", "SELLER_APPLICATION", "CREATOR_APPLICATION",
            "CHANGE_REQUEST", "POST_REPORT");
    /** 활성 인플루언서 · 브랜드 설명의 정의 부분(시안 21 K2) — 셈은 「기간 중 결제가 1건 이상 있는 공구」다({@link MetricKey}). */
    static final String ACTIVE_LEAD = "기간 중 공구 1건 이상";

    private final MetricsQueryRepository repository;
    private final AdminOperationsQueryRepository operationsRepository;
    private final BusinessCalendar businessCalendar;
    private final AdminProperties adminProperties;
    private final GroupBuyProperties groupBuyProperties;
    private final SettlementProperties settlementProperties;

    public Facts compute(LocalDateTime from, LocalDateTime to, LocalDateTime now) {
        Facts facts = new Facts(from, to);
        Map<String, Long> elapsed = new LinkedHashMap<>();
        timed(elapsed, "trade", () -> trade(facts));
        timed(elapsed, "supply", () -> supply(facts, now));
        timed(elapsed, "groupBuys", () -> groupBuys(facts));
        timed(elapsed, "demand", () -> demand(facts));
        timed(elapsed, "operation", () -> operation(facts));
        // 단계별 소요(58 계획 1-1) — 느린 계산은 운영 로그(INFO 이상)에 남아 원인 단계를 바로 찾는다.
        long total = elapsed.values().stream().mapToLong(Long::longValue).sum();
        if (total >= SLOW_LOG_MILLIS) {
            log.warn("[운영 지표] 계산 지연 {}ms · 기간 {} ~ {} · 단계 {}", total, from, to, elapsed);
        } else {
            log.debug("[운영 지표] 계산 {}ms · 기간 {} ~ {} · 단계 {}", total, from, to, elapsed);
        }
        return facts;
    }

    private static void timed(Map<String, Long> elapsed, String step, Runnable body) {
        long started = System.nanoTime();
        body.run();
        elapsed.put(step, (System.nanoTime() - started) / 1_000_000);
    }

    // ------------------------------------------------------------------ 거래(핵심)

    private void trade(Facts f) {
        f.lines = repository.paidLines(f.from, f.to);
        f.delivered = repository.deliveredByGroupBuy(f.from, f.to);
        f.claims = repository.claims(f.from, f.to);
        long gmv = f.lines.stream().mapToLong(PaidLine::amount).sum();
        Map<Long, Long> gmvByGroupBuy = f.lines.stream()
                .collect(Collectors.groupingBy(PaidLine::groupBuyId, Collectors.summingLong(PaidLine::amount)));
        f.gmvByGroupBuy = gmvByGroupBuy;
        long deliveredTotal = f.delivered.values().stream().mapToLong(Long::longValue).sum();
        f.put(MetricKey.GMV, Value.of(gmv));
        f.put(MetricKey.ORDERS, Value.of(f.lines.stream().map(PaidLine::orderId).distinct().count()));
        // 설명 「중앙값 · 8건」(시안 21 K1) — 건수 = 중앙값을 낸 공구 수(기간 중 결제가 있는 공구)
        f.put(MetricKey.GMV_MEDIAN, Value.nullable(median(gmvByGroupBuy.values()))
                .withNote("중앙값 · " + gmvByGroupBuy.size() + "건"));
        // 설명 「베타 0% · 정상 요율 2% 적용 시」 — 베타 = 지금 정산에 적용되는 요율 · 정상 요율 = 이 예상 값의 계산 요율
        BigDecimal standardFeeRate = adminProperties.getMetrics().getStandardFeeRate();
        f.put(MetricKey.FEE_REVENUE, Value.of(BigDecimal.valueOf(gmv).multiply(standardFeeRate).longValue())
                .withNote("베타 " + percentText(settlementProperties.getPlatformFeeRate()) + " · 정상 요율 "
                        + percentText(standardFeeRate) + " 적용 시"));
        f.put(MetricKey.RETURN_EXCHANGE_RATE, Value.rate(f.claims.size(), deliveredTotal));
        long brandFault = f.claims.stream().filter(c -> "RETURN".equals(c.type()) && c.sellerFault()).count();
        f.put(MetricKey.BRAND_FAULT_RETURN_RATE, Value.rate(brandFault, deliveredTotal));
    }

    // ------------------------------------------------------------------ 공급

    private void supply(Facts f, LocalDateTime now) {
        Set<Long> creators = f.lines.stream().map(PaidLine::creatorId).collect(Collectors.toSet());
        Set<Long> brands = f.lines.stream().map(PaidLine::marketId).collect(Collectors.toSet());
        Set<Long> returningCreators = repository.creatorsOpenedBefore(creators, f.from);
        Set<Long> returningBrands = repository.marketsOpenedBefore(brands, f.from);
        f.returningCreators = returningCreators;
        f.cumulativeByCreator = repository.openedCountByCreator(creators, f.to);
        // 설명 = 정의 + 이번 기간 값(시안 21 K2 「기간 중 공구 1건 이상 · 신규 1 · 재공구 4」)
        f.put(MetricKey.ACTIVE_CREATORS, Value.of(creators.size()).withNote(ACTIVE_LEAD + " · 신규 "
                + (creators.size() - returningCreators.size()) + " · 재공구 " + returningCreators.size()));
        f.put(MetricKey.ACTIVE_BRANDS, Value.of(brands.size()).withNote(ACTIVE_LEAD + " · 신규 "
                + (brands.size() - returningBrands.size()) + " · 재공구 " + returningBrands.size()));

        int window = adminProperties.getMetrics().getRegroupbuyWindowDays();
        f.groupBuys = repository.openedGroupBuys(f.from, f.to.plusDays(window));
        long regroup = 0;
        long judged = 0;
        for (GroupBuyLine g : endedIn(f)) {
            boolean next = f.groupBuys.stream().anyMatch(o -> !o.id().equals(g.id()) && o.creatorId().equals(g.creatorId())
                    && o.startAt().isAfter(g.endAt()) && !o.startAt().isAfter(g.endAt().plusDays(window)));
            if (next || !g.endAt().plusDays(window).isAfter(now)) {
                judged++;
                regroup += next ? 1 : 0;
            }
        }
        f.put(MetricKey.CREATOR_REGROUPBUY_RATE, Value.rate(regroup, judged));

        f.concluded = repository.concludedContracts(f.from, f.to);
        Set<Long> concludedBrands = new HashSet<>();
        for (Object[] row : f.concluded) {
            concludedBrands.add((Long) row[0]);
        }
        Set<Long> earlierBrands = repository.marketsConcludedBefore(concludedBrands, f.from);
        long recontract = concludedBrands.stream().filter(earlierBrands::contains).count();
        f.put(MetricKey.BRAND_RECONTRACT_RATE, Value.rate(recontract, concludedBrands.size()));
        f.put(MetricKey.CONTRACTS_CONCLUDED, Value.of(f.concluded.size()));

        long[] connection = repository.connectionConversion(f.from, f.to);
        f.put(MetricKey.CONNECTION_CONVERSION, Value.rate(connection[1], connection[0]));

        List<Double> leadDays = f.groupBuys.stream()
                .filter(g -> g.concludedAt() != null && !g.openedAt().isBefore(f.from) && g.openedAt().isBefore(f.to))
                .map(g -> round1(Duration.between(g.concludedAt(), g.openedAt()).toHours() / 24.0))
                .toList();
        f.put(MetricKey.CONCLUDE_TO_OPEN_DAYS, Value.nullable(medianDouble(leadDays)));

        // 신규 입점 — 값 칸 「1곳 · 3명」(시안 21 K2). 비교 기간도 같은 Facts 계산이라 같은 형식으로 나뉜다. 설명은 정의 문구.
        long[] onboarding = repository.approvedOnboarding(f.from, f.to);
        f.put(MetricKey.NEW_ONBOARDING, Value.of(onboarding[0] + onboarding[1])
                .withBreakdown(onboarding[0] + "곳 · " + onboarding[1] + "명"));
    }

    // ------------------------------------------------------------------ 공구

    private void groupBuys(Facts f) {
        long inProgress = 0;
        long ended = 0;
        for (GroupBuyLine g : f.groupBuys) {
            if (g.sellingDuring(f.from, f.to)) {
                if (g.terminal()) {
                    ended++;
                } else {
                    inProgress++;
                }
            }
        }
        f.put(MetricKey.GROUP_BUYS_RUN, Value.of(inProgress + ended).withNote("진행 중 " + inProgress + " · 종료 " + ended));

        List<GroupBuyLine> endedIn = endedIn(f);
        List<Long> endedIds = endedIn.stream().map(GroupBuyLine::id).toList();
        Map<Long, Long> sold = repository.soldQuantityByGroupBuy(endedIds);
        Map<Long, Long> gross = repository.grossByGroupBuy(endedIds);
        Map<Long, List<ContractItem>> items = repository.contractItemsByGroupBuy(endedIds);
        Map<Long, Long> rewards = repository.rewardByGroupBuy(endedIds);
        long achieved = 0;
        long judged = 0;
        List<Double> rois = new ArrayList<>();
        for (GroupBuyLine g : endedIn) {
            List<ContractItem> contractItems = items.getOrDefault(g.id(), List.of());
            Integer minQuantity = minQuantity(contractItems);
            if (minQuantity != null && minQuantity > 0) {
                judged++;
                boolean ok = sold.getOrDefault(g.id(), 0L) >= minQuantity;
                achieved += ok ? 1 : 0;
                f.achievement.put(g.id(), round1(sold.getOrDefault(g.id(), 0L) * 100.0 / minQuantity));
            }
            long g0 = gross.getOrDefault(g.id(), 0L);
            long reward = rewards.containsKey(g.id()) ? rewards.get(g.id()) : estimatedReward(contractItems, g0);
            long cost = reward + g.fixedFee();
            if (cost > 0) {
                double roi = Math.round(g0 * 100.0 / cost) / 100.0;
                rois.add(roi);
                f.roi.put(g.id(), roi);
            }
        }
        f.put(MetricKey.MIN_QUANTITY_RATE, Value.rate(achieved, judged));
        f.put(MetricKey.BRAND_ROI, Value.nullable(medianDouble(rois)));
        long[] suspensions = repository.countSuspensionsByKind(f.from, f.to);
        f.put(MetricKey.SUSPENSIONS, Value.of(suspensions[0] + suspensions[1])
                .withNote("신청 " + suspensions[0] + " · 직권 " + suspensions[1]));

        List<LocalDateTime[]> reviews = repository.processedPairs("OPEN_REVIEW", f.from, f.to);
        f.pairs.put("OPEN_REVIEW", reviews);
        int sla = groupBuyProperties.getOpenReview().getSlaBusinessDays();
        long over = reviews.stream().filter(p -> p[1].isAfter(dueAt(p[0], sla))).count();
        Double avg = reviews.isEmpty() ? null : round1(reviews.stream().mapToInt(this::businessDays).average().orElse(0));
        // 시안 21 K3 「SLA 3영업일 · 초과 0건」 — 기한 정의와 초과 건수를 함께
        f.put(MetricKey.OPEN_REVIEW_DAYS, Value.nullable(avg).withNote("SLA " + sla + "영업일 · 초과 " + over + "건"));
    }

    // ------------------------------------------------------------------ 수요

    private void demand(Facts f) {
        Set<Long> buyers = f.lines.stream().map(PaidLine::userId).collect(Collectors.toSet());
        f.put(MetricKey.BUYERS, Value.of(buyers.size()));
        Map<Long, Set<Long>> buyersByGroupBuy = new HashMap<>();
        f.lines.forEach(l -> buyersByGroupBuy.computeIfAbsent(l.groupBuyId(), k -> new HashSet<>()).add(l.userId()));
        f.put(MetricKey.BUYERS_MEDIAN, Value.nullable(median(buyersByGroupBuy.values().stream()
                .map(s -> (long) s.size()).toList())));

        // 팔로워 → 구매 — 결제 전에 그 공구 인플루언서를 팔로우한 구매자 ÷ 진행 쇼룸 팔로워(기간 끝 시점).
        Map<String, LocalDateTime> firstPaid = new HashMap<>();
        for (PaidLine l : f.lines) {
            firstPaid.merge(l.creatorId() + ":" + l.userId(), l.paidAt(), (a, b) -> a.isBefore(b) ? a : b);
        }
        Set<Long> creators = f.lines.stream().map(PaidLine::creatorId).collect(Collectors.toSet());
        Map<String, LocalDateTime> follows = repository.firstFollows(creators, buyers);
        Set<Long> convertedUsers = new HashSet<>();
        for (Map.Entry<String, LocalDateTime> paid : firstPaid.entrySet()) {
            LocalDateTime followedAt = follows.get(paid.getKey());
            if (followedAt != null && followedAt.isBefore(paid.getValue())) {
                String[] key = paid.getKey().split(":");
                convertedUsers.add(Long.valueOf(key[1]));
                f.convertedByCreator.merge(Long.valueOf(key[0]), 1L, Long::sum);
            }
        }
        f.followers = repository.followersAt(creators, f.to);
        long followerTotal = f.followers.values().stream().mapToLong(Long::longValue).sum();
        f.put(MetricKey.FOLLOWER_CONVERSION, Value.rate(convertedUsers.size(), followerTotal));
        f.put(MetricKey.REPURCHASE_RATE, Value.rate(repository.usersPaidBefore(buyers, f.from).size(), buyers.size()));

        Map<String, Long> signups = repository.signupsByProvider(f.from, f.to);
        long other = signups.entrySet().stream()
                .filter(e -> !List.of("KAKAO", "NAVER", "APPLE").contains(e.getKey()))
                .mapToLong(Map.Entry::getValue).sum();
        f.put(MetricKey.NEW_CONSUMERS, Value.of(signups.values().stream().mapToLong(Long::longValue).sum())
                .withNote("카카오 " + signups.getOrDefault("KAKAO", 0L) + " · 네이버 " + signups.getOrDefault("NAVER", 0L)
                        + " · Apple " + signups.getOrDefault("APPLE", 0L) + " · 그 외 " + other));
        f.put(MetricKey.CONSUMER_WITHDRAWALS, Value.of(repository.countUserWithdrawals(f.from, f.to)));
    }

    // ------------------------------------------------------------------ 운영

    private void operation(Facts f) {
        // 정산 조정 설명 「6건 중 1건 · 합의 대기 1건」(시안 21 K5) — 합의 대기 = 기간 중 열린 협의 중 아직 협의 중(조회 시점)
        long adjustments = repository.countAdjustmentsOpened(f.from, f.to);
        long settlements = repository.countSettlementsCreated(f.from, f.to);
        Value adjustmentRate = Value.rate(adjustments, settlements);
        if (adjustments > 0 || settlements > 0) {
            adjustmentRate = adjustmentRate.withNote(settlements + "건 중 " + adjustments + "건 · 합의 대기 "
                    + repository.countAdjustmentsAwaitingAgreement(f.from, f.to) + "건");
        }
        f.put(MetricKey.ADJUSTMENT_REQUEST_RATE, adjustmentRate);
        // 신고율 설명 — 「신고 18 ÷ 게시물 2,010 · 근거 서비스 운영정책 — 커뮤니티 장」(55 계획서 A15). 근거는 노출 중지 조항과 같은 장이다.
        long postReports = repository.countPostReports(f.from, f.to);
        long publishedPosts = repository.countPublishedPosts(f.to);
        f.put(MetricKey.POST_REPORT_RATE, Value.rate(postReports, publishedPosts)
                .withNote("신고 %,d ÷ 게시물 %,d · 근거 %s".formatted(postReports, publishedPosts,
                        PostPolicyClause.POLICY_CHAPTER)));
        f.processedTimes = operationsRepository.processedTimes(f.from, f.to);
        double days = Math.max(1.0, Duration.between(f.from, f.to).toMinutes() / 1440.0);
        f.put(MetricKey.PROCESSED, Value.of(f.processedTimes.size())
                .withNote("하루 평균 " + round1(f.processedTimes.size() / days) + "건"));

        long within = 0;
        long total = 0;
        for (String queue : SLA_QUEUES) {
            List<LocalDateTime[]> pairs = f.pairs.computeIfAbsent(queue, q -> repository.processedPairs(q, f.from, f.to));
            if ("POST_REPORT".equals(queue)) {
                continue; // 신고 검토는 기한 규칙이 없다 — 분해 표에만 쓴다
            }
            for (LocalDateTime[] p : pairs) {
                total++;
                within += withinSla(queue, p) ? 1 : 0;
            }
        }
        f.put(MetricKey.SLA_RATE, Value.rate(within, total));

        List<LocalDateTime[]> inquiries = f.pairs.get("INQUIRY");
        long overInquiries = inquiries.stream().filter(p -> !withinSla("INQUIRY", p)).count();
        Double avgHours = inquiries.isEmpty() ? null
                : round1(inquiries.stream().mapToLong(p -> Duration.between(p[0], p[1]).toMinutes()).average().orElse(0) / 60.0);
        // 시안 21 K5 「기한 24시간 · 초과 2건」 — 기한 정의와 초과 건수를 함께
        f.put(MetricKey.INQUIRY_FIRST_ANSWER_HOURS, Value.nullable(avgHours)
                .withNote("기한 " + adminProperties.getInquirySlaHours() + "시간 · 초과 " + overInquiries + "건"));
        List<String> refundFailures = repository.refundFailureReasons(f.from, f.to);
        f.put(MetricKey.REFUND_FAILURES, Value.of(refundFailures.size()).withNote(refundFailureNote(refundFailures)));
    }

    /**
     * 환불 실패 설명(시안 21 K5 「모두 카드 유효기간 만료」) — 저장된 마지막 실패 사유만 쓴다. 모든 건이 같은 사유면 「모두 …」, 아니면 많은 순
     * 두 사유 「… N건」 + 나머지 「외 K건」(사유가 지워진 건 포함). 사유가 하나도 남지 않았으면 {@code null}(정의 문구).
     */
    static String refundFailureNote(List<String> reasons) {
        Map<String, Long> byReason = new TreeMap<>();
        reasons.stream().filter(r -> r != null && !r.isBlank()).forEach(r -> byReason.merge(r.strip(), 1L, Long::sum));
        if (byReason.isEmpty()) {
            return null;
        }
        if (byReason.size() == 1 && byReason.values().iterator().next() == reasons.size()) {
            return "모두 " + byReason.keySet().iterator().next();
        }
        List<Map.Entry<String, Long>> top = byReason.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(2).toList();
        long shown = top.stream().mapToLong(Map.Entry::getValue).sum();
        String text = top.stream().map(e -> e.getKey() + " " + e.getValue() + "건").collect(Collectors.joining(" · "));
        return shown < reasons.size() ? text + " 외 " + (reasons.size() - shown) + "건" : text;
    }

    /** 요율 표기 — 0.02 → 「2%」 · 0 → 「0%」 · 0.025 → 「2.5%」. */
    static String percentText(BigDecimal rate) {
        BigDecimal percent = rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros();
        return (percent.signum() == 0 ? "0" : percent.toPlainString()) + "%";
    }

    boolean withinSla(String queue, LocalDateTime[] pair) {
        return switch (queue) {
            case "INQUIRY" -> Duration.between(pair[0], pair[1]).toMinutes() <= adminProperties.getInquirySlaHours() * 60L;
            case "OPEN_REVIEW" -> !pair[1].isAfter(dueAt(pair[0], groupBuyProperties.getOpenReview().getSlaBusinessDays()));
            case "SELLER_APPLICATION", "CREATOR_APPLICATION" ->
                    !pair[1].isAfter(dueAt(pair[0], adminProperties.getOnboardingSlaBusinessDays()));
            case "CHANGE_REQUEST" -> Duration.between(pair[0], pair[1]).toMinutes() <= adminProperties.getChangeRequestSlaHours() * 60L;
            default -> true;
        };
    }

    /** 처리 쌍의 영업일 소요 — 진입일 다음 날부터 처리일까지의 영업일 수(같은 날 처리 = 0). 오픈 승인 소요 · 큐별 처리가 같이 쓴다. */
    int businessDays(LocalDateTime[] pair) {
        return businessCalendar.businessDaysBetween(pair[0].toLocalDate(), pair[1].toLocalDate());
    }

    // ------------------------------------------------------------------

    private static List<GroupBuyLine> endedIn(Facts f) {
        return f.groupBuys.stream()
                .filter(g -> g.terminal() && !g.endAt().isBefore(f.from) && g.endAt().isBefore(f.to))
                .toList();
    }

    private LocalDateTime dueAt(LocalDateTime base, int businessDays) {
        return businessCalendar.addBusinessDays(base.toLocalDate(), businessDays).atTime(23, 59, 59);
    }

    private static Integer minQuantity(List<ContractItem> items) {
        if (items.isEmpty()) {
            return null;
        }
        int sum = 0;
        for (ContractItem item : items) {
            Integer min = item.getMinQuantity();
            if (min == null) {
                return null;
            }
            sum += min;
        }
        return sum;
    }

    /** 정산 전 리워드 추정 — 계약 항목 리워드율 평균 × 거래액. */
    private static long estimatedReward(List<ContractItem> items, long gross) {
        List<BigDecimal> rates = items.stream().map(ContractItem::getRewardRate).filter(r -> r != null).toList();
        if (rates.isEmpty()) {
            return 0;
        }
        double avg = rates.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        return Math.round(gross * avg / 100.0);
    }

    static Double median(Collection<Long> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    static Double medianDouble(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : round1((sorted.get(mid - 1) + sorted.get(mid)) / 2.0);
    }

    static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /**
     * 지표 값 — {@code value == null} 이면 계산할 수 없다(분모 0 · 대상 없음).
     *
     * @param numerator   비율의 분자(「4/6」의 4)
     * @param denominator 비율의 분모
     * @param breakdown   값을 나눠 적는 지표의 표시 문구(신규 입점 「1곳 · 3명」) — 현재 · 비교 기간이 같은 형식이다
     */
    public record Value(Double value, Long numerator, Long denominator, String note, String breakdown) {

        static Value of(long value) {
            return new Value((double) value, null, null, null, null);
        }

        static Value nullable(Double value) {
            return new Value(value, null, null, null, null);
        }

        static Value rate(long numerator, long denominator) {
            return new Value(denominator == 0 ? null : round1(numerator * 100.0 / denominator), numerator, denominator,
                    null, null);
        }

        Value withNote(String note) {
            return new Value(value, numerator, denominator, note, breakdown);
        }

        Value withBreakdown(String breakdown) {
            return new Value(value, numerator, denominator, note, breakdown);
        }

        public String countText() {
            return denominator == null ? null : numerator + "/" + denominator;
        }
    }

    /** 한 기간의 지표 값 + 탭 조립이 다시 쓰는 원천. */
    public static final class Facts {

        public final LocalDateTime from;
        public final LocalDateTime to;
        final Map<MetricKey, Value> values = new EnumMap<>(MetricKey.class);
        List<PaidLine> lines = List.of();
        Map<Long, Long> delivered = Map.of();
        List<ClaimLine> claims = List.of();
        Map<Long, Long> gmvByGroupBuy = Map.of();
        List<GroupBuyLine> groupBuys = List.of();
        Set<Long> returningCreators = Set.of();
        /** 기간 끝 전에 오픈한 공구 수 — 인플루언서별(「누적 공구」). */
        Map<Long, Long> cumulativeByCreator = Map.of();
        /** 기간 중 체결 계약 — [브랜드 id, 체결 시각]. */
        List<Object[]> concluded = List.of();
        Map<Long, Long> followers = Map.of();
        final Map<Long, Long> convertedByCreator = new HashMap<>();
        final Map<Long, Double> achievement = new HashMap<>();
        final Map<Long, Double> roi = new HashMap<>();
        final Map<String, List<LocalDateTime[]>> pairs = new LinkedHashMap<>();
        List<LocalDateTime> processedTimes = List.of();

        Facts(LocalDateTime from, LocalDateTime to) {
            this.from = from;
            this.to = to;
        }

        void put(MetricKey key, Value value) {
            values.put(key, value);
        }

        public Value get(MetricKey key) {
            return values.getOrDefault(key, Value.nullable(null));
        }
    }
}
