package showroomz.api.seller.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.groupbuy.dto.GroupBuyDetailResponse;
import showroomz.api.seller.groupbuy.dto.GroupBuyListItem;
import showroomz.api.seller.groupbuy.dto.GroupBuySummaryResponse;
import showroomz.api.seller.groupbuy.service.GroupBuyAccessGuard.SellerScope;
import showroomz.domain.contract.repository.ContractItemRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyAdminSuspensionRepository;
import showroomz.domain.groupbuy.repository.GroupBuyChangeRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyExtensionRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyRemarkCode;
import showroomz.domain.groupbuy.type.GroupBuySortType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.GroupBuyTab;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 파트너센터 공구 조회(설계서 4-2 ~ 4-4). 목록은 조회 전용이다 — 실행은 상세에서만 시작한다(§30-1).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerGroupBuyQueryService {

    private final GroupBuyAccessGuard accessGuard;
    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPostRepository postRepository;
    private final GroupBuyExtensionRequestRepository extensionRequestRepository;
    private final GroupBuyChangeRequestRepository changeRequestRepository;
    private final GroupBuyAdminSuspensionRepository adminSuspensionRepository;
    private final ContractItemRepository contractItemRepository;
    private final GroupBuyDetailAssembler detailAssembler;

    /**
     * 목록(A1~A3). 빈 상태(A2)와 검색 결과 없음(A3)은 같은 응답이다 — FE가 {@code tabCounts.ALL == 0}으로 가른다.
     *
     * <p>행마다 게시물 상태와 비고가 필요하다. 페이지의 공구 id로 <b>테이블당 IN 쿼리 1번씩</b> 모아 조립한다 —
     * 행마다 조회하면 50건에 200쿼리가 된다.
     */
    public PageResponse<GroupBuyListItem> getGroupBuys(String sellerEmail, GroupBuyTab tab, String keyword,
                                                       GroupBuySortType sort, PagingRequest pagingRequest) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        Pageable pageable = PageRequest.of(Math.max(pagingRequest.getPage() - 1, 0), pagingRequest.getSize(),
                sortOf(sortOrDefault(sort)));

        Page<GroupBuy> page = groupBuyRepository.searchForSeller(
                scope.market().getId(), tabOrAll(tab).getStatuses(), patternOf(keyword), pageable);

        List<GroupBuy> groupBuys = page.getContent();
        if (groupBuys.isEmpty()) {
            return PageResponse.of(new PageImpl<>(List.of(), pageable, page.getTotalElements()));
        }
        List<Long> ids = groupBuys.stream().map(GroupBuy::getId).toList();
        Map<Long, GroupBuyPost> posts = postRepository.findByGroupBuyIds(ids).stream()
                .collect(Collectors.toMap(post -> post.getGroupBuy().getId(), Function.identity()));
        Set<Long> pendingExtensions = new HashSet<>(extensionRequestRepository.findPendingGroupBuyIds(ids));
        Map<Long, GroupBuyChangeRequest> pendingRequests = changeRequestRepository.findPendingByGroupBuyIds(ids).stream()
                .collect(Collectors.toMap(request -> request.getGroupBuy().getId(), Function.identity(), (a, b) -> a));
        Map<Long, GroupBuyAdminSuspension> notices = adminSuspensionRepository.findNoticedByGroupBuyIds(ids).stream()
                .collect(Collectors.toMap(notice -> notice.getGroupBuy().getId(), Function.identity(), (a, b) -> a));
        Map<Long, Long> itemCounts = countItems(groupBuys);

        List<GroupBuyListItem> content = groupBuys.stream()
                .map(groupBuy -> {
                    GroupBuyPostStatus postStatus = GroupBuyPostStatus.of(posts.get(groupBuy.getId()), groupBuy);
                    GroupBuyStatus status = groupBuy.getStatus();
                    return new GroupBuyListItem(
                            groupBuy.getId(),
                            groupBuy.getGroupBuyNumber(),
                            groupBuy.getContract().getTitle(),
                            groupBuy.getCreator().getShowroomName(),
                            itemCounts.getOrDefault(groupBuy.getContract().getId(), 0L),
                            groupBuy.getStartAt(),
                            groupBuy.getEndAt(),
                            postStatus,
                            postStatus.getLabel(),
                            postStatus.getTone(),
                            status,
                            status.getLabel(),
                            status.getTone(),
                            remark(groupBuy, notices.get(groupBuy.getId()), pendingRequests.get(groupBuy.getId()),
                                    pendingExtensions.contains(groupBuy.getId())));
                })
                .toList();
        return PageResponse.of(new PageImpl<>(content, pageable, page.getTotalElements()));
    }

    /**
     * 비고는 <b>하나만</b> 내린다 — {@link GroupBuyRemarkCode} 선언 순서가 우선순위다.
     * 판매가 끊길 가능성이 큰 쪽이 위다.
     */
    private GroupBuyListItem.Remark remark(GroupBuy groupBuy, GroupBuyAdminSuspension notice,
                                           GroupBuyChangeRequest pendingRequest, boolean extensionPending) {
        if (notice != null) {
            return new GroupBuyListItem.Remark(GroupBuyRemarkCode.ADMIN_SUSPENSION_NOTICED, notice.getAppealDeadlineAt());
        }
        if (pendingRequest != null) {
            return new GroupBuyListItem.Remark(pendingRequest.getRequestType() == ChangeRequestType.SUSPEND
                    ? GroupBuyRemarkCode.SUSPENSION_REQUEST_REVIEWING
                    : GroupBuyRemarkCode.EARLY_CLOSE_REQUEST_REVIEWING, null);
        }
        if (extensionPending) {
            return new GroupBuyListItem.Remark(GroupBuyRemarkCode.EXTENSION_PENDING, null);
        }
        if (groupBuy.getStatus() == GroupBuyStatus.SUSPENDED && groupBuy.getClosingAdminSuspensionId() != null) {
            return new GroupBuyListItem.Remark(GroupBuyRemarkCode.SUSPENDED_BY_ADMIN, null);
        }
        return null;
    }

    private Map<Long, Long> countItems(List<GroupBuy> groupBuys) {
        List<Long> contractIds = groupBuys.stream().map(groupBuy -> groupBuy.getContract().getId()).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : contractItemRepository.countByContractIds(contractIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static GroupBuyTab tabOrAll(GroupBuyTab tab) {
        return tab == null ? GroupBuyTab.ALL : tab;
    }

    private static GroupBuySortType sortOrDefault(GroupBuySortType sort) {
        return sort == null ? GroupBuySortType.START_AT_ASC : sort;
    }

    private static String patternOf(String keyword) {
        return keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
    }

    private static Sort sortOf(GroupBuySortType sort) {
        return switch (sort) {
            case START_AT_ASC -> Sort.by(Sort.Order.asc("startAt"), Sort.Order.asc("id"));
            case CREATED_DESC -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };
    }

    /**
     * 탭 카운트 + GNB 배지(설계서 4-3).
     *
     * <p>배지에 넣지 않는 것 — 게시물 반려(B2a)·숨김(B4j)은 경고색이지만 고칠 권한이 인플루언서·운영자에게 있다.
     * 브랜드가 끌 수 없는 배지가 켜지면 안 된다. 요청 대기도 같은 이유로 제외한다.
     */
    public GroupBuySummaryResponse getSummary(String sellerEmail) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        Long marketId = scope.market().getId();

        Map<GroupBuyStatus, Long> byStatus = new HashMap<>();
        for (Object[] row : groupBuyRepository.countByStatus(marketId)) {
            byStatus.put((GroupBuyStatus) row[0], (Long) row[1]);
        }
        Map<String, Long> tabCounts = new LinkedHashMap<>();
        for (GroupBuyTab tab : GroupBuyTab.values()) {
            tabCounts.put(tab.name(), tab.getStatuses().stream().mapToLong(s -> byStatus.getOrDefault(s, 0L)).sum());
        }

        long actionRequired = groupBuyRepository.countStockConfirmationPending(marketId)
                + adminSuspensionRepository.countAppealable(marketId, LocalDateTime.now());
        // 이행 확인 대기는 세지 않는다 — 이행 확인 폐기(2026-10-06).
        return new GroupBuySummaryResponse(tabCounts, actionRequired);
    }

    /**
     * 상세(B1~B7a). 목록 조건(tab · keyword · sort)이 오면 같은 조건·정렬로 앞뒤 1건씩 이웃을 고른다 — FE가 목록을 다시
     * 불러와 계산하지 않게 한다. 한 브랜드의 공구는 수십~수백 건이라 id 목록으로 충분하다.
     */
    public GroupBuyDetailResponse getGroupBuy(String sellerEmail, Long groupBuyId, GroupBuyTab tab, String keyword,
                                              GroupBuySortType sort) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        GroupBuy groupBuy = accessGuard.loadOwned(groupBuyId, scope.market());
        return detailAssembler.assemble(groupBuy, navigation(scope.market().getId(), groupBuyId, tab, keyword, sort));
    }

    private GroupBuyDetailResponse.Navigation navigation(Long marketId, Long groupBuyId, GroupBuyTab tab,
                                                         String keyword, GroupBuySortType sort) {
        boolean hasListContext = tab != null || sort != null || (keyword != null && !keyword.isBlank());
        if (!hasListContext) {
            return new GroupBuyDetailResponse.Navigation(null, null);
        }
        List<Long> ids = groupBuyRepository.findOrderedIdsForSeller(marketId, tabOrAll(tab).getStatuses(),
                patternOf(keyword), sortOf(sortOrDefault(sort)));
        int index = ids.indexOf(groupBuyId);
        if (index < 0) {
            // 현재 공구가 목록 조건에 맞지 않는다(그 사이 탭이 바뀌는 전이) — 근거 없는 이웃을 지어내지 않는다.
            return new GroupBuyDetailResponse.Navigation(null, null);
        }
        return new GroupBuyDetailResponse.Navigation(
                index > 0 ? ids.get(index - 1) : null,
                index < ids.size() - 1 ? ids.get(index + 1) : null);
    }
}
