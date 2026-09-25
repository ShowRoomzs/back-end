package showroomz.api.admin.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyDetailResponse;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyDto;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyListItem;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuySummaryResponse;
import showroomz.api.seller.groupbuy.service.GroupBuyAppealAttachmentStorage;
import showroomz.domain.contract.repository.ContractItemRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyAppealAttachment;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.entity.GroupBuyPostRevision;
import showroomz.domain.groupbuy.repository.GroupBuyAdminSuspensionRepository;
import showroomz.domain.groupbuy.repository.GroupBuyAppealAttachmentRepository;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.repository.GroupBuyPostRevisionRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepositoryCustom.DeadlineRow;
import showroomz.domain.groupbuy.service.GroupBuyFacts;
import showroomz.domain.groupbuy.service.GroupBuyFactsLoader;
import showroomz.domain.groupbuy.type.AdminGroupBuyQueue;
import showroomz.domain.groupbuy.type.AdminGroupBuySortType;
import showroomz.domain.groupbuy.type.AdminGroupBuyTab;
import showroomz.domain.groupbuy.type.GroupBuyAttachmentStatus;
import showroomz.domain.groupbuy.type.GroupBuyPostRevisionKind;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.global.config.properties.GroupBuyProperties;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 어드민 공구 조회(32 설계 3 · 4절). 목록에는 실행 액션이 없다 — 모든 판정은 상세의 모달을 거친다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminGroupBuyQueryService {

    private final AdminGroupBuyAccess access;
    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPostRepository postRepository;
    private final GroupBuyPostRevisionRepository revisionRepository;
    private final GroupBuyAdminSuspensionRepository adminSuspensionRepository;
    private final GroupBuyAppealAttachmentRepository appealAttachmentRepository;
    private final ContractItemRepository contractItemRepository;
    private final GroupBuyFactsLoader factsLoader;
    private final AdminGroupBuyDetailAssembler detailAssembler;
    private final AdminGroupBuyPermissionPolicy permissionPolicy;
    private final AdminSuspensionSchedule schedule;
    private final GroupBuyAppealAttachmentStorage appealStorage;
    private final GroupBuyProperties properties;

    /**
     * 목록(A1~A3). 행마다 게시물 상태·조치 여부가 필요하다 — 페이지의 공구 id로 <b>테이블당 IN 쿼리 1번씩</b> 모은다.
     * 조치 여부는 배지와 같은 판정식({@code AdminGroupBuyQueuePredicate})을 쿼리로 태운다.
     */
    public PageResponse<AdminGroupBuyListItem> getGroupBuys(AdminGroupBuyTab tab, String keyword,
                                                            AdminGroupBuySortType sort, PagingRequest pagingRequest) {
        LocalDateTime now = LocalDateTime.now();
        Pageable pageable = PageRequest.of(Math.max(pagingRequest.getPage() - 1, 0), pagingRequest.getSize());
        Page<GroupBuy> page = groupBuyRepository.searchForAdmin(tabOrAll(tab), patternOf(keyword),
                sortOrDefault(sort), now, pageable);
        List<GroupBuy> groupBuys = page.getContent();
        if (groupBuys.isEmpty()) {
            return PageResponse.of(new PageImpl<>(List.of(), pageable, page.getTotalElements()));
        }

        List<Long> ids = groupBuys.stream().map(GroupBuy::getId).toList();
        Map<Long, GroupBuyPost> posts = postRepository.findByGroupBuyIds(ids).stream()
                .collect(Collectors.toMap(post -> post.getGroupBuy().getId(), Function.identity()));
        Set<Long> actionRequired = groupBuyRepository.findAdminActionRequiredIds(ids, now);
        Map<Long, Long> itemCounts = countItems(groupBuys);

        List<AdminGroupBuyListItem> content = groupBuys.stream()
                .map(groupBuy -> {
                    GroupBuyPostStatus postStatus = GroupBuyPostStatus.of(posts.get(groupBuy.getId()), groupBuy);
                    GroupBuyStatus status = groupBuy.getStatus();
                    return new AdminGroupBuyListItem(
                            groupBuy.getId(),
                            groupBuy.getGroupBuyNumber(),
                            groupBuy.getContract().getTitle(),
                            groupBuy.getCreator().getShowroomName(),
                            groupBuy.getMarket().getMarketName(),
                            itemCounts.getOrDefault(groupBuy.getContract().getId(), 0L),
                            groupBuy.getStartAt(),
                            groupBuy.getEndAt(),
                            postStatus, postStatus.getLabel(), postStatus.getTone(),
                            status, status.getLabel(), status.getTone(),
                            actionRequired.contains(groupBuy.getId()));
                })
                .toList();
        return PageResponse.of(new PageImpl<>(content, pageable, page.getTotalElements()));
    }

    /**
     * 요약(3-3) — 큐 4개는 COUNT 4회, 탭 카운트는 GROUP BY 1회 + 큐 합. 판매 포트를 부르지 않는다 — GNB가 폴링한다.
     * {@code actionRequiredCount}는 큐 합이고, 큐가 배타적이라 조치 필요 탭의 행 수와 같다.
     */
    public AdminGroupBuySummaryResponse getSummary() {
        LocalDateTime now = LocalDateTime.now();
        Map<AdminGroupBuyQueue, Long> queues = new EnumMap<>(AdminGroupBuyQueue.class);
        for (AdminGroupBuyQueue queue : AdminGroupBuyQueue.values()) {
            queues.put(queue, groupBuyRepository.countAdminQueue(queue, now));
        }
        long actionRequired = queues.values().stream().mapToLong(Long::longValue).sum();

        Map<GroupBuyStatus, Long> byStatus = new HashMap<>();
        for (Object[] row : groupBuyRepository.countAllByStatus()) {
            byStatus.put((GroupBuyStatus) row[0], (Long) row[1]);
        }
        Map<String, Long> tabCounts = new LinkedHashMap<>();
        for (AdminGroupBuyTab tab : AdminGroupBuyTab.values()) {
            tabCounts.put(tab.name(), tab.isActionRequiredOnly() ? actionRequired
                    : tab.getStatuses().stream().mapToLong(s -> byStatus.getOrDefault(s, 0L)).sum());
        }

        List<AdminGroupBuySummaryResponse.NearestDeadline> deadlines = new ArrayList<>();
        DeadlineRow openReview = groupBuyRepository.findEarliestOpenReview();
        if (openReview != null) {
            LocalDateTime dueAt = detailAssembler.openReviewDueAt(openReview.at(),
                    properties.getOpenReview().getSlaBusinessDays());
            deadlines.add(deadline(AdminGroupBuyQueue.OPEN_REVIEW, openReview, dueAt, now));
        }
        DeadlineRow appeal = groupBuyRepository.findEarliestAppealReview(now);
        if (appeal != null) {
            deadlines.add(deadline(AdminGroupBuyQueue.APPEAL_REVIEW, appeal, appeal.at(), now));
        }

        return new AdminGroupBuySummaryResponse(queues, actionRequired, tabCounts, deadlines,
                settlementWatch(byStatus.getOrDefault(GroupBuyStatus.ENDED, 0L), now));
    }

    private static AdminGroupBuySummaryResponse.NearestDeadline deadline(AdminGroupBuyQueue queue, DeadlineRow row,
                                                                         LocalDateTime dueAt, LocalDateTime now) {
        return new AdminGroupBuySummaryResponse.NearestDeadline(queue, row.groupBuyId(), row.title(), dueAt,
                ChronoUnit.DAYS.between(now.toLocalDate(), dueAt.toLocalDate()));
    }

    /** 조치와 다른 축 — 기한을 넘겨도 조치 큐에 넣지 않는다. 중단 공구의 정산 지연은 세지 않는다(13-2 #12). */
    private AdminGroupBuySummaryResponse.SettlementWatch settlementWatch(long watching, LocalDateTime now) {
        int watchDays = properties.getSettlement().getWatchDays();
        long overdue = groupBuyRepository.countEndedAtOrBefore(now.minusDays(watchDays));
        DeadlineRow earliest = groupBuyRepository.findEarliestEnded();
        AdminGroupBuySummaryResponse.Nearest nearest = earliest == null ? null
                : new AdminGroupBuySummaryResponse.Nearest(earliest.groupBuyId(), earliest.title(),
                        earliest.at().toLocalDate().plusDays(watchDays),
                        !now.isBefore(earliest.at().plusDays(watchDays)));
        return new AdminGroupBuySummaryResponse.SettlementWatch(watching, overdue, nearest);
    }

    /** 상세. 목록 조건(tab · keyword · sort)이 오면 같은 정렬로 앞뒤 1건씩 이웃을 고른다. */
    public AdminGroupBuyDetailResponse getGroupBuy(Long groupBuyId, AdminGroupBuyTab tab, String keyword,
                                                   AdminGroupBuySortType sort) {
        GroupBuy groupBuy = access.read(groupBuyId);
        return detailAssembler.assemble(groupBuy, navigation(groupBuyId, tab, keyword, sort));
    }

    private AdminGroupBuyDetailResponse.Navigation navigation(Long groupBuyId, AdminGroupBuyTab tab, String keyword,
                                                              AdminGroupBuySortType sort) {
        boolean hasListContext = tab != null || sort != null || (keyword != null && !keyword.isBlank());
        if (!hasListContext) {
            return new AdminGroupBuyDetailResponse.Navigation(null, null);
        }
        List<Long> ids = groupBuyRepository.findOrderedIdsForAdmin(tabOrAll(tab), patternOf(keyword),
                sortOrDefault(sort), LocalDateTime.now());
        int index = ids.indexOf(groupBuyId);
        if (index < 0) {
            // 현재 공구가 목록 조건에 맞지 않는다(그 사이 판정으로 탭이 바뀌었다) — 근거 없는 이웃을 지어내지 않는다.
            return new AdminGroupBuyDetailResponse.Navigation(null, null);
        }
        return new AdminGroupBuyDetailResponse.Navigation(
                index > 0 ? ids.get(index - 1) : null,
                index < ids.size() - 1 ? ids.get(index + 1) : null);
    }

    /**
     * 게시물 판본 목록(5-5) — 원문 판본만 내리고 대조는 FE가 두 판본을 나란히 놓는다. 공구당 게시물 1개 · 판본 수십 개라
     * 페이징하지 않는다.
     */
    public List<AdminGroupBuyDto.PostRevisionItem> getPostRevisions(Long groupBuyId) {
        GroupBuy groupBuy = access.read(groupBuyId);
        GroupBuyPost post = postRepository.findByGroupBuyId(groupBuy.getId()).orElse(null);
        if (post == null) {
            return List.of();
        }
        List<GroupBuyPostRevision> revisions = revisionRepository.findByGroupBuyPostPostIdOrderByRevisionNoAsc(post.getPostId());
        if (revisions.isEmpty()) {
            return List.of();
        }
        // 승인된 판 = reviewed_at 이전 마지막 SUBMITTED — PENDING 동안 수정이 불가해 유일하게 정해진다(31 설계 2-6).
        Integer approvedNo = !post.isApproved() || post.getReviewedAt() == null ? null
                : revisions.stream()
                        .filter(r -> r.getKind() == GroupBuyPostRevisionKind.SUBMITTED)
                        .filter(r -> !r.getCreatedAt().isAfter(post.getReviewedAt()))
                        .map(GroupBuyPostRevision::getRevisionNo)
                        .reduce((first, second) -> second)
                        .orElse(null);
        Integer noticeNo = adminSuspensionRepository.findByGroupBuyIdOrderByNoticedAtDescIdDesc(groupBuy.getId())
                .stream()
                .map(GroupBuyAdminSuspension::getNoticeRevisionNo)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        int latestNo = revisions.get(revisions.size() - 1).getRevisionNo();
        return revisions.stream()
                .map(r -> new AdminGroupBuyDto.PostRevisionItem(
                        r.getRevisionNo(), r.getKind(), r.getTitle(), r.getContent(), r.getCreatedAt(),
                        Objects.equals(r.getRevisionNo(), approvedNo),
                        Objects.equals(r.getRevisionNo(), post.getHiddenRevisionNo()),
                        Objects.equals(r.getRevisionNo(), post.getUnhiddenRevisionNo()),
                        Objects.equals(r.getRevisionNo(), noticeNo),
                        r.getRevisionNo() == latestNo))
                .toList();
    }

    /** M6 날짜 선택지(6-1) — 서버 now(Asia/Seoul) · 공휴일 설정을 FE가 다시 가질 필요가 없게 칩을 서버가 만든다. */
    public AdminGroupBuyDto.NoticeOptionsResponse getNoticeOptions(Long groupBuyId) {
        GroupBuy groupBuy = access.read(groupBuyId);
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime minAppeal = schedule.minAppealDeadline(now.toLocalDate());
        AdminGroupBuyDetailResponse.NoticeUnavailableReason reason = permissionPolicy.noticeUnavailableReason(facts, now);
        return new AdminGroupBuyDto.NoticeOptionsResponse(
                now.toLocalDate(),
                new AdminGroupBuyDto.AppealDeadline(minAppeal, minAppeal),
                schedule.executionDates(groupBuy, now),
                groupBuy.getEndAt(),
                reason == null,
                reason);
    }

    /** 소명 첨부 다운로드 URL — 클릭 시 발급한다(유효 5분). 업로드 완료 첨부만. */
    public AdminGroupBuyDto.AttachmentUrlResponse getAppealAttachmentUrl(Long groupBuyId, Long attachmentId) {
        GroupBuyAppealAttachment attachment = appealAttachmentRepository.findById(attachmentId)
                .filter(a -> a.getAdminSuspension().getGroupBuy().getId().equals(groupBuyId))
                .filter(a -> a.getStatus() == GroupBuyAttachmentStatus.UPLOADED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_APPEAL_ATTACHMENT_NOT_FOUND));
        return new AdminGroupBuyDto.AttachmentUrlResponse(attachment.getId(),
                appealStorage.presignDownload(attachment.getS3Key(), attachment.getOriginalName()),
                attachment.getOriginalName(),
                GroupBuyAppealAttachmentStorage.DOWNLOAD_EXPIRY.toSeconds());
    }

    private Map<Long, Long> countItems(List<GroupBuy> groupBuys) {
        List<Long> contractIds = groupBuys.stream().map(groupBuy -> groupBuy.getContract().getId()).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : contractItemRepository.countByContractIds(contractIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static AdminGroupBuyTab tabOrAll(AdminGroupBuyTab tab) {
        return tab == null ? AdminGroupBuyTab.ALL : tab;
    }

    private static AdminGroupBuySortType sortOrDefault(AdminGroupBuySortType sort) {
        return sort == null ? AdminGroupBuySortType.ACTION_REQUIRED_FIRST : sort;
    }

    private static String patternOf(String keyword) {
        return keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
    }
}
