package showroomz.api.admin.creator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.common.AdminMemberMasker;
import showroomz.api.admin.common.AdminPrivilege;
import showroomz.api.admin.common.AdminReasonOption;
import showroomz.api.admin.common.MemberNumber;
import showroomz.api.admin.creator.dto.AdminCreatorDto.*;
import showroomz.api.admin.creator.repository.AdminCreatorListQueryRepository;
import showroomz.api.admin.creator.repository.AdminCreatorQueryRepository;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.changerequest.repository.ChangeRequestRepository;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.WithholdingType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.history.type.DeviceType;
import showroomz.domain.member.common.type.MemberType;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.member.creator.entity.CreatorPrivacyAccessLog;
import showroomz.domain.member.creator.entity.CreatorSanction;
import showroomz.domain.member.creator.entity.CreatorWithdrawalRequest;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorPrivacyAccessLogRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.repository.CreatorSanctionRepository;
import showroomz.domain.member.creator.repository.CreatorSettlementAccountHistoryRepository;
import showroomz.domain.member.creator.repository.CreatorWithdrawalRequestRepository;
import showroomz.domain.member.creator.service.CreatorAdminHistoryReader;
import showroomz.domain.member.creator.service.CreatorWithdrawalEligibility;
import showroomz.domain.member.creator.type.CreatorApplicationStatus;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.creator.type.CreatorPrivacyAccessReason;
import showroomz.domain.member.creator.type.CreatorStatus;
import showroomz.domain.member.creator.type.CreatorSuspensionReason;
import showroomz.domain.member.creator.type.CreatorWithdrawalCondition;
import showroomz.domain.member.creator.type.CreatorWithdrawalRejectReason;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.repository.PostNotificationLogRepository;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustment;
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentRepository;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 인플루언서 상세 조회(53 설계서 3-1 ~ 3-7) — 좌측은 사실 조회, 우측 레일은 판단과 조치의 근거다. 마스킹은 서버가 끝내고(2-2), 본인확인
 * 정보는 <b>이 운영자의 유효한 해제 세션</b>이 있을 때만 실값이다(2-3).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCreatorQueryService {

    /** 레일 처리 이력 — 최신 3건(탈퇴 심사 카드가 붙으면 2건 · 시안 B1-b). */
    private static final int RAIL_HISTORY_SIZE = 3;
    private static final int RAIL_HISTORY_SIZE_WITH_REVIEW = 2;

    private final CreatorRepository creators;
    private final CreatorSanctionRepository sanctions;
    private final CreatorWithdrawalRequestRepository withdrawalRequests;
    private final CreatorPrivacyAccessLogRepository privacyLogs;
    private final CreatorSettlementAccountHistoryRepository accountHistories;
    private final CreatorFollowRepository follows;
    private final CreatorWithdrawalEligibility eligibility;
    private final AdminCreatorQueryRepository queries;
    private final CreatorAdminHistoryReader historyReader;
    private final AdminPrivilege privilege;
    private final SettlementPayoutRepository payouts;
    private final SettlementAdjustmentRepository adjustments;
    private final PostNotificationLogRepository postNotificationLogs;
    private final SellerRepository sellers;
    private final ChangeRequestRepository changeRequests;
    private final showroomz.domain.member.common.service.SanctionTermPolicy termPolicy;
    private final AdminCreatorListQueryRepository listQueries;
    private final showroomz.domain.connection.repository.ConnectionRepository connections;

    @Value("${showroomz.showroom.base-url:https://showroomz.kr/}")
    private String showroomBaseUrl;

    // ------------------------------------------------------------------ 목록(ui-admin-16 rev.7 · 55 계획서 M1)

    /**
     * 인플루언서 회원 목록 — 브랜드 목록과 같은 골격(탭 5종 · 겹치지 않는 건수 · 전체 탭은 탈퇴 승인 대기 상단 고정 · 요약은 검색어 범위).
     * 「최장 대기」는 상세 심사 카드와 같은 날짜 차다(D13-7).
     */
    @Transactional(readOnly = true)
    public ListResponse list(showroomz.api.admin.common.AdminMemberListTab tab, String keyword,
                             showroomz.api.admin.common.AdminMemberListSort sort, showroomz.global.dto.PagingRequest paging) {
        var resolvedTab = tab == null ? showroomz.api.admin.common.AdminMemberListTab.ALL : tab;
        var resolvedSort = sort == null ? showroomz.api.admin.common.AdminMemberListSort.RECENT_JOINED : sort;
        var page = paging == null ? new showroomz.global.dto.PagingRequest() : paging;
        var pageable = page.toPageable(org.springframework.data.domain.Sort.unsorted());
        var k = showroomz.api.admin.common.AdminMemberListKeyword.of(MemberType.CREATOR, keyword);

        org.springframework.data.domain.Page<AdminCreatorListQueryRepository.Row> rows = k.noMatch()
                ? org.springframework.data.domain.Page.empty(pageable) : listQueries.search(resolvedTab, k, resolvedSort, pageable);
        java.util.Map<Long, Long> connected = new java.util.HashMap<>();
        if (!rows.isEmpty()) {
            for (Object[] row : connections.countConnectedPairsByCreatorIn(rows.map(r -> r.creator().getId()).getContent())) {
                connected.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }
        List<ListItem> content = rows.getContent().stream()
                .map(r -> toListItem(r, connected.getOrDefault(r.creator().getId(), 0L)))
                .toList();

        java.util.Map<showroomz.api.admin.common.AdminMemberListTab, Long> counts = k.noMatch()
                ? new java.util.EnumMap<>(showroomz.api.admin.common.AdminMemberListTab.class) : listQueries.countByTab(k);
        Long longestPendingDays = null;
        if (resolvedTab == showroomz.api.admin.common.AdminMemberListTab.WITHDRAWAL_PENDING && !k.noMatch()) {
            LocalDateTime oldest = listQueries.oldestPendingRequestedAt(k);
            longestPendingDays = oldest == null ? null
                    : Math.max(0, ChronoUnit.DAYS.between(oldest.toLocalDate(), LocalDateTime.now().toLocalDate()));
        }
        return new ListResponse(content, new showroomz.global.dto.PaginationInfo(rows),
                new showroomz.api.admin.common.AdminMemberListSummary(
                        counts.getOrDefault(showroomz.api.admin.common.AdminMemberListTab.ALL, 0L),
                        counts.getOrDefault(showroomz.api.admin.common.AdminMemberListTab.ACTIVE, 0L),
                        counts.getOrDefault(showroomz.api.admin.common.AdminMemberListTab.SUSPENDED, 0L),
                        counts.getOrDefault(showroomz.api.admin.common.AdminMemberListTab.WITHDRAWAL_PENDING, 0L),
                        counts.getOrDefault(showroomz.api.admin.common.AdminMemberListTab.WITHDRAWN, 0L),
                        longestPendingDays));
    }

    private static ListItem toListItem(AdminCreatorListQueryRepository.Row row, long connectedBrands) {
        Creator c = row.creator();
        boolean waiting = row.withdrawalRequestedAt() != null;
        var listStatus = showroomz.api.admin.common.AdminMemberListStatus.of(c.isWithdrawn(),
                c.getStatus() == CreatorStatus.SUSPENDED, waiting);
        String businessTypeLabel = c.getBusinessType() == null ? null
                : c.getBusinessType() == CreatorBusinessType.BUSINESS ? "사업자" : "비사업자";
        return new ListItem(c.getId(), MemberNumber.format(MemberType.CREATOR, c.getId()), c.getShowroomName(),
                c.isWithdrawn() ? null : c.getInstagramUrl(), c.getBusinessEmail(), c.getBusinessType(), businessTypeLabel,
                c.getCreatedAt(), connectedBrands, c.getStatus(), c.getStatus().getLabel(), listStatus.name(),
                listStatus.getLabel(), listStatus.getTone(), waiting, row.withdrawalRequestedAt());
    }

    // ------------------------------------------------------------------ 3-1 상세

    public Detail detail(Long creatorId, Long operatorId) {
        Creator creator = load(creatorId);
        return assemble(creator, operatorId, LocalDateTime.now());
    }

    public Detail assemble(Creator creator, Long operatorId, LocalDateTime now) {
        Users user = creator.getUser();
        List<CreatorApplication> applications = queries.findApplications(user.getId());
        LocalDateTime approvedAt = applications.stream()
                .filter(a -> a.getStatus() == CreatorApplicationStatus.APPROVED && a.getProcessedAt() != null)
                .map(CreatorApplication::getProcessedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        DeviceType device = queries.findLatestLogin(user.getId())
                .map(login -> DeviceTypes.parse(login.getUserAgent()))
                .orElse(null);
        CreatorPrivacyAccessLog session = activeSession(creator.getId(), operatorId, now);
        boolean purged = creator.isPrivacyPurged();
        boolean unmasked = session != null && !purged;

        Basic basic = new Basic(creator.getShowroomName(), creator.getProfileImageUrl(), creator.getShowroomAddress(),
                creator.isWithdrawn() || creator.getShowroomAddress() == null ? null : showroomBaseUrl + creator.getShowroomAddress(),
                !creator.isWithdrawn(), creator.getInstagramUrl(), creator.getBusinessEmail(), creator.getCreatedAt(),
                approvedAt, user.getLastLoginAt(), device);

        return new Detail(creator.getId(), MemberNumber.format(MemberType.CREATOR, creator.getId()), creator.getStatus(),
                creator.getStatus().getLabel(), creator.getStatus().getTone(),
                new showroomz.api.admin.common.AdminMemberNavigation(creators.findPreviousMemberId(creator.getId()),
                        creators.findNextMemberId(creator.getId())),
                basic,
                identity(creator, user, unmasked, purged, now), unmask(creator, operatorId, session, purged, now),
                business(creator, purged), settlementAccount(creator, purged),
                rail(creator, user, approvedAt, now));
    }

    private Identity identity(Creator creator, Users user, boolean unmasked, boolean purged, LocalDateTime now) {
        if (purged) {
            return new Identity(true, false, null, null, null, null, null, null, null);
        }
        if (unmasked) {
            return new Identity(false, true, creator.getRealName(), AdminMemberMasker.formatBirthday(creator.getBirthday()),
                    age(creator.getBirthday(), now.toLocalDate()), AdminMemberMasker.formatPhone(creator.getPhoneNumber()),
                    null, user.getIdentityVerifiedAt(), null);
        }
        return new Identity(false, false, AdminMemberMasker.maskName(creator.getRealName()),
                AdminMemberMasker.maskBirthdayYearOnly(creator.getBirthday()), null,
                AdminMemberMasker.maskPhone(creator.getPhoneNumber()), null, user.getIdentityVerifiedAt(), null);
    }

    private Unmask unmask(Creator creator, Long operatorId, CreatorPrivacyAccessLog session, boolean purged,
                          LocalDateTime now) {
        var lastReason = privacyLogs.findFirstByCreatorIdOrderByAccessedAtDescIdDesc(creator.getId())
                .map(CreatorPrivacyAccessLog::getReason)
                .orElse(null);
        boolean allowed = !purged && privilege.canUnmaskPrivacy(operatorId);
        if (session == null || purged) {
            return new Unmask(allowed, false, null, null, null, null, lastReason);
        }
        String operatorName = sellers.findById(session.getOperatorId()).map(s -> s.getName()).orElse("운영자");
        long remaining = Math.max(0, Duration.between(now, session.getExpiresAt()).getSeconds());
        return new Unmask(allowed, true, operatorName, session.getAccessedAt(), session.getExpiresAt(), remaining,
                lastReason);
    }

    private static Business business(Creator creator, boolean purged) {
        CreatorBusinessType type = creator.getBusinessType();
        WithholdingType withholding = WithholdingType.from(type);
        // 사업자 여부 · 등록번호는 세무 보존(53 설계서 0-10) — 탈퇴 뒤에는 내리지 않는다.
        if (purged) {
            return new Business(true, null, null, null, null, null, null, null);
        }
        boolean business = type == CreatorBusinessType.BUSINESS;
        return new Business(false, type, type == null ? null : type.getDescription(),
                business ? creator.getBusinessRegistrationNumber() : null,
                business ? creator.getBusinessLicenseImageUrl() : null,
                type == CreatorBusinessType.INDIVIDUAL ? creator.isResidentNumberRegistered() : null,
                withholding, withholding == null ? null : withholding.getLabel());
    }

    private SettlementAccount settlementAccount(Creator creator, boolean purged) {
        if (purged) {
            return new SettlementAccount(true, null, null, null, null, null);
        }
        AccountChange change = accountHistories.findFirstByCreatorIdOrderByChangedAtDescIdDesc(creator.getId())
                .filter(h -> h.getPreviousBankName() != null || h.getPreviousLast4() != null)
                .map(h -> new AccountChange(h.getChangedAt(), h.getPreviousBankName(), h.getPreviousLast4()))
                .orElse(null);
        // 53 설계서가 비워 둔 자리 — 열린 사업자 구분 변경 요청(§16-2a · 16 설계서 5-1).
        PendingBusinessTypeChange pending = changeRequests.findOpenForCreator(creator.getId())
                .filter(r -> r.getType().isBusinessTypeChange())
                .map(r -> new PendingBusinessTypeChange(r.getId(), r.getRequestCode(), r.getType().name(),
                        r.getRequestedAt()))
                .orElse(null);
        return new SettlementAccount(false, creator.getBankName(), AdminMemberMasker.maskName(creator.getRealName()),
                AdminMemberMasker.maskAccountNumber(creator.getAccountNumber()), change, pending);
    }

    private Rail rail(Creator creator, Users user, LocalDateTime approvedAt, LocalDateTime now) {
        Suspension suspension = null;
        Withdrawal withdrawal = null;
        if (creator.getStatus() == CreatorStatus.SUSPENDED) {
            suspension = sanctions.findByCreatorIdAndReleasedAtIsNull(creator.getId())
                    .map(s -> suspensionOf(s, termPolicy.appealReviewBusinessDays()))
                    .orElse(null);
        }
        if (creator.isWithdrawn()) {
            CreatorWithdrawalRequest approved = withdrawalRequests
                    .findFirstByCreatorIdAndStatusOrderByProcessedAtDesc(creator.getId(), MemberWithdrawalStatus.APPROVED)
                    .orElse(null);
            withdrawal = new Withdrawal(creator.getWithdrawnAt(), approved == null ? null : approved.getRequestedAt(),
                    approved == null ? null : approved.getProcessedByName());
        }
        CurrentStatus current = new CurrentStatus(creator.getStatus(), creator.getStatus().getLabel(),
                creator.getStatus().getTone(), creator.getCreatedAt(), approvedAt, user.getLastLoginAt(), suspension,
                withdrawal);

        WithdrawalReview review = creator.isWithdrawn() ? null : withdrawalRequests
                .findFirstByCreatorIdAndStatusOrderByRequestedAtDesc(creator.getId(), MemberWithdrawalStatus.PENDING)
                .map(r -> review(r, now))
                .orElse(null);
        List<OpenAdjustment> openAdjustments = creator.isWithdrawn() ? List.of()
                : queries.findOpenAdjustments(creator.getId()).stream().map(AdminCreatorQueryService::adjustmentOf).toList();
        List<HistoryItem> histories = histories(creator);
        int railSize = review == null ? RAIL_HISTORY_SIZE : RAIL_HISTORY_SIZE_WITH_REVIEW;
        return new Rail(current, queries.findOperatorChannelThreadId(creator.getId()).orElse(null), openAdjustments,
                review, histories.stream().limit(railSize).toList(), histories.size());
    }

    private WithdrawalReview review(CreatorWithdrawalRequest request, LocalDateTime now) {
        CreatorWithdrawalEligibility.Result result = eligibility.evaluate(request.getCreatorId());
        return new WithdrawalReview(request.getId(), request.getRequestedAt(),
                ChronoUnit.DAYS.between(request.getRequestedAt().toLocalDate(), now.toLocalDate()), request.getDueDate(),
                request.getReasonText(), new Conditions(result.count(CreatorWithdrawalCondition.UNFINISHED_GROUP_BUYS),
                result.count(CreatorWithdrawalCondition.UNFINISHED_CONTRACTS),
                result.count(CreatorWithdrawalCondition.UNSETTLED)));
    }

    static Suspension suspensionOf(CreatorSanction sanction, Integer appealReviewBusinessDays) {
        return new Suspension(sanction.getId(), sanction.getReason(), sanction.getReason().getLabel(),
                sanction.getReasonDetail(), sanction.getSuspendedAt(), sanction.getSuspendedByName(),
                sanction.isPayoutHeld(),
                sanction.isPayoutHeld() ? "지급 정지 · 정지 해제 다음 영업일 지급" : "유지 · 사유가 정산 정보와 무관",
                showroomz.api.common.account.dto.MemberAccountStatusResponse.Term.of(sanction.getEndsOn(), sanction.getSuspendedAt(),
                        sanction.getAppealDeadlineAt(), appealReviewBusinessDays));
    }

    private static OpenAdjustment adjustmentOf(SettlementAdjustment a) {
        return new OpenAdjustment(a.getId(), a.getSettlementId(), a.getSettlementNumber(), a.getDeadlineAt(), a.getThreadId());
    }

    // ------------------------------------------------------------------ 3-2 개요

    public Overview overview(Long creatorId) {
        Creator creator = load(creatorId);
        Long id = creator.getId();
        Operations operations = new Operations(queries.countConnectedBrands(id), queries.countSellingGroupBuys(id),
                queries.sumRewardInProgress(id), queries.findOpenAdjustments(id).size());
        long[] threads = queries.countPairThreads(id);
        long generalSuspensions = postNotificationLogs.countSanctionEventsByCreator(id).suspensionCount();
        Activity activity = new Activity(queries.countGroupBuyPosts(id),
                queries.countGroupBuyEvents(id, GroupBuyEventType.OPEN_REJECTED),
                queries.countGroupBuyEvents(id, GroupBuyEventType.POST_HIDDEN) + generalSuspensions,
                queries.countGeneralPosts(id), follows.countByCreator_Id(id), threads[0], threads[1]);
        return new Overview(operations, activity);
    }

    // ------------------------------------------------------------------ 3-3 최근 정산

    public SettlementList settlements(Long creatorId, int size) {
        Creator creator = load(creatorId);
        int limit = Math.min(Math.max(size, 1), 50);
        List<Settlement> rows = queries.findRecentSettlements(creator.getId(), limit);
        List<Long> ids = rows.stream().map(Settlement::getId).toList();
        Map<Long, SettlementPayout> creatorPayouts = ids.isEmpty() ? Map.of() : payouts.findBySettlementIdIn(ids).stream()
                .filter(p -> p.getPayee() == SettlementPayee.CREATOR)
                .collect(Collectors.toMap(SettlementPayout::getSettlementId, Function.identity(), (a, b) -> a));
        Map<Long, SettlementAdjustment> openAdjustments = ids.isEmpty() ? Map.of() : adjustments.findBySettlementIdIn(ids)
                .stream()
                .filter(a -> !a.getStatus().isClosed())
                .collect(Collectors.toMap(SettlementAdjustment::getSettlementId, Function.identity(), (a, b) -> a));
        List<SettlementRow> content = rows.stream().map(s -> {
            SettlementPayout payout = creatorPayouts.get(s.getId());
            SettlementAdjustment adjustment = openAdjustments.get(s.getId());
            return new SettlementRow(s.getId(), s.getSettlementNumber(), groupBuyIdOf(s),
                    s.getContract() == null ? null : s.getContract().getTitle(), s.getMarket().getMarketName(), s.getStatus(),
                    s.getStatus().getLabel(), s.getStatus().getTone().name(), s.getRewardAmount(),
                    payout == null ? null : payout.getStatus(), payout == null ? null : payout.getStatus().getLabel(),
                    s.getPayoutDueDate(), s.getPaidAt(), adjustment == null ? null : adjustmentOf(adjustment));
        }).toList();
        return new SettlementList(queries.countSettlements(creator.getId()), content);
    }

    private static Long groupBuyIdOf(Settlement settlement) {
        return settlement.getGroupBuy() == null ? null : settlement.getGroupBuy().getId();
    }

    // ------------------------------------------------------------------ 3-4 계약

    public showroomz.global.dto.PageResponse<ContractRow> contracts(Long creatorId, int page, int size) {
        Creator creator = load(creatorId);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 1);
        List<Contract> rows = queries.findReceivedContracts(creator.getId(), (safePage - 1) * safeSize, safeSize);
        List<Long> groupBuyIds = rows.stream().map(Contract::getGroupBuyId).filter(Objects::nonNull).toList();
        Map<Long, LocalDateTime> endedAt = queries.findGroupBuyEndedAt(groupBuyIds);
        Map<Long, LocalDateTime> postApprovedAt = queries.findPostApprovedAt(groupBuyIds);
        Map<Long, LocalDateTime> paidAt = queries.findSettlementPaidAt(groupBuyIds);
        List<ContractRow> content = rows.stream().map(c -> {
            LocalDateTime reached = null;
            if (c.getFixedFeeTrigger() != null && c.getGroupBuyId() != null) {
                reached = switch (c.getFixedFeeTrigger()) {
                    case POST_REGISTERED -> postApprovedAt.get(c.getGroupBuyId());
                    case GROUP_BUY_ENDED -> endedAt.get(c.getGroupBuyId());
                    case SETTLEMENT_COMPLETED -> paidAt.get(c.getGroupBuyId());
                };
            }
            boolean hasFee = c.hasFixedFee();
            return new ContractRow(c.getId(), c.getContractNumber(), c.getMarket().getMarketName(), c.getTitle(), c.getStatus(),
                    c.getStatus().getLabel(), c.getStatus().getTone().name(), hasFee ? c.getFixedFeeAmount() : null,
                    hasFee ? c.getFixedFeeTrigger() : null,
                    hasFee && c.getFixedFeeTrigger() != null ? c.getFixedFeeTrigger().getLabel() : null,
                    hasFee ? reached : null, hasFee ? c.getFixedFeePaidAt() : null);
        }).toList();
        org.springframework.data.domain.Page<ContractRow> pageResult = new org.springframework.data.domain.PageImpl<>(
                content, org.springframework.data.domain.PageRequest.of(safePage - 1, safeSize),
                queries.countReceivedContracts(creator.getId()));
        return showroomz.global.dto.PageResponse.of(pageResult);
    }

    // ------------------------------------------------------------------ 3-5 연결 브랜드

    public ConnectionList connections(Long creatorId) {
        Creator creator = load(creatorId);
        Map<Long, Long> selling = queries.countSellingGroupBuysByMarket(creator.getId());
        List<ConnectionRow> content = queries.findListedConnections(creator.getId()).stream()
                .sorted(Comparator.comparing((Connection c) -> c.getStatus() == ConnectionStatus.CONNECTED ? 0 : 1)
                        .thenComparing(c -> c.getRespondedAt() == null ? c.getRequestedAt() : c.getRespondedAt(),
                                Comparator.reverseOrder()))
                .map(c -> new ConnectionRow(c.getId(), c.getMarket().getId(), c.getMarket().getMarketName(),
                        c.getRespondedAt(), selling.getOrDefault(c.getMarket().getId(), 0L), c.getStatus(),
                        c.getDisconnectedAt()))
                .toList();
        return new ConnectionList(content.size(), content);
    }

    // ------------------------------------------------------------------ 모달 사유 목록

    /** 정지 · 해제 · 탈퇴 반려 · 마스킹 해제 사유의 코드와 라벨 — 단일 소스(브랜드 상세와 같은 조립식). */
    public ReasonOptions reasonOptions() {
        return new ReasonOptions(
                AdminReasonOption.suspension(CreatorSuspensionReason.values()),
                AdminReasonOption.memberRelease(),
                AdminReasonOption.of(CreatorWithdrawalRejectReason.values()),
                Arrays.stream(CreatorPrivacyAccessReason.values())
                        .map(r -> AdminReasonOption.label(r.name(), r.getLabel()))
                        .toList());
    }

    // ------------------------------------------------------------------ 3-6 처리 이력

    public HistoryPage histories(Long creatorId, int page, int size) {
        Creator creator = load(creatorId);
        List<HistoryItem> all = histories(creator);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 1);
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return new HistoryPage(all.size(), safePage, safeSize, all.subList(from, to));
    }

    private List<HistoryItem> histories(Creator creator) {
        return historyReader.read(creator).stream()
                .map(item -> new HistoryItem(item.historyId(), item.type(), item.type().getLabel(), item.type().getTone(),
                        item.detail(), item.actorType(), item.actorName(), item.refType(), item.refId(), item.occurredAt()))
                .toList();
    }

    // ------------------------------------------------------------------ 공통

    public Creator load(Long creatorId) {
        return creators.findWithUserById(creatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }

    private CreatorPrivacyAccessLog activeSession(Long creatorId, Long operatorId, LocalDateTime now) {
        if (operatorId == null) {
            return null;
        }
        return privacyLogs.findFirstByCreatorIdAndOperatorIdOrderByAccessedAtDescIdDesc(creatorId, operatorId)
                .filter(log -> log.isActive(now))
                .orElse(null);
    }

    private static Integer age(String birthday, LocalDate today) {
        if (birthday == null) {
            return null;
        }
        String digits = birthday.replaceAll("[^0-9]", "");
        if (digits.length() < 8) {
            return null;
        }
        try {
            LocalDate born = LocalDate.of(Integer.parseInt(digits.substring(0, 4)), Integer.parseInt(digits.substring(4, 6)),
                    Integer.parseInt(digits.substring(6, 8)));
            return Period.between(born, today).getYears();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** User-Agent → 기기 — 로그인 이력 화면(기획 제외 패키지)의 판별과 같은 규칙을 여기에 둔다(그 패키지를 참조하지 않는다). */
    static final class DeviceTypes {

        private DeviceTypes() {
        }

        static DeviceType parse(String userAgent) {
            if (userAgent == null || userAgent.isEmpty()) {
                return DeviceType.UNKNOWN;
            }
            if (userAgent.contains("Android")) {
                return DeviceType.ANDROID;
            }
            if (userAgent.contains("iPhone")) {
                return DeviceType.IPHONE;
            }
            if (userAgent.contains("Edg")) {
                return DeviceType.DESKTOP_EDGE;
            }
            if (userAgent.contains("Chrome") && !userAgent.contains("Mobile")) {
                return DeviceType.DESKTOP_CHROME;
            }
            return DeviceType.UNKNOWN;
        }
    }
}
