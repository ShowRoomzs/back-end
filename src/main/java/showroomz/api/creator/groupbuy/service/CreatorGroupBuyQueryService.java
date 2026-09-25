package showroomz.api.creator.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuyDetailResponse;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuyListItem;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuySummaryResponse;
import showroomz.domain.contract.repository.ContractItemRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.CreatorGroupBuySortType;
import showroomz.domain.groupbuy.type.CreatorGroupBuyTab;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 스튜디오 공구 조회(31 설계 3 · 4절). 목록은 조회 전용이고 실행은 상세에서만 시작한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorGroupBuyQueryService {

    private final CreatorGroupBuyReader reader;
    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPostRepository postRepository;
    private final ContractItemRepository contractItemRepository;
    private final CreatorGroupBuyDetailAssembler detailAssembler;

    /**
     * 목록(A1 · A2). 빈 상태(A2)는 {@code content: []} + 요약의 {@code tabCounts.ALL == 0}이다.
     *
     * <p>행마다 게시물 상태·조치 여부가 필요하다 — 페이지의 공구 id로 <b>테이블당 IN 쿼리 1번씩</b> 모은다.
     * 조치 여부는 배지와 같은 판정식({@code CreatorGroupBuyActionPredicate})을 쿼리로 태운다.
     */
    public PageResponse<CreatorGroupBuyListItem> getGroupBuys(String creatorEmail, CreatorGroupBuyTab tab, String keyword,
                                                              CreatorGroupBuySortType sort, PagingRequest pagingRequest) {
        Creator creator = reader.resolveCreator(creatorEmail);
        LocalDateTime now = LocalDateTime.now();
        Pageable pageable = PageRequest.of(Math.max(pagingRequest.getPage() - 1, 0), pagingRequest.getSize());

        Page<GroupBuy> page = groupBuyRepository.searchForCreator(creator.getId(), tabOrAll(tab).getStatuses(),
                patternOf(keyword), sortOrDefault(sort), now, pageable);
        List<GroupBuy> groupBuys = page.getContent();
        if (groupBuys.isEmpty()) {
            return PageResponse.of(new PageImpl<>(List.of(), pageable, page.getTotalElements()));
        }

        List<Long> ids = groupBuys.stream().map(GroupBuy::getId).toList();
        Map<Long, GroupBuyPost> posts = postRepository.findByGroupBuyIds(ids).stream()
                .collect(Collectors.toMap(post -> post.getGroupBuy().getId(), Function.identity()));
        Set<Long> actionRequired = groupBuyRepository.findActionRequiredIdsForCreator(creator.getId(), ids, now);
        Map<Long, Long> itemCounts = countItems(groupBuys);

        List<CreatorGroupBuyListItem> content = groupBuys.stream()
                .map(groupBuy -> {
                    GroupBuyPostStatus postStatus = GroupBuyPostStatus.of(posts.get(groupBuy.getId()), groupBuy);
                    GroupBuyStatus status = groupBuy.getStatus();
                    return new CreatorGroupBuyListItem(
                            groupBuy.getId(),
                            groupBuy.getGroupBuyNumber(),
                            groupBuy.getContract().getTitle(),
                            groupBuy.getMarket().getMarketName(),
                            itemCounts.getOrDefault(groupBuy.getContract().getId(), 0L),
                            groupBuy.getStartAt(),
                            groupBuy.getEndAt(),
                            postStatus,
                            postStatus.getLabel(),
                            postStatus.getTone(),
                            status,
                            status.getLabel(),
                            status.getTone(),
                            actionRequired.contains(groupBuy.getId()));
                })
                .toList();
        return PageResponse.of(new PageImpl<>(content, pageable, page.getTotalElements()));
    }

    /**
     * 탭 카운트 + 「내 조치 필요」 수(31 설계 3-3). GNB 배지 · 목록 헤더 · 「먼저」 정렬이 같은 판정식을 쓴다 —
     * 한 값만 내린다(시안의 헤더 3 / GNB 2 불일치는 시안 정정 대상이다 · 10-1 #3).
     */
    public CreatorGroupBuySummaryResponse getSummary(String creatorEmail) {
        Creator creator = reader.resolveCreator(creatorEmail);

        Map<GroupBuyStatus, Long> byStatus = new HashMap<>();
        for (Object[] row : groupBuyRepository.countByStatusForCreator(creator.getId())) {
            byStatus.put((GroupBuyStatus) row[0], (Long) row[1]);
        }
        Map<String, Long> tabCounts = new LinkedHashMap<>();
        for (CreatorGroupBuyTab tab : CreatorGroupBuyTab.values()) {
            tabCounts.put(tab.name(), tab.getStatuses().stream().mapToLong(s -> byStatus.getOrDefault(s, 0L)).sum());
        }
        long actionRequired = groupBuyRepository.countActionRequiredForCreator(creator.getId(), LocalDateTime.now());
        return new CreatorGroupBuySummaryResponse(tabCounts, actionRequired);
    }

    /**
     * 상세. 목록 조건(tab · keyword · sort)이 오면 같은 정렬 기준으로 앞뒤 1건씩 이웃을 고른다 — 목록과 이웃이 어긋나지
     * 않도록 목록 쿼리와 같은 조건·정렬을 쓴다. 한 인플루언서의 공구는 수십~수백 건이라 id 목록으로 충분하다.
     */
    public CreatorGroupBuyDetailResponse getGroupBuy(String creatorEmail, Long groupBuyId, CreatorGroupBuyTab tab,
                                                     String keyword, CreatorGroupBuySortType sort) {
        Creator creator = reader.resolveCreator(creatorEmail);
        GroupBuy groupBuy = reader.requireMine(creator.getId(), groupBuyId);
        return detailAssembler.assemble(groupBuy, navigation(creator.getId(), groupBuyId, tab, keyword, sort));
    }

    private CreatorGroupBuyDetailResponse.Navigation navigation(Long creatorId, Long groupBuyId,
                                                                CreatorGroupBuyTab tab, String keyword,
                                                                CreatorGroupBuySortType sort) {
        boolean hasListContext = tab != null || sort != null || (keyword != null && !keyword.isBlank());
        if (!hasListContext) {
            return new CreatorGroupBuyDetailResponse.Navigation(null, null);
        }
        List<Long> ids = groupBuyRepository.findOrderedIdsForCreator(creatorId, tabOrAll(tab).getStatuses(),
                patternOf(keyword), sortOrDefault(sort), LocalDateTime.now());
        int index = ids.indexOf(groupBuyId);
        if (index < 0) {
            // 현재 공구가 목록 조건에 맞지 않는다(그 사이 탭이 바뀌는 전이) — 근거 없는 이웃을 지어내지 않는다.
            return new CreatorGroupBuyDetailResponse.Navigation(null, null);
        }
        return new CreatorGroupBuyDetailResponse.Navigation(
                index > 0 ? ids.get(index - 1) : null,
                index < ids.size() - 1 ? ids.get(index + 1) : null);
    }

    private Map<Long, Long> countItems(List<GroupBuy> groupBuys) {
        List<Long> contractIds = groupBuys.stream().map(groupBuy -> groupBuy.getContract().getId()).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : contractItemRepository.countByContractIds(contractIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static CreatorGroupBuyTab tabOrAll(CreatorGroupBuyTab tab) {
        return tab == null ? CreatorGroupBuyTab.ALL : tab;
    }

    private static CreatorGroupBuySortType sortOrDefault(CreatorGroupBuySortType sort) {
        return sort == null ? CreatorGroupBuySortType.START_AT_ASC : sort;
    }

    private static String patternOf(String keyword) {
        return keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
    }
}
