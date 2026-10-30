package showroomz.api.seller.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.calendar.service.CommonBusinessHolidayService;
import showroomz.api.common.calendar.service.HomeCalendarRules;
import showroomz.api.common.sanction.SanctionHoldView;
import showroomz.api.seller.home.dto.SellerHomeDto;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 파트너 홈 공구 일정 월 달력(1017 설계서 4-3). 시안 그리드가 앞뒤 달 칸까지 그리므로 요청 월 ±7일을 준다({@link HomeCalendarRules}).
 * 「한 칸 3줄 초과 +N」 · 주말 회색 · 오늘 테두리는 FE 몫이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerHomeCalendarService {

    static final String SHIP_DUE = "SHIP_DUE";
    static final String SIGN_DUE = "SIGN_DUE";

    private final CommonBusinessHolidayService holidayService;
    private final GroupBuyRepository groupBuyRepository;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final ContractRepository contractRepository;

    public SellerHomeDto.Calendar read(Long marketId, String month) {
        YearMonth yearMonth = HomeCalendarRules.parseMonth(month);
        LocalDate from = yearMonth.atDay(1).minusDays(HomeCalendarRules.PAD_DAYS);
        LocalDate to = yearMonth.atEndOfMonth().plusDays(HomeCalendarRules.PAD_DAYS);
        LocalDateTime fromAt = KstDates.startOfDay(from);
        LocalDateTime toAt = KstDates.startOfDay(to.plusDays(1));

        List<GroupBuy> groupBuys = groupBuyRepository.findOverlappingForMarket(marketId, fromAt, toAt);
        List<SellerHomeDto.CalendarGroupBuy> bars = groupBuys.stream()
                .map(g -> new SellerHomeDto.CalendarGroupBuy(g.getId(), g.getContract().getTitle(), g.getStartAt(),
                        g.getStatus().isTerminal() && g.getEndedAt() != null ? g.getEndedAt() : g.getEndAt(),
                        HomeCalendarRules.barStatus(g.getStatus()), SanctionHoldView.forBrand(g)))
                .toList();

        List<SellerHomeDto.Deadline> deadlines = new ArrayList<>(shipDeadlines(marketId, fromAt, toAt, groupBuys));
        for (Contract contract : contractRepository.findSignatureDeadlinesForMarket(marketId, fromAt, toAt)) {
            deadlines.add(new SellerHomeDto.Deadline(KstDates.toKstDate(contract.getSignatureDeadlineAt()), SIGN_DUE,
                    "서명 기한 · " + contract.getTitle(), "CONTRACT", contract.getId()));
        }
        deadlines.sort(Comparator.comparing(SellerHomeDto.Deadline::date)
                .thenComparing(SellerHomeDto.Deadline::kind)
                .thenComparing(SellerHomeDto.Deadline::targetId));
        return new SellerHomeDto.Calendar(yearMonth.toString(), from, to, holidayService.getHolidays(from, to), bars,
                deadlines);
    }

    /** 발송 기한 — 공구별 DISTINCT 날짜. 기간이 구간과 겹치지 않는 공구(종료 뒤 발송 기한만 걸친 것)는 이름을 따로 읽는다. */
    private List<SellerHomeDto.Deadline> shipDeadlines(Long marketId, LocalDateTime fromAt, LocalDateTime toAt,
                                                       List<GroupBuy> loaded) {
        Map<Long, Set<LocalDate>> datesByGroupBuy = new LinkedHashMap<>();
        for (Object[] row : deliveryGroupRepository.findShipDueForMarket(marketId, fromAt, toAt)) {
            datesByGroupBuy.computeIfAbsent((Long) row[0], id -> new TreeSet<>())
                    .add(KstDates.toKstDate(((LocalDateTime) row[1])));
        }
        if (datesByGroupBuy.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        loaded.forEach(g -> names.put(g.getId(), g.getContract().getTitle()));
        Set<Long> missing = new HashSet<>(datesByGroupBuy.keySet());
        missing.removeAll(names.keySet());
        if (!missing.isEmpty()) {
            groupBuyRepository.findAllById(missing).forEach(g -> names.put(g.getId(), g.getContract().getTitle()));
        }
        List<SellerHomeDto.Deadline> deadlines = new ArrayList<>();
        datesByGroupBuy.forEach((groupBuyId, dates) -> dates.forEach(date -> deadlines.add(new SellerHomeDto.Deadline(
                date, SHIP_DUE, "발송 기한 · " + names.getOrDefault(groupBuyId, ""), "GROUP_BUY", groupBuyId))));
        return deadlines;
    }
}
