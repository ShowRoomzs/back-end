package showroomz.api.creator.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.calendar.service.CommonBusinessHolidayService;
import showroomz.api.common.calendar.service.HomeCalendarRules;
import showroomz.api.common.sanction.SanctionHoldView;
import showroomz.api.creator.home.dto.CreatorHomeDto;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyExtensionRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 스튜디오 홈 월 달력(1017 설계서 5-3) — 파트너 달력(4-3)과 같은 모양. 기한은 내 서명 기한 · 연장 응답 기한(공구 종료일)만이다 —
 * 게시물 작성 · 재등록은 제출 마감 규칙이 없어 달력에 넣지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorHomeCalendarService {

    static final String SIGN_DUE = "SIGN_DUE";
    static final String EXTENSION_RESPOND_DUE = "EXTENSION_RESPOND_DUE";
    static final String GROUP_BUY_OPEN = "GROUP_BUY_OPEN";
    static final String GROUP_BUY_END = "GROUP_BUY_END";

    private final CommonBusinessHolidayService holidayService;
    private final GroupBuyRepository groupBuyRepository;
    private final ContractRepository contractRepository;
    private final GroupBuyExtensionRequestRepository extensionRequestRepository;
    private final GroupBuyPostRepository groupBuyPostRepository;

    public CreatorHomeDto.Calendar read(Long creatorId, String month) {
        YearMonth yearMonth = HomeCalendarRules.parseMonth(month);
        LocalDate from = yearMonth.atDay(1).minusDays(HomeCalendarRules.PAD_DAYS);
        LocalDate to = yearMonth.atEndOfMonth().plusDays(HomeCalendarRules.PAD_DAYS);
        LocalDateTime fromAt = KstDates.startOfDay(from);
        LocalDateTime toAt = KstDates.startOfDay(to.plusDays(1));

        List<CreatorHomeDto.CalendarGroupBuy> bars = groupBuyRepository
                .findOverlappingForCreator(creatorId, fromAt, toAt).stream()
                .map(CreatorHomeCalendarService::bar)
                .toList();
        return new CreatorHomeDto.Calendar(yearMonth.toString(), from, to, holidayService.getHolidays(from, to), bars,
                deadlines(creatorId, fromAt, toAt));
    }

    /**
     * 다가오는 일정(H2) 재료 — 오늘 이후 오픈 · 종료. 앞에서 {@code limit}건. 오픈 카드 문구는 게시물 상태에 따라 다르다(시안 「게시물 승인
     * 완료 · 오픈 대기」 · 「운영자가 게시물을 검토하고 있어요」) — 공구 · 게시물 상태를 함께 내려 FE 가 고른다.
     */
    public List<CreatorHomeDto.Upcoming> upcoming(Long creatorId, LocalDateTime now, int limit) {
        List<GroupBuy> groupBuys = groupBuyRepository.findOverlappingForCreator(creatorId, now, now.plusDays(90)).stream()
                .filter(groupBuy -> !groupBuy.getStatus().isTerminal())
                .toList();
        Map<Long, GroupBuyPost> posts = groupBuys.isEmpty() ? Map.of() : groupBuyPostRepository
                .findByGroupBuyIds(groupBuys.stream().map(GroupBuy::getId).toList()).stream()
                .collect(Collectors.toMap(p -> p.getGroupBuy().getId(), Function.identity(), (a, b) -> a));
        List<CreatorHomeDto.Upcoming> events = new ArrayList<>();
        for (GroupBuy groupBuy : groupBuys) {
            String name = groupBuy.getContract().getTitle();
            CreatorHomeDto.Target target = new CreatorHomeDto.Target("GROUP_BUY", groupBuy.getId());
            GroupBuyPostStatus postStatus = GroupBuyPostStatus.of(posts.get(groupBuy.getId()), groupBuy);
            if (groupBuy.getStartAt().isAfter(now)) {
                events.add(new CreatorHomeDto.Upcoming(KstDates.toKstDate(groupBuy.getStartAt()), "공구 오픈 · " + name,
                        "오픈되면 게시물이 공개되고 판매가 시작돼요", target, GROUP_BUY_OPEN, groupBuy.getStatus(),
                        postStatus));
            }
            if (groupBuy.getEndAt().isAfter(now)) {
                events.add(new CreatorHomeDto.Upcoming(KstDates.toKstDate(groupBuy.getEndAt()), "공구 종료 · " + name,
                        "종료 후 주문이 모두 끝나면 정산이 생성돼요", target, GROUP_BUY_END, groupBuy.getStatus(),
                        postStatus));
            }
        }
        // 같은 날이면 종료를 먼저 — 시안 「종료 · 오픈 순」.
        events.sort(Comparator.comparing(CreatorHomeDto.Upcoming::date)
                .thenComparing(u -> GROUP_BUY_END.equals(u.kind()) ? 0 : 1));
        return events.size() > limit ? List.copyOf(events.subList(0, limit)) : events;
    }

    private List<CreatorHomeDto.Deadline> deadlines(Long creatorId, LocalDateTime fromAt, LocalDateTime toAt) {
        List<CreatorHomeDto.Deadline> deadlines = new ArrayList<>();
        for (Contract contract : contractRepository.findCreatorSignatureTodos(creatorId)) {
            LocalDateTime due = contract.getSignatureDeadlineAt();
            // 체결 보류 중이면 기한이 멈춰 있다 — 달력에 찍지 않는다.
            if (due != null && !contract.isHeldBySanction() && !due.isBefore(fromAt) && due.isBefore(toAt)) {
                deadlines.add(new CreatorHomeDto.Deadline(KstDates.toKstDate(due), SIGN_DUE, "서명 기한 · " + contract.getTitle(),
                        "CONTRACT", contract.getId()));
            }
        }
        for (GroupBuyExtensionRequest request : extensionRequestRepository.findPendingForCreator(creatorId, fromAt)) {
            GroupBuy groupBuy = request.getGroupBuy();
            if (groupBuy.getEndAt().isBefore(toAt)) {
                deadlines.add(new CreatorHomeDto.Deadline(KstDates.toKstDate(groupBuy.getEndAt()), EXTENSION_RESPOND_DUE,
                        "연장 응답 · " + groupBuy.getContract().getTitle(), "GROUP_BUY", groupBuy.getId()));
            }
        }
        deadlines.sort(Comparator.comparing(CreatorHomeDto.Deadline::date)
                .thenComparing(CreatorHomeDto.Deadline::kind)
                .thenComparing(CreatorHomeDto.Deadline::targetId));
        return deadlines;
    }

    private static CreatorHomeDto.CalendarGroupBuy bar(GroupBuy groupBuy) {
        return new CreatorHomeDto.CalendarGroupBuy(groupBuy.getId(), groupBuy.getContract().getTitle(),
                groupBuy.getMarket().getMarketName(), groupBuy.getStartAt(),
                groupBuy.getStatus().isTerminal() && groupBuy.getEndedAt() != null ? groupBuy.getEndedAt() : groupBuy.getEndAt(),
                HomeCalendarRules.barStatus(groupBuy.getStatus()), SanctionHoldView.forCreator(groupBuy));
    }
}
