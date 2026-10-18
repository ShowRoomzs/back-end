package showroomz.api.admin.brand.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.brand.dto.AdminBrandDto;
import showroomz.api.admin.brand.type.AdminBrandSort;
import showroomz.api.admin.brand.type.AdminBrandTab;
import showroomz.api.admin.common.AdminMemberMasker;
import showroomz.api.admin.common.AdminReasonOption;
import showroomz.api.admin.common.MemberNumber;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.repository.ChangeRequestRepository;
import showroomz.domain.changerequest.service.ChangeRequestFieldResolver;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.member.brand.entity.BrandAdminMemo;
import showroomz.domain.member.brand.entity.BrandSanction;
import showroomz.domain.member.brand.entity.BrandWithdrawalRequest;
import showroomz.domain.member.brand.repository.BrandAdminMemoRepository;
import showroomz.domain.member.brand.repository.BrandSanctionRepository;
import showroomz.domain.member.brand.repository.BrandWithdrawalRequestRepository;
import showroomz.domain.member.brand.repository.MarketShipLeadHistoryRepository;
import showroomz.domain.member.brand.service.BrandAdminHistoryReader;
import showroomz.domain.member.brand.type.BrandMemberStatus;
import showroomz.domain.member.brand.type.BrandPendingChip;
import showroomz.domain.member.brand.type.BrandSuspensionReason;
import showroomz.domain.member.brand.type.BrandWithdrawalRejectReason;
import showroomz.domain.member.common.type.MemberType;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.service.MessageThreadService;
import showroomz.domain.message.type.ThreadKind;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.service.ShipDuePolicy;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.dto.PaginationInfo;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 어드민 브랜드 상세 · 목록 조회(52 설계서 2절). 숫자는 전부 다른 화면이 이미 세는 식을 쓴다 — 「두 화면 숫자가 다르다」를 만들지 않는다.
 * 집계 리더 하나가 실패하면 카드를 비우지 않고 예외다 — 틀린 숫자가 빈 칸보다 나쁘다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBrandQueryService {

    static final int RAIL_HISTORY_LIMIT = 3;
    static final int RAIL_HISTORY_LIMIT_WITH_REVIEW = 2;
    static final int CONNECTED_CREATOR_PREVIEW = 3;

    private final MarketRepository marketRepository;
    private final BrandSanctionRepository sanctionRepository;
    private final BrandWithdrawalRequestRepository withdrawalRepository;
    private final BrandAdminMemoRepository memoRepository;
    private final MarketShipLeadHistoryRepository shipLeadHistoryRepository;
    private final BrandAdminHistoryReader historyReader;
    private final ChangeRequestRepository changeRequestRepository;
    private final ConnectionRepository connectionRepository;
    private final MessageThreadRepository threadRepository;
    private final MessageThreadService messageThreadService;
    private final GroupBuyRepository groupBuyRepository;
    private final ContractRepository contractRepository;
    private final ProductRepository productRepository;
    private final ProductInquiryRepository productInquiryRepository;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderClaimRepository claimRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementTaxDocumentRepository taxDocumentRepository;

    /** 모달 셀렉트 사유 목록(정합 계획서 R1-9) — enum 단일 소스. 인플루언서 상세와 같은 조립식이다. */
    public AdminBrandDto.ReasonOptions reasonOptions() {
        return new AdminBrandDto.ReasonOptions(
                AdminReasonOption.suspension(BrandSuspensionReason.values()),
                AdminReasonOption.memberRelease(),
                AdminReasonOption.of(BrandWithdrawalRejectReason.values()));
    }

    // ------------------------------------------------------------------ 목록

    public AdminBrandDto.ListResponse list(AdminBrandTab tab, String keyword, AdminBrandSort sort, PagingRequest paging) {
        AdminBrandTab resolvedTab = tab == null ? AdminBrandTab.ALL : tab;
        AdminBrandSort resolvedSort = sort == null ? AdminBrandSort.RECENT_JOINED : sort;
        PagingRequest page = paging == null ? new PagingRequest() : paging;
        Keyword k = Keyword.of(keyword);

        Page<Market> markets = k.noMatch()
                ? Page.empty(page.toPageable(resolvedSort.toSort()))
                : marketRepository.searchBrandMembers(resolvedTab.getStatuses(), k.marketId(), k.name(),
                page.toPageable(resolvedSort.toSort()));
        Set<Long> pendingIds = markets.isEmpty() ? Set.of()
                : new HashSet<>(withdrawalRepository.findPendingMarketIds(markets.map(Market::getId).getContent()));

        List<AdminBrandDto.ListItem> content = markets.getContent().stream().map(m -> {
            Seller s = m.getSeller();
            BrandMemberStatus status = BrandMemberStatus.of(m.getStatus());
            boolean withdrawn = status == BrandMemberStatus.WITHDRAWN;
            return new AdminBrandDto.ListItem(m.getId(), MemberNumber.format(MemberType.BRAND, m.getId()), m.getMarketName(),
                    withdrawn ? null : s.getName(), withdrawn ? null : s.getBusinessType(), status.name(),
                    status.getLabel(), pendingIds.contains(m.getId()), s.getCreatedAt(), s.getLastLoginAt());
        }).toList();

        Map<BrandMemberStatus, Long> counts = new EnumMap<>(BrandMemberStatus.class);
        if (!k.noMatch()) {
            for (Object[] row : marketRepository.countBrandMembersByStatus(k.marketId(), k.name())) {
                counts.merge(BrandMemberStatus.of((MarketStatus) row[0]), ((Number) row[1]).longValue(), Long::sum);
            }
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new AdminBrandDto.ListResponse(content, new PaginationInfo(markets),
                new AdminBrandDto.ListSummary(total, counts.getOrDefault(BrandMemberStatus.ACTIVE, 0L),
                        counts.getOrDefault(BrandMemberStatus.SUSPENDED, 0L),
                        counts.getOrDefault(BrandMemberStatus.WITHDRAWN, 0L), withdrawalRepository.countPending()));
    }

    /** 검색어 축 — {@code BRD-}로 시작하면 회원번호 정확 일치(숫자가 아니면 0건) · 그 외 브랜드명 부분 일치. */
    private record Keyword(Long marketId, String name, boolean noMatch) {
        static Keyword of(String keyword) {
            if (keyword == null || keyword.isBlank()) {
                return new Keyword(null, null, false);
            }
            if (MemberNumber.looksLike(MemberType.BRAND, keyword)) {
                Long id = MemberNumber.parseOrNull(MemberType.BRAND, keyword);
                return new Keyword(id, null, id == null);
            }
            return new Keyword(null, keyword.trim(), false);
        }
    }

    // ------------------------------------------------------------------ 상세

    public AdminBrandDto.Detail detail(Long marketId, Long operatorId) {
        Market market = loadBrand(marketId);
        Seller seller = market.getSeller();
        BrandMemberStatus status = BrandMemberStatus.of(market.getStatus());
        boolean purged = status == BrandMemberStatus.WITHDRAWN;
        Optional<BrandWithdrawalRequest> pending = purged ? Optional.empty()
                : withdrawalRepository.findFirstByMarketIdAndStatusOrderByRequestedAtDesc(marketId,
                MemberWithdrawalStatus.PENDING);
        Optional<MessageThread> thread = operatorThread(market);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime approvedAt = historyReader.approvedAt(seller);

        AdminBrandDto.BasicInfo basic = new AdminBrandDto.BasicInfo(
                purged ? null : seller.getName(), seller.getPhoneNumber(), purged ? null : seller.getEmail(),
                market.getBrandSiteUrl(), seller.getCreatedAt(), approvedAt);
        AdminBrandDto.BusinessInfo business = new AdminBrandDto.BusinessInfo(seller.getBusinessType(),
                seller.getCompanyName(), seller.getRepresentativeName(),
                purged ? null : seller.getBusinessRegistrationNumber(), seller.getMailOrderRegNumber(),
                ChangeRequestFieldResolver.combineAddress(seller.getBusinessAddress(), seller.getDetailAddress()));
        AdminBrandDto.SettlementInfo settlementInfo = new AdminBrandDto.SettlementInfo(seller.getBankName(),
                seller.getAccountHolder(), AdminMemberMasker.maskAccountNumber(seller.getAccountNumber()),
                seller.getTaxEmail(), purged ? List.of() : pendingChangeRequests(marketId));

        Long threadId = thread.map(MessageThread::getId).orElse(null);
        AdminBrandDto.Rail rail = new AdminBrandDto.Rail(currentStatus(market, approvedAt, threadId),
                purged ? List.of() : pendingChips(marketId, thread.orElse(null), now),
                pending.map(r -> withdrawalReview(r, now)).orElse(null),
                histories(market, pending.isPresent() ? RAIL_HISTORY_LIMIT_WITH_REVIEW : RAIL_HISTORY_LIMIT));

        Page<BrandAdminMemo> latestMemo = memoRepository.findByMarketIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                marketId, PageRequest.of(0, 1));
        AdminBrandDto.Memos memos = new AdminBrandDto.Memos(
                latestMemo.getContent().stream().map(m -> toMemo(m, operatorId, purged, now)).toList(),
                latestMemo.getTotalElements());

        return new AdminBrandDto.Detail(marketId, MemberNumber.format(MemberType.BRAND, marketId), market.getMarketName(),
                status.name(), status.getLabel(), pending.isPresent(), purged,
                new AdminBrandDto.Navigation(marketRepository.findPreviousBrandMemberId(marketId),
                        marketRepository.findNextBrandMemberId(marketId)),
                basic, business, settlementInfo, deliveryPolicy(market), operations(marketId),
                connectedCreatorsPreview(marketId),
                thread.map(t -> new AdminBrandDto.OperatorThread(t.getId(), unread(t), t.getLastMessageAt())).orElse(null),
                rail, memos);
    }

    /** 조치 응답 — 현재 상태 카드만 다시 그린다. */
    public AdminBrandDto.CurrentStatus currentStatus(Long marketId) {
        Market market = loadBrand(marketId);
        Long threadId = operatorThread(market).map(MessageThread::getId).orElse(null);
        return currentStatus(market, historyReader.approvedAt(market.getSeller()), threadId);
    }

    public AdminBrandDto.Rail rail(Long marketId) {
        Market market = loadBrand(marketId);
        Optional<MessageThread> thread = operatorThread(market);
        boolean purged = market.getStatus() == MarketStatus.WITHDRAWN;
        Optional<BrandWithdrawalRequest> pending = purged ? Optional.empty()
                : withdrawalRepository.findFirstByMarketIdAndStatusOrderByRequestedAtDesc(marketId,
                MemberWithdrawalStatus.PENDING);
        LocalDateTime now = LocalDateTime.now();
        return new AdminBrandDto.Rail(
                currentStatus(market, historyReader.approvedAt(market.getSeller()), thread.map(MessageThread::getId).orElse(null)),
                purged ? List.of() : pendingChips(marketId, thread.orElse(null), now),
                pending.map(r -> withdrawalReview(r, now)).orElse(null),
                histories(market, pending.isPresent() ? RAIL_HISTORY_LIMIT_WITH_REVIEW : RAIL_HISTORY_LIMIT));
    }

    public AdminBrandDto.HistoryPage historyPage(Long marketId, PagingRequest paging) {
        Market market = loadBrand(marketId);
        PagingRequest page = paging == null ? new PagingRequest() : paging;
        Pageable pageable = page.toPageable(org.springframework.data.domain.Sort.unsorted());
        List<BrandAdminHistoryReader.Item> all = historyReader.read(market);
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        Page<BrandAdminHistoryReader.Item> slice = new PageImpl<>(all.subList(from, to), pageable, all.size());
        return new AdminBrandDto.HistoryPage(slice.getContent().stream().map(AdminBrandQueryService::toHistory).toList(),
                new PaginationInfo(slice));
    }

    public AdminBrandDto.MemoPage memoPage(Long marketId, Long operatorId, PagingRequest paging) {
        Market market = loadBrand(marketId);
        boolean purged = market.getStatus() == MarketStatus.WITHDRAWN;
        PagingRequest page = paging == null ? new PagingRequest() : paging;
        LocalDateTime now = LocalDateTime.now();
        Page<BrandAdminMemo> memos = memoRepository.findByMarketIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                marketId, page.toPageable(org.springframework.data.domain.Sort.unsorted()));
        return new AdminBrandDto.MemoPage(memos.getContent().stream().map(m -> toMemo(m, operatorId, purged, now)).toList(),
                new PaginationInfo(memos));
    }

    public AdminBrandDto.ConnectedCreatorPage connectedCreatorPage(Long marketId, PagingRequest paging) {
        loadBrand(marketId);
        PagingRequest page = paging == null ? new PagingRequest() : paging;
        Page<Connection> pairs = connectionRepository.findPairsForAdminByMarket(marketId, page.toPageable(org.springframework.data.domain.Sort.unsorted()));
        Map<Long, Long> selling = sellingByCreator(marketId);
        return new AdminBrandDto.ConnectedCreatorPage(
                pairs.getContent().stream().map(c -> toConnectedCreator(c, selling)).toList(), new PaginationInfo(pairs));
    }

    // ------------------------------------------------------------------ 카드 조립

    /** 회원 관리 대상 — 입점이 끝난 브랜드(셀러 APPROVED · SELLER)만. 그 밖은 존재를 알리지 않는다. */
    public Market loadBrand(Long marketId) {
        Market market = marketRepository.findByIdWithSeller(marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
        Seller seller = market.getSeller();
        if (seller.getRoleType() != RoleType.SELLER || seller.getStatus() != SellerStatus.APPROVED) {
            throw new BusinessException(ErrorCode.MARKET_NOT_FOUND);
        }
        return market;
    }

    private List<AdminBrandDto.PendingChangeRequest> pendingChangeRequests(Long marketId) {
        return changeRequestRepository.findByMarket_IdAndStatusOrderByRequestedAtAsc(marketId, ChangeRequestStatus.PENDING)
                .stream()
                .map((ChangeRequest r) -> new AdminBrandDto.PendingChangeRequest(r.getId(), r.getRequestCode(),
                        r.getType().name(), BrandAdminHistoryReader.typeLabel(r.getType()), r.getRequestedAt()))
                .toList();
    }

    private AdminBrandDto.DeliveryPolicy deliveryPolicy(Market market) {
        int days = ShipDuePolicy.businessDaysOf(market);
        AdminBrandDto.ShipLeadChange change = shipLeadHistoryRepository
                .findFirstByMarketIdOrderByChangedAtDescIdDesc(market.getId())
                .map(h -> new AdminBrandDto.ShipLeadChange(h.getChangedAt(), h.getPreviousDays(), h.getNewDays()))
                .orElse(null);
        return new AdminBrandDto.DeliveryPolicy(market.getDefaultDeliveryFee(), market.getFreeShippingThreshold(),
                market.getJejuSurcharge(), market.getRemoteAreaSurcharge(), market.getReturnFee(), market.getExchangeFee(), days,
                ShipDuePolicy.noticeText(days), change,
                new AdminBrandDto.ReturnAddress(market.getShippingRecipientName(), market.getShippingContact(),
                        market.getShippingAddress(), market.getShippingDetailAddress()));
    }

    private AdminBrandDto.Operations operations(Long marketId) {
        long activeGroupBuys = 0;
        for (Object[] row : groupBuyRepository.countByStatus(marketId)) {
            if (GroupBuyStatus.SELLING.contains((GroupBuyStatus) row[0])) {
                activeGroupBuys += ((Number) row[1]).longValue();
            }
        }
        long settled = 0;
        long unsettled = 0;
        for (Object[] row : payoutRepository.aggregateForMarket(marketId, SettlementPayee.BRAND)) {
            PayoutStatus payoutStatus = (PayoutStatus) row[0];
            long amount = ((Number) row[2]).longValue();
            if (payoutStatus == PayoutStatus.PAID) {
                settled += amount;
            } else if (payoutStatus != PayoutStatus.NOT_APPLICABLE) {
                unsettled += amount;
            }
        }
        long signing = 0;
        long concluded = 0;
        for (Object[] row : contractRepository.countByStatus(marketId)) {
            long count = ((Number) row[1]).longValue();
            if (row[0] == ContractStatus.SIGNING) {
                signing += count;
            } else if (row[0] == ContractStatus.CONCLUDED) {
                concluded += count;
            }
        }
        return new AdminBrandDto.Operations(connectionRepository.countConnectedPairsByMarket(marketId), activeGroupBuys,
                settled, unsettled, sanctionRepository.existsOpenPayoutHold(marketId),
                new AdminBrandDto.ContractCounts(signing, concluded),
                new AdminBrandDto.ProductCounts(productRepository.countSellingByMarket(marketId),
                        productRepository.countSoldOutByMarket(marketId)));
    }

    private AdminBrandDto.ConnectedCreators connectedCreatorsPreview(Long marketId) {
        Page<Connection> pairs = connectionRepository.findPairsForAdminByMarket(marketId,
                PageRequest.of(0, CONNECTED_CREATOR_PREVIEW));
        Map<Long, Long> selling = sellingByCreator(marketId);
        return new AdminBrandDto.ConnectedCreators(connectionRepository.countConnectedPairsByMarket(marketId),
                pairs.getContent().stream().map(c -> toConnectedCreator(c, selling)).toList());
    }

    private Map<Long, Long> sellingByCreator(Long marketId) {
        Map<Long, Long> selling = new HashMap<>();
        for (Object[] row : groupBuyRepository.countSellingByCreatorForMarket(marketId)) {
            selling.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return selling;
    }

    private static AdminBrandDto.ConnectedCreator toConnectedCreator(Connection c, Map<Long, Long> selling) {
        return new AdminBrandDto.ConnectedCreator(c.getCreator().getId(), c.getCreator().getShowroomName(),
                c.getRespondedAt(), c.getDisconnectedAt(), selling.getOrDefault(c.getCreator().getId(), 0L),
                c.getStatus().name(), c.getStatus() == showroomz.domain.connection.type.ConnectionStatus.CONNECTED
                ? "연결중" : "연결 종료");
    }

    private Optional<MessageThread> operatorThread(Market market) {
        return connectionRepository.findByTypeAndMarket(ConnectionType.OPERATOR_MARKET, market)
                .flatMap(c -> threadRepository.findByConnectionAndKind(c, ThreadKind.CONNECTION));
    }

    private long unread(MessageThread thread) {
        return messageThreadService.countUnreadForOperatorTeam(List.of(thread.getId()))
                .getOrDefault(thread.getId(), 0L);
    }

    private AdminBrandDto.CurrentStatus currentStatus(Market market, LocalDateTime approvedAt, Long threadId) {
        Seller seller = market.getSeller();
        BrandMemberStatus status = BrandMemberStatus.of(market.getStatus());
        Long marketId = market.getId();
        AdminBrandDto.Sanction sanction = null;
        AdminBrandDto.Withdrawal withdrawal = null;
        if (status == BrandMemberStatus.SUSPENDED) {
            sanction = sanctionRepository.findByMarketIdAndReleasedAtIsNull(marketId)
                    .map(s -> toSanction(s, threadId)).orElse(null);
        }
        if (status == BrandMemberStatus.WITHDRAWN) {
            withdrawal = withdrawalRepository.findFirstByMarketIdAndStatusOrderByProcessedAtDesc(marketId,
                            MemberWithdrawalStatus.APPROVED)
                    .map(r -> new AdminBrandDto.Withdrawal(r.getRequestedAt(),
                            r.getReason() == null ? null : r.getReason().name(), r.reasonLabel(), r.getMessage(),
                            r.getProcessedAt(), r.getProcessedByName()))
                    .orElse(null);
        }
        boolean pending = status != BrandMemberStatus.WITHDRAWN
                && withdrawalRepository.existsByMarketIdAndStatus(marketId, MemberWithdrawalStatus.PENDING);
        AdminBrandDto.Actions actions = new AdminBrandDto.Actions(status == BrandMemberStatus.ACTIVE,
                status == BrandMemberStatus.SUSPENDED, pending);
        return new AdminBrandDto.CurrentStatus(marketId, status.name(), status.getLabel(), status.getTone(),
                seller.getCreatedAt(), approvedAt, seller.getLastLoginAt(), sanction, withdrawal, actions);
    }

    private static AdminBrandDto.Sanction toSanction(BrandSanction s, Long threadId) {
        return new AdminBrandDto.Sanction(s.getId(), s.getReason().name(), s.getReason().getLabel(), s.getReasonDetail(),
                s.isPayoutHeld(), s.getSuspendedAt(), s.getSuspendedByName(), threadId);
    }

    /** 진행 중 칩 8종 — 1건 이상만(§52-3). */
    private List<AdminBrandDto.PendingChip> pendingChips(Long marketId, MessageThread thread, LocalDateTime now) {
        Map<BrandPendingChip, Long> counts = new EnumMap<>(BrandPendingChip.class);
        counts.put(BrandPendingChip.SHIP_OVERDUE, deliveryGroupRepository.countShipOverdue(marketId, now));
        long cancelPending = 0;
        for (Object[] row : deliveryGroupRepository.countPendingCancelByStatus(marketId)) {
            cancelPending += ((Number) row[1]).longValue();
        }
        counts.put(BrandPendingChip.CANCEL_RESPONSE_PENDING, cancelPending);
        long inspection = 0;
        long reship = 0;
        for (Object[] row : claimRepository.countByStatusAndType(marketId)) {
            ClaimStatus claimStatus = (ClaimStatus) row[0];
            long count = ((Number) row[2]).longValue();
            if (ClaimTab.INSPECTION.getStatuses().contains(claimStatus)) {
                inspection += count;
            } else if (ClaimTab.RESHIP.getStatuses().contains(claimStatus)) {
                reship += count;
            }
        }
        counts.put(BrandPendingChip.INSPECTION_PENDING, inspection);
        counts.put(BrandPendingChip.RESHIP_PENDING, reship);
        counts.put(BrandPendingChip.PRODUCT_INQUIRY_UNANSWERED, productInquiryRepository.countUnansweredByMarket(marketId));
        counts.put(BrandPendingChip.OPERATOR_THREAD_UNREAD, thread == null ? 0L : unread(thread));
        long adjusting = 0;
        for (Object[] row : settlementRepository.countByStatusForMarket(marketId)) {
            if (row[0] == SettlementStatus.ADJUSTING) {
                adjusting += ((Number) row[1]).longValue();
            }
        }
        counts.put(BrandPendingChip.SETTLEMENT_ADJUSTING, adjusting);
        counts.put(BrandPendingChip.BRAND_TAX_INVOICE_PENDING, taxDocumentRepository.countBrandInvoicesPendingIssue(marketId));

        List<AdminBrandDto.PendingChip> chips = new ArrayList<>();
        counts.forEach((chip, count) -> {
            if (count > 0) {
                chips.add(new AdminBrandDto.PendingChip(chip.name(), chip.getLabel(), chip.getTone(), count,
                        chip.getTarget(), chip == BrandPendingChip.OPERATOR_THREAD_UNREAD && thread != null
                        ? thread.getId() : null));
            }
        });
        return chips;
    }

    private static AdminBrandDto.WithdrawalReview withdrawalReview(BrandWithdrawalRequest r, LocalDateTime now) {
        long elapsedDays = Math.max(0, Duration.between(r.getRequestedAt(), now).toDays());
        return new AdminBrandDto.WithdrawalReview(r.getId(), r.getRequestedAt(), elapsedDays, r.getRequesterName(),
                r.getReason() == null ? null : r.getReason().name(), r.reasonLabel(), r.getMessage());
    }

    private AdminBrandDto.Histories histories(Market market, int limit) {
        List<BrandAdminHistoryReader.Item> all = historyReader.read(market);
        return new AdminBrandDto.Histories(all.stream().limit(limit).map(AdminBrandQueryService::toHistory).toList(),
                all.size());
    }

    private static AdminBrandDto.HistoryItem toHistory(BrandAdminHistoryReader.Item item) {
        return new AdminBrandDto.HistoryItem(item.type().name(), item.type().getLabel(), item.type().getTone(),
                item.actorType().name(), item.actorName(), item.detail(), item.refType(), item.refId(), item.occurredAt());
    }

    private static AdminBrandDto.Memo toMemo(BrandAdminMemo memo, Long operatorId, boolean purged, LocalDateTime now) {
        boolean editable = !purged && memo.isEditableBy(operatorId, now);
        return new AdminBrandDto.Memo(memo.getId(), memo.getAuthorName(), memo.getContent(), memo.getCreatedAt(),
                memo.getModifiedAt(), editable);
    }
}
