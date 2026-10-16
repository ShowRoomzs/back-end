package showroomz.api.creator.performance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.creator.performance.dto.CreatorPerformanceDto;
import showroomz.api.creator.performance.type.CreatorPerformanceStatus;
import showroomz.api.creator.performance.type.RewardStage;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.service.port.GroupBuyThreadGateway;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.performance.service.PerformanceFacts;
import showroomz.domain.performance.type.PerformanceSource;
import showroomz.domain.performance.type.PerformanceTone;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader.AdjustmentSummary;
import showroomz.domain.settlement.adjustment.type.AdjustmentStatus;
import showroomz.domain.settlement.adjustment.type.ProposalStatus;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.domain.settlement.entity.Settlement;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 스튜디오 성과 상세(D1 ~ D4) 조립(49 스튜디오 성과 설계서 3-4 ~ 3-7) — 공통 {@link PerformanceFacts} 에서 스튜디오 블록만 만든다.
 *
 * <ul>
 *     <li>「예정」은 정산이 없는 동안({@code projected}) 판매 · 소비자 블록에만 붙는다 — 방문 · 게시물 · 팔로워 수는 확정값이다(0-4).</li>
 *     <li>돈은 정산 행이 정본이다 — 실지급은 {@code creator_payout_amount} 그대로이고 계산하지 않는다(3-6).</li>
 *     <li>응답 버튼 · 권한은 내리지 않는다 — 조정 협의 응답은 이슈 스레드에서만(§47-0).</li>
 *     <li>원가 · 배송 운영 품질 · 취소 · 반품 사유 분해(파트너 전용)는 읽지 않는다 — DTO 에 자리가 없다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CreatorPerformanceDetailAssembler {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM.dd");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final SettlementAdjustmentReader adjustmentReader;
    private final GroupBuyThreadGateway threadGateway;

    public CreatorPerformanceDto.Detail assemble(GroupBuy groupBuy, Creator creator, PerformanceFacts f) {
        CreatorPerformanceStatus status = CreatorPerformanceStatus.of(f.status());
        RewardStage stage = RewardStage.of(status);
        boolean estimated = f.projected();
        Settlement s = f.settlement();
        AdjustmentSummary adjustment = s == null ? null
                : adjustmentReader.findBySettlementId(s.getId()).orElse(null);
        boolean agreed = status == CreatorPerformanceStatus.SETTLED && adjustment != null
                && adjustment.status() == AdjustmentStatus.AGREED && s.getRewardAmount() != s.getOriginalRewardAmount();

        PerformanceFacts.Orders o = f.orders();
        PerformanceFacts.Traffic t = f.traffic();
        long originalReward = s != null ? s.getOriginalRewardAmount() : o.contractRewardAmount();
        Long finalReward = stage == RewardStage.CONFIRMED ? s.getRewardAmount() : null;
        Long totalReward = finalReward == null ? null : finalReward + f.fixedFeeAmount();
        Long agreedDelta = agreed ? s.getRewardAmount() - s.getOriginalRewardAmount() : null;

        CreatorPerformanceDto.Money salesMoney = new CreatorPerformanceDto.Money(stage, finalReward, originalReward,
                agreed ? "원래 %s원 + 조정 %s원".formatted(won(originalReward), signedWon(agreedDelta))
                        : estimated ? "상품별 리워드율 합산 · 예정" : "상품별 리워드율 합산");
        CreatorPerformanceDto.Money fixedMoney = new CreatorPerformanceDto.Money(RewardStage.CONFIRMED,
                f.fixedFeeAmount(), null, "브랜드 직접 지급");
        CreatorPerformanceDto.Money totalMoney = new CreatorPerformanceDto.Money(stage, totalReward, null,
                agreed ? "판매 리워드 + 고정 지급비 · 조정 합의 반영" : "판매 리워드 + 고정 지급비");

        long buyers = f.buyers().size();
        long repeat = f.buyers().stream().filter(PerformanceFacts.Buyer::repeatWithCreator).count();
        long followerBuyers = f.buyers().stream().filter(PerformanceFacts.Buyer::followerAtPurchase).count();
        long female = f.buyers().stream().filter(b -> "FEMALE".equalsIgnoreCase(b.gender())).count();
        long male = f.buyers().stream().filter(b -> "MALE".equalsIgnoreCase(b.gender())).count();

        CreatorPerformanceDto.DetailKpi kpi = new CreatorPerformanceDto.DetailKpi(totalMoney,
                new CreatorPerformanceDto.AmountFigure(o.confirmedSalesAmount(),
                        "구매확정 %s건 기준".formatted(num(o.confirmedCount())), estimated),
                new CreatorPerformanceDto.RateFigure(CreatorPerformanceRates.percent(o.confirmedCount(), t.visitorCount()),
                        "쇼룸 방문 %s명 대비 구매확정".formatted(num(t.visitorCount())), estimated),
                new CreatorPerformanceDto.RateFigure(CreatorPerformanceRates.percent(repeat, buyers),
                        "구매 소비자 %s명 중 %s명".formatted(num(buyers), num(repeat)), estimated),
                new CreatorPerformanceDto.FollowerDelta(f.followers().delta(), f.followers().countAtEnd()),
                CreatorPerformanceDistribution.genderRatio(female, male), estimated);

        return new CreatorPerformanceDto.Detail(
                header(groupBuy, f, status),
                notice(status, f, adjustment, agreed, s),
                new CreatorPerformanceDto.RewardBar(salesMoney, fixedMoney, totalMoney),
                kpi,
                traffic(f),
                funnel(f, estimated),
                sales(f, estimated, agreedDelta),
                new CreatorPerformanceDto.Consumers(estimated, buyers, buyers - repeat, repeat, followerBuyers,
                        CreatorPerformanceRates.percent(followerBuyers, buyers),
                        CreatorPerformanceDistribution.of(f.buyers().stream()
                                .map(b -> new CreatorPerformanceDistribution.Person(b.gender(), b.birthday())).toList(),
                                f.periodEnd().toLocalDate())),
                followers(f, estimated),
                reward(f, stage, status, s, adjustment, agreed, originalReward, finalReward, totalReward, creator),
                new CreatorPerformanceDto.Recollaboration(groupBuy.getMarket().getId(),
                        threadGateway.findPairThreadId(groupBuy).orElse(null)));
    }

    private static CreatorPerformanceDto.Header header(GroupBuy g, PerformanceFacts f, CreatorPerformanceStatus status) {
        return new CreatorPerformanceDto.Header(g.getId(), g.getGroupBuyNumber(), g.getContract().getTitle(),
                g.getMarket().getMarketName(), g.getMarket().getId(), g.getContract().getId(), f.periodStart(),
                f.periodEnd(), status, status.getLabel(), status.getTone());
    }

    /** 상단 안내 — 집계 중 미종결은 중립(경고 · 정보색 금지) · 조정 협의는 경고 · 합의 반영은 중립(§49-3). */
    private static CreatorPerformanceDto.Notice notice(CreatorPerformanceStatus status, PerformanceFacts f,
                                                      AdjustmentSummary adjustment, boolean agreed, Settlement s) {
        if (status == CreatorPerformanceStatus.ADJUSTING && adjustment != null) {
            return new CreatorPerformanceDto.Notice(PerformanceTone.WARNING,
                    "정산 조정 협의 중이에요. 리워드는 브랜드와 합의한 금액으로 확정되고, 합의 기한(%s)까지 합의가 없으면 원래 금액으로 확정돼요. "
                            .formatted(adjustment.deadlineAt().format(MONTH_DAY))
                            + "판매 · 유입 · 소비자 지표는 확정값입니다.",
                    "ISSUE_THREAD", adjustment.threadId());
        }
        if (agreed) {
            return new CreatorPerformanceDto.Notice(PerformanceTone.NEUTRAL,
                    "정산 조정이 합의되어 반영됐어요. %s 금액에 %s 양측이 동의해 판매 리워드가 %s원 → %s원(%s원)으로 확정됐습니다.".formatted(
                            agreedProposalLabel(adjustment),
                            adjustment.closedAt() == null ? "" : adjustment.closedAt().format(DATE),
                            won(s.getOriginalRewardAmount()), won(s.getRewardAmount()),
                            signedWon(s.getRewardAmount() - s.getOriginalRewardAmount())),
                    "ISSUE_THREAD_HISTORY", adjustment.threadId());
        }
        PerformanceFacts.Closure closure = f.closure();
        if (status == CreatorPerformanceStatus.COLLECTING && f.projected() && closure != null
                && closure.unclosedCount() > 0) {
            String head = closure.openReturnCount() > 0
                    ? "미처리 반품 %s건이 남아 있습니다.".formatted(num(closure.openReturnCount()))
                    : "처리 중인 주문 %s건이 남아 있습니다.".formatted(num(closure.unclosedCount()));
            return new CreatorPerformanceDto.Notice(PerformanceTone.NEUTRAL,
                    head + " 아래 «예정» 표시 값은 처리 결과에 따라 줄어들 수 있습니다.", null, null);
        }
        return null;
    }

    private static CreatorPerformanceDto.Traffic traffic(PerformanceFacts f) {
        PerformanceFacts.Traffic t = f.traffic();
        Map<PerformanceSource, Long> bySource = t.visitorsBySource();
        List<CreatorPerformanceDto.SourceItem> sources = new ArrayList<>();
        for (PerformanceSource source : PerformanceSource.values()) {
            long visitors = bySource == null ? 0 : bySource.getOrDefault(source, 0L);
            sources.add(new CreatorPerformanceDto.SourceItem(source.name(), source.getLabel(), visitors,
                    CreatorPerformanceRates.percent(visitors, t.visitorCount())));
        }
        PerformanceFacts.PostFacts p = f.post();
        CreatorPerformanceDto.PostBlock post = p == null ? null : new CreatorPerformanceDto.PostBlock(p.postId(),
                p.impressionCount(), p.uniqueViewerCount(), p.productViewCount(),
                CreatorPerformanceRates.percent(p.productViewCount(), p.impressionCount()), p.orderCount(),
                CreatorPerformanceRates.percent(p.orderCount(), p.productViewCount()), p.likeCount(), null);
        return new CreatorPerformanceDto.Traffic(t.visitorCount(), sources, post);
    }

    private static CreatorPerformanceDto.Funnel funnel(PerformanceFacts f, boolean estimated) {
        PerformanceFacts.Orders o = f.orders();
        long visitors = f.traffic().visitorCount();
        return new CreatorPerformanceDto.Funnel(visitors, o.orderCount(), Math.max(0, visitors - o.orderCount()),
                o.confirmedCount(), o.cancelledCount(), o.returnedCount(), o.deliveryExceptionCount(),
                CreatorPerformanceRates.percent(o.confirmedCount(), o.orderCount()),
                CreatorPerformanceRates.percent(o.confirmedCount(), visitors), estimated);
    }

    private static CreatorPerformanceDto.Sales sales(PerformanceFacts f, boolean estimated, Long agreedDelta) {
        List<CreatorPerformanceDto.ProductRow> rows = f.products().stream()
                .map(p -> new CreatorPerformanceDto.ProductRow(p.productId(), p.productName(), p.detailViewCount(),
                        p.orderCount(), p.confirmedQuantity(), p.confirmedAmount(), p.rewardRate(), p.rewardAmount()))
                .toList();
        return new CreatorPerformanceDto.Sales(estimated, rows, f.orders().contractRewardAmount(), agreedDelta,
                CreatorPerformanceHighlights.of(rows, estimated));
    }

    /** 팔로워 · 비팔로워 비교표 — 판정은 사건 시각(첫 방문 · 첫 조회 · 결제). 합계 행은 두지 않는다(위 패널에 총량이 있다). */
    private static CreatorPerformanceDto.Followers followers(PerformanceFacts f, boolean estimated) {
        PerformanceFacts.Traffic t = f.traffic();
        PerformanceFacts.Orders o = f.orders();
        long followerVisitors = t.visitorFollowerCount();
        long otherVisitors = Math.max(0, t.visitorCount() - followerVisitors);
        long followerViewers = f.post() == null ? 0 : f.post().followerViewerCount();
        long otherViewers = f.post() == null ? 0 : Math.max(0, f.post().uniqueViewerCount() - followerViewers);
        CreatorPerformanceDto.Side follower = new CreatorPerformanceDto.Side(followerVisitors, followerViewers,
                o.orderFollowerCount(), CreatorPerformanceRates.percent(o.confirmedFollowerCount(), followerVisitors));
        CreatorPerformanceDto.Side other = new CreatorPerformanceDto.Side(otherVisitors, otherViewers,
                Math.max(0, o.orderCount() - o.orderFollowerCount()),
                CreatorPerformanceRates.percent(Math.max(0, o.confirmedCount() - o.confirmedFollowerCount()), otherVisitors));
        return new CreatorPerformanceDto.Followers(follower, other, f.followers().countAtEnd(), f.followers().delta(),
                CreatorPerformanceRates.percent(followerVisitors, t.visitorCount()), estimated);
    }

    private static CreatorPerformanceDto.Reward reward(PerformanceFacts f, RewardStage stage,
                                                      CreatorPerformanceStatus status, Settlement s,
                                                      AdjustmentSummary adjustment, boolean agreed, long originalReward,
                                                      Long finalReward, Long totalReward, Creator creator) {
        CreatorPerformanceDto.Offer mine = null;
        CreatorPerformanceDto.Offer theirs = null;
        if (status == CreatorPerformanceStatus.ADJUSTING && adjustment != null) {
            mine = adjustment.latestOf(SettlementParty.CREATOR)
                    .map(p -> new CreatorPerformanceDto.Offer(p.rewardAmount(), p.proposedAt())).orElse(null);
            theirs = adjustment.latestOf(SettlementParty.SELLER)
                    .map(p -> new CreatorPerformanceDto.Offer(p.rewardAmount(), p.proposedAt())).orElse(null);
        }
        CreatorPerformanceDto.Adjustment applied = agreed ? new CreatorPerformanceDto.Adjustment(
                s.getRewardAmount() - s.getOriginalRewardAmount(), adjustment.closedAt(), agreedProposalLabel(adjustment))
                : null;

        CreatorBusinessType type = s != null ? s.getCreatorBusinessType()
                : creator.getBusinessType() != null ? creator.getBusinessType() : CreatorBusinessType.INDIVIDUAL;
        String note = type == CreatorBusinessType.BUSINESS ? "사업자 · 부가세 포함 금액"
                : "비사업자 · 3.3% 원천징수는 SHOWROOMZ가 신고";
        CreatorPerformanceDto.Payout payout = new CreatorPerformanceDto.Payout(type,
                stage == RewardStage.CONFIRMED ? s.getCreatorPayoutAmount() : null, note);

        return new CreatorPerformanceDto.Reward(stage, f.orders().confirmedSalesAmount(), originalReward, mine, theirs,
                applied, finalReward, f.fixedFeeAmount(), totalReward, payout,
                s == null ? null : s.getId(),
                s == null ? null : s.getStatus().toPartyStatus().getLabel(),
                adjustment == null ? null : adjustment.threadId(),
                status == CreatorPerformanceStatus.ADJUSTING && adjustment != null ? adjustment.deadlineAt() : null);
    }

    /** 합의된 제안을 낸 쪽 — 「브랜드 제안」 · 「내 요청」. 찾지 못하면 「합의」. */
    private static String agreedProposalLabel(AdjustmentSummary adjustment) {
        return adjustment.proposals().stream()
                .filter(p -> p.status() == ProposalStatus.ACCEPTED)
                .reduce((a, b) -> b)
                .map(p -> p.proposerType() == SettlementParty.SELLER ? "브랜드 제안" : "내 요청")
                .orElse("합의");
    }

    private static String num(long value) {
        return CreatorPerformanceHighlights.number(value);
    }

    private static String won(long value) {
        return CreatorPerformanceHighlights.number(value);
    }

    private static String signedWon(Long value) {
        if (value == null) {
            return "";
        }
        return (value >= 0 ? "+" : "−") + CreatorPerformanceHighlights.number(Math.abs(value));
    }
}
