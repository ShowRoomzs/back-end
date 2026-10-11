package showroomz.api.admin.thread.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.thread.dto.AdminThreadDto.ChannelListItem;
import showroomz.api.admin.thread.dto.AdminThreadDto.IssueListItem;
import showroomz.api.admin.thread.dto.AdminThreadDto.IssueTabSummary;
import showroomz.api.admin.thread.type.AdminChannelTab;
import showroomz.api.admin.thread.type.AdminIssueState;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustment;
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentRepository;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader.AdjustmentSummary;
import showroomz.domain.settlement.adjustment.type.AdjustmentStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 어드민 20b 이슈 탭(44 이슈 스레드 설계서 4-1 · 4-2) — 정산 조정 3자 스레드의 목록 · seg 숫자.
 *
 * <p>원천은 협의 행이다(스레드가 아니라) — 목록에 PAIR 스레드가 섞이지 않는다(4-5). 운영자는 참가자가 아니라 안 읽은 수는 항상 0이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminIssueThreadQueryService {

    private final SettlementAdjustmentRepository adjustments;
    private final SettlementAdjustmentReader reader;
    private final MessageThreadRepository threads;
    private final GroupBuyRepository groupBuys;
    private final MarketRepository markets;
    private final CreatorRepository creators;

    /** 공구명 · 브랜드명 · 쇼룸명 부분 일치 · 마지막 메시지 최신순(메시지 없는 스레드는 맨 아래). */
    public PageResponse<ChannelListItem> list(AdminIssueState state, String keyword, PagingRequest paging) {
        Pageable pageable = paging.toPageable(Sort.unsorted());
        String pattern = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
        Page<SettlementAdjustment> page = adjustments.searchForAdmin(
                (state == null ? AdminIssueState.OPEN : state).getStatuses(), pattern, pageable);
        List<SettlementAdjustment> rows = page.getContent();
        if (rows.isEmpty()) {
            return PageResponse.of(page.map(a -> (ChannelListItem) null));
        }
        List<Long> threadIds = rows.stream().map(SettlementAdjustment::getThreadId).toList();
        Map<Long, MessageThread> threadById = byId(threads.findAllById(threadIds), MessageThread::getId);
        Map<Long, AdjustmentSummary> summaries = reader.findByThreadIds(threadIds);
        Map<Long, String> titles = groupBuys.findTitleMapByIds(
                rows.stream().map(SettlementAdjustment::getGroupBuyId).distinct().toList());
        Map<Long, String> brandNames = markets.findAllById(ids(rows, SettlementAdjustment::getMarketId)).stream()
                .collect(Collectors.toMap(Market::getId, Market::getMarketName));
        Map<Long, String> showroomNames = creators.findAllById(ids(rows, SettlementAdjustment::getCreatorId)).stream()
                .collect(Collectors.toMap(Creator::getId, Creator::getShowroomName));
        return PageResponse.of(page.map(a -> toListItem(a, threadById.get(a.getThreadId()),
                summaries.get(a.getThreadId()), titles.get(a.getGroupBuyId()), brandNames.get(a.getMarketId()),
                showroomNames.get(a.getCreatorId()))));
    }

    /** seg 숫자 — 진행 중 · 종결. 운영자 기준 안 읽은 수 · 미처리 카드는 없다(0-7). */
    public IssueTabSummary summary() {
        long open = 0;
        long closed = 0;
        for (Object[] row : adjustments.countByStatus()) {
            long count = ((Number) row[1]).longValue();
            if (row[0] == AdjustmentStatus.OPEN) {
                open += count;
            } else {
                closed += count;
            }
        }
        return new IssueTabSummary(0, 0, open, closed);
    }

    private static ChannelListItem toListItem(SettlementAdjustment a, MessageThread thread, AdjustmentSummary summary,
                                              String title, String brandName, String showroomName) {
        String[] badge = AdminIssuePanelAssembler.badgeOf(a.getStatus());
        IssueListItem issue = new IssueListItem(a.getId(), a.getStatus().name(), a.getStatus().getLabel(), badge[0],
                badge[1], AdminIssuePanelAssembler.subtitleOf(brandName, showroomName), a.getSettlementId(),
                a.getSettlementNumber(), a.getGroupBuyId(), a.getMarketId(), brandName, a.getCreatorId(), showroomName,
                a.getDeadlineAt(), summary == null ? null : summary.remainingBusinessDays());
        return new ChannelListItem(a.getThreadId(), AdminChannelTab.ISSUE, title, null, null, null, null, null, null,
                thread == null ? null : thread.getLastMessagePreview(), false,
                thread == null ? null : thread.getLastMessageAt(), 0, false, issue);
    }

    private static List<Long> ids(Collection<SettlementAdjustment> rows, Function<SettlementAdjustment, Long> key) {
        return rows.stream().map(key).distinct().toList();
    }

    private static <T> Map<Long, T> byId(Collection<T> items, Function<T, Long> key) {
        return items.stream().collect(Collectors.toMap(key, Function.identity()));
    }
}
