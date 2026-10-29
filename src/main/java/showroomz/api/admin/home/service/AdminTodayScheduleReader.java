package showroomz.api.admin.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.home.dto.AdminHomeDto;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.metrics.repository.AdminOperationsQueryRepository;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustment;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 어드민 홈 오늘 일정(1017 설계서 6-2) — 오늘 일어날 일을 시각순으로. 지급은 배치 시각({@code settlement.payout-hour})에 한 줄로 묶는다.
 * 직권 중단 집행은 자동이 아니다 — 운영자가 집행해야 해서 「운영자 집행 필요」로 적는다(시안 「자동 실행」과 다르다 · 12절).
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTodayScheduleReader {

    private final AdminOperationsQueryRepository opsRepository;
    private final SettlementProperties settlementProperties;

    public List<AdminHomeDto.ScheduleItem> read(LocalDateTime now) {
        LocalDate today = KstDates.today(now);
        LocalDateTime from = KstDates.startOfDay(today);
        LocalDateTime to = from.plusDays(1);
        List<AdminHomeDto.ScheduleItem> items = new ArrayList<>();
        for (GroupBuy groupBuy : opsRepository.findOpeningBetween(from, to)) {
            items.add(new AdminHomeDto.ScheduleItem(groupBuy.getStartAt(), "GROUP_BUY_OPEN",
                    "공구 오픈 · " + groupBuy.getContract().getTitle(), pair(groupBuy), groupBuyTarget(groupBuy)));
        }
        for (GroupBuy groupBuy : opsRepository.findEndingBetween(from, to)) {
            items.add(new AdminHomeDto.ScheduleItem(groupBuy.getEndAt(), "GROUP_BUY_END",
                    "공구 종료 · " + groupBuy.getContract().getTitle(), pair(groupBuy), groupBuyTarget(groupBuy)));
        }
        Object[] payouts = opsRepository.sumPayoutsDue(today);
        long payoutCount = payouts[0] instanceof Number n ? n.longValue() : 0;
        if (payoutCount > 0) {
            long amount = payouts[1] instanceof Number n ? n.longValue() : 0;
            items.add(new AdminHomeDto.ScheduleItem(KstDates.at(today, java.time.LocalTime.of(settlementProperties.getPayoutHour(), 0)),
                    "SETTLEMENT_PAYOUT", "정산 지급 " + payoutCount + "건", String.format("합계 %,d원", amount),
                    new AdminHomeDto.Target("QUEUE", null, "/settlements?tab=ALL")));
        }
        for (Settlement settlement : opsRepository.findReviewDueBetween(from, to)) {
            items.add(new AdminHomeDto.ScheduleItem(settlement.getReviewDueAt(), "SETTLEMENT_REVIEW_DUE",
                    "정산 확인 마감 · " + settlement.getContract().getTitle(), "조정 요청이 없으면 자동 확정",
                    new AdminHomeDto.Target("SETTLEMENT", settlement.getId(), null)));
        }
        for (SettlementAdjustment adjustment : opsRepository.findAdjustmentDeadlinesBetween(from, to)) {
            items.add(new AdminHomeDto.ScheduleItem(adjustment.getDeadlineAt(), "ADJUSTMENT_DEADLINE",
                    "조정 협의 기한 · " + adjustment.getSettlementNumber(), "합의가 없으면 원래 금액으로 확정",
                    new AdminHomeDto.Target("SETTLEMENT", adjustment.getSettlementId(), null)));
        }
        for (GroupBuyAdminSuspension suspension : opsRepository.findSuspensionExecutionsBetween(from, to)) {
            GroupBuy groupBuy = suspension.getGroupBuy();
            items.add(new AdminHomeDto.ScheduleItem(suspension.getExecuteScheduledAt(), "ADMIN_SUSPENSION_EXECUTE",
                    "직권 중단 집행 예정 · " + groupBuy.getContract().getTitle(), "운영자 집행 필요",
                    new AdminHomeDto.Target("GROUP_BUY", groupBuy.getId(), null)));
        }
        items.sort(Comparator.comparing(AdminHomeDto.ScheduleItem::time).thenComparing(AdminHomeDto.ScheduleItem::kind));
        return items;
    }

    private static String pair(GroupBuy groupBuy) {
        return groupBuy.getMarket().getMarketName() + " × " + groupBuy.getCreator().getShowroomName();
    }

    private static AdminHomeDto.Target groupBuyTarget(GroupBuy groupBuy) {
        return new AdminHomeDto.Target("GROUP_BUY", groupBuy.getId(), null);
    }
}
