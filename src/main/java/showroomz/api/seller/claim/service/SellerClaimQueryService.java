package showroomz.api.seller.claim.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.order.service.OrderAddressMasker;
import showroomz.api.seller.claim.dto.SellerClaimDetailResponse;
import showroomz.api.seller.claim.dto.SellerClaimListItem;
import showroomz.api.seller.claim.dto.SellerClaimSummaryResponse;
import showroomz.api.seller.order.service.SellerOrderAccessGuard;
import showroomz.api.seller.order.service.SellerOrderAccessGuard.SellerScope;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimAttachment;
import showroomz.domain.order.entity.OrderClaimCharge;
import showroomz.domain.order.entity.OrderClaimCollection;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderClaimAttachmentRepository;
import showroomz.domain.order.repository.OrderClaimChargeRepository;
import showroomz.domain.order.repository.OrderClaimHistoryRepository;
import showroomz.domain.order.repository.OrderClaimNoticeRepository;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.SellerClaimSearchCondition;
import showroomz.domain.order.type.ClaimAttachmentOwner;
import showroomz.domain.order.type.ClaimChargeStatus;
import showroomz.domain.order.type.ClaimChargeType;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.order.type.ClaimType;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 파트너센터 반품·교환 조회(35 설계서 4-1 ~ 4-3). 탭의 기본 기간·정렬과 1년 상한은 여기서 끝낸다.
 * 목록은 페이지의 클레임 id 로 테이블당 IN 쿼리 1번씩 모아 조립한다 — 행 수에 비례하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerClaimQueryService {

    /** 접수번호는 PK 의 표기다 — 키워드가 이 모양이면 PK 조회로 바꾼다. 아니면 주문번호 검색. */
    private static final Pattern CLAIM_NUMBER = Pattern.compile("^CLM-?(\\d+)$", Pattern.CASE_INSENSITIVE);
    /** 기간 필터의 기본 — 신청일시 기준 1개월. */
    private static final int DEFAULT_PERIOD_DAYS = 30;

    private final SellerOrderAccessGuard accessGuard;
    private final OrderClaimRepository claimRepository;
    private final OrderClaimAttachmentRepository attachmentRepository;
    private final OrderClaimHistoryRepository historyRepository;
    private final OrderClaimNoticeRepository noticeRepository;
    private final OrderClaimChargeRepository chargeRepository;
    private final OrderProductRepository orderProductRepository;
    private final SellerClaimAssembler assembler;
    private final OrderProperties orderProperties;
    private final showroomz.domain.order.repository.OrderClaimFreightAdjustmentRepository freightAdjustmentRepository;
    private final showroomz.domain.order.service.ClaimFeePolicy feePolicy;
    private final showroomz.domain.order.service.OrderFulfillmentService fulfillmentService;

    public PageResponse<SellerClaimListItem> getClaims(String sellerEmail, ClaimTab tab, Set<ClaimType> types,
                                                       ClaimReason reason, LocalDate from, LocalDate to,
                                                       String keyword, PagingRequest pagingRequest) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        return searchClaims(scope.market().getId(), tab, types, reason, from, to, keyword, pagingRequest);
    }

    /** 목록 — {@code marketId}가 null 이면 전 브랜드(어드민 거래 관리 06b · 1009 기획 수정본 8-3). */
    public PageResponse<SellerClaimListItem> searchClaims(Long marketId, ClaimTab tab, Set<ClaimType> types,
                                                          ClaimReason reason, LocalDate from, LocalDate to,
                                                          String keyword, PagingRequest pagingRequest) {
        return searchClaims(marketId, tab, types, reason, from, to, keyword, null, null, null, null, false,
                pagingRequest);
    }

    /**
     * 어드민 06b — 파트너와 같은 조회에 브랜드명 · 소비자명 검색과 정렬 셀렉트를 더한다(38 설계서 7절 #2 · #3). 전체 탭은 검수
     * 지연을 상단에 고정한다(페이지를 넘어서도 고정되도록 서버 정렬).
     *
     * <p>소비자 상세에서 넘어온 진입(56 C1) — {@code userId}로 주문한 소비자만 남기고, {@code openOnly}면 종결(COMPLETED)을 뺀다.
     * {@code userId}가 있고 기간을 둘 다 비웠으면 기본 1개월을 걸지 않는다 — 한 소비자의 클레임은 많지 않고, 「반품·교환」 칩
     * 건수(기간 없음)와 목록 건수가 같아야 한다.
     */
    public PageResponse<SellerClaimListItem> searchClaims(Long marketId, ClaimTab tab, Set<ClaimType> types,
                                                          ClaimReason reason, LocalDate from, LocalDate to,
                                                          String keyword, String marketName, String consumerName,
                                                          showroomz.domain.order.type.ClaimSort sort, Long userId,
                                                          boolean openOnly, PagingRequest pagingRequest) {
        LocalDateTime now = LocalDateTime.now();
        boolean unbounded = userId != null && from == null && to == null;
        SellerClaimSearchCondition base = buildCondition(marketId, tab, types, reason, from, to, keyword, now);
        boolean admin = marketId == null;
        SellerClaimSearchCondition condition = new SellerClaimSearchCondition(base.marketId(), base.tab(), base.types(),
                base.reason(), unbounded ? null : base.from(), unbounded ? null : base.to(), base.claimId(),
                base.orderNumber(), admin ? marketName : null, admin ? consumerName : null, sort,
                admin && base.tab() == ClaimTab.ALL, now, userId, openOnly);
        int size = pagingRequest.getSize();
        if (size < 1 || size > orderProperties.getListPageSizeMax()) {
            throw new BusinessException(ErrorCode.ORDER_PAGE_SIZE_INVALID);
        }
        Pageable pageable = PageRequest.of(Math.max(pagingRequest.getPage() - 1, 0), size);

        Page<OrderClaim> page = claimRepository.searchForSeller(condition, pageable);
        return PageResponse.of(new PageImpl<>(assembleRows(page.getContent(), now), pageable, page.getTotalElements()));
    }

    /** KPI · 탭 카운트 · 유형 카운트 — 검색 조건·기간과 무관한 전체 기준이다. 결제 대기(접수 전)는 세지 않는다. */
    public SellerClaimSummaryResponse getSummary(String sellerEmail) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        return summarize(scope.market().getId());
    }

    /** 요약 — {@code marketId}가 null 이면 전 브랜드(어드민 06b). 탭 · 건수가 파트너 11 과 같은 기준이다. */
    public SellerClaimSummaryResponse summarize(Long marketId) {

        Map<ClaimStatus, Long> byStatus = new EnumMap<>(ClaimStatus.class);
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        for (ClaimType type : ClaimType.values()) {
            typeCounts.put(type.name(), 0L);
        }
        for (Object[] row : claimRepository.countByStatusAndType(marketId)) {
            ClaimStatus status = (ClaimStatus) row[0];
            if (status == ClaimStatus.PAYMENT_PENDING) {
                continue;
            }
            long count = (Long) row[2];
            byStatus.merge(status, count, Long::sum);
            typeCounts.merge(((ClaimType) row[1]).name(), count, Long::sum);
        }
        Map<String, Long> tabCounts = new LinkedHashMap<>();
        for (ClaimTab tab : ClaimTab.values()) {
            tabCounts.put(tab.name(),
                    tab.getStatuses().stream().mapToLong(status -> byStatus.getOrDefault(status, 0L)).sum());
        }
        return new SellerClaimSummaryResponse(
                new SellerClaimSummaryResponse.Kpi(tabCounts.get(ClaimTab.COLLECT_WAIT.name()),
                        tabCounts.get(ClaimTab.INSPECTION.name()), tabCounts.get(ClaimTab.RESHIP.name()),
                        claimRepository.countOverdue(marketId, LocalDateTime.now())),
                tabCounts, typeCounts);
    }

    /** 검수 기한 경과만 — 파트너 홈 「입고·검수 지연」(1017 설계서 4-1). KPI {@code overdue} 는 회수 대기 방치를 합산한다. */
    public long countInspectOverdue(Long marketId) {
        return claimRepository.countInspectOverdueByMarket(marketId, LocalDateTime.now());
    }

    public SellerClaimDetailResponse getClaim(String sellerEmail, Long claimId) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        OrderClaim claim = claimRepository.findOwned(claimId, scope.market().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        return detail(claim);
    }

    /** 상세 — 브랜드 범위 없이(어드민 06b). 결제 대기(접수 전)는 없는 것으로 본다. */
    public SellerClaimDetailResponse getClaimForAdmin(Long claimId) {
        OrderClaim claim = claimRepository.findById(claimId)
                .filter(found -> found.getStatus() != ClaimStatus.PAYMENT_PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        return detail(claim);
    }

    private SellerClaimDetailResponse detail(OrderClaim claim) {
        Long claimId = claim.getId();
        LocalDateTime now = LocalDateTime.now();
        OrderClaimCollection collection = claim.getCollection();
        OrderDeliveryGroup group = claim.getDeliveryGroup();
        List<OrderClaim> boxClaims = claimRepository.findByCollectionId(collection.getId());
        List<OrderProduct> groupItems = orderProductRepository.findByDeliveryGroupIds(List.of(group.getId()));
        List<OrderClaimAttachment> attachments = attachmentRepository.findByClaimIds(List.of(claimId));
        List<String> consumerPhotos = urls(attachments, ClaimAttachmentOwner.CONSUMER);
        List<String> sellerPhotos = urls(attachments, ClaimAttachmentOwner.SELLER);
        List<OrderClaimCharge> charges = chargeRepository.findByCollectionId(collection.getId());
        ClaimStatus status = claim.getStatus();
        Boolean reshipFeeCharged = claim.getType() != ClaimType.EXCHANGE ? null
                : charges.stream().anyMatch(charge -> charge.getType() == ClaimChargeType.EXCHANGE_RESHIP
                && charge.getStatus() == ClaimChargeStatus.PAID);

        return new SellerClaimDetailResponse(
                assembler.toListItem(claim, boxClaims, groupItems, consumerPhotos.size(), sellerPhotos.size(), charges,
                        now),
                group.getOrder().getOrderNumber(),
                group.getId(),
                OrderAddressMasker.maskPhone(group.getOrder().getRecipientPhone()),
                claim.getReasonDetail(),
                reshipFeeCharged,
                refund(claim, collection, boxClaims),
                consumerPhotos,
                sellerPhotos,
                claim.getRejectDetail(),
                claim.getRejectedAt() == null ? null : new SellerClaimDetailResponse.Rejection(
                        claim.getRejectLegalBasis(),
                        claim.getRejectLegalBasis() == null ? null : claim.getRejectLegalBasis().getLabel(),
                        claim.getRejectConsumerMessage(),
                        claim.isFaultChangedToSeller(),
                        claim.getSplitFromClaimId() == null ? null : "CLM-" + claim.getSplitFromClaimId()),
                purchaseConfirm(group, now),
                result(claim, group),
                status != ClaimStatus.REJECT_HOLD ? List.of() : noticeRepository.findByClaimId(claimId).stream()
                        .map(notice -> new SellerClaimDetailResponse.Notice(notice.getSeq(), notice.getNotifiedAt(),
                                notice.getChannel())).toList(),
                historyRepository.findByClaimId(claimId).stream()
                        .map(h -> new SellerClaimDetailResponse.HistoryItem(h.getEventType().name(),
                                h.getEventType().getLabel(), h.getActorType().name(), h.getActorType().getLabel(),
                                h.getDetail(), h.getOccurredAt())).toList(),
                new SellerClaimDetailResponse.Actions(assembler.canConfirmReceipt(status),
                        status == ClaimStatus.RECEIVED, status == ClaimStatus.RECEIVED,
                        status == ClaimStatus.RESHIP_READY, status == ClaimStatus.RESHIPPING,
                        status == ClaimStatus.RECEIVED
                                && collection.getFeeBearer() == showroomz.domain.order.type.ClaimFeeBearer.SELLER),
                reshipFeeCharged,
                inspection(collection, charges),
                PREVIEW_STATUSES.contains(status) ? freightPreview(group) : null);
    }

    /** 운임 미리보기를 내리는 단계 — 판정 전. */
    private static final Set<ClaimStatus> PREVIEW_STATUSES = EnumSet.of(ClaimStatus.REQUESTED, ClaimStatus.COLLECTING,
            ClaimStatus.ARRIVED, ClaimStatus.RECEIVED);

    /** 판정 전 운임 미리보기(58 계획 5-4) — 판정이 쓰는 정액(기본 배송비 · 재발송 배송비)과 PG 환급 한도. */
    private SellerClaimDetailResponse.FreightPreview freightPreview(OrderDeliveryGroup group) {
        long[] payment = fulfillmentService.paymentRefundable(group);
        return new SellerClaimDetailResponse.FreightPreview(feePolicy.freightFlat(group),
                group.getRegionSurcharge() == null ? 0 : group.getRegionSurcharge(), feePolicy.reshipFee(group),
                payment == null ? null : payment[0], payment == null ? null : payment[1]);
    }

    /** 검수 판정 · 운임 정산(1021 설계서 4-3 · 4-4) — 판정 기록도 운임 정산 행도 없으면 null. */
    private SellerClaimDetailResponse.Inspection inspection(OrderClaimCollection collection, List<OrderClaimCharge> charges) {
        List<SellerClaimDetailResponse.FreightRow> rows = freightAdjustmentRepository
                .findByClaimCollectionIdOrderByIdAsc(collection.getId()).stream()
                .map(row -> new SellerClaimDetailResponse.FreightRow(row.getTrigger(), row.getTrigger().getLabel(),
                        row.getDirection(), row.getCollectFreight(), row.getPrepaidFreight(), row.getInitialFeeRestored(),
                        row.signedAmount(), row.getSettledVia()))
                .toList();
        if (collection.getInspectionResult() == null && rows.isEmpty() && !collection.isAutoReceived()) {
            return null;
        }
        Integer overturnFee = charges.stream().filter(charge -> charge.getType() == ClaimChargeType.OVERTURN_RESHIP)
                .reduce((first, second) -> second).map(OrderClaimCharge::getAmount).orElse(null);
        return new SellerClaimDetailResponse.Inspection(collection.getInspectionResult(),
                collection.getInspectionResult() == null ? null : collection.getInspectionResult().getLabel(),
                collection.getInspectionResult() == showroomz.domain.order.type.ClaimInspectionResult.REASON_OVERTURNED
                        ? collection.getInspectionNote() : null,
                collection.isAutoReceived(), overturnFee, rows);
    }

    // ------------------------------------------------------------------ 내부

    /** 구매확정 타이머(1009 기획 수정본 4-2) — 정지 중이면 정지 시점 기준으로 멈춘 남은 일수. */
    private SellerClaimDetailResponse.PurchaseConfirm purchaseConfirm(OrderDeliveryGroup group, LocalDateTime now) {
        if (group.confirmBaseAt() == null) {
            return null;
        }
        int days = orderProperties.getPurchaseConfirmDays();
        boolean paused = group.getConfirmPausedAt() != null;
        return new SellerClaimDetailResponse.PurchaseConfirm(paused, group.confirmRemainingDays(days, now),
                paused ? null : group.confirmBaseAt().plusDays(days));
    }

    private SellerClaimSearchCondition buildCondition(Long marketId, ClaimTab tab, Set<ClaimType> types,
                                                      ClaimReason reason, LocalDate from, LocalDate to,
                                                      String keyword, LocalDateTime now) {
        LocalDate resolvedTo = to != null ? to : KstDates.today(now);
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_PERIOD_DAYS);
        // 역전된 기간은 빈 목록이 아니라 입력 오류다 — 주문 관리와 같은 규칙.
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new BusinessException(ErrorCode.ORDER_SEARCH_RANGE_INVALID);
        }
        if (ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) > orderProperties.getSearchRangeMaxDays()) {
            throw new BusinessException(ErrorCode.ORDER_SEARCH_RANGE_EXCEEDED);
        }
        Long claimId = null;
        String orderNumber = null;
        if (keyword != null && !keyword.isBlank()) {
            Matcher matcher = CLAIM_NUMBER.matcher(keyword.trim());
            if (matcher.matches()) {
                claimId = parseId(matcher.group(1));
            } else {
                orderNumber = keyword.trim();
            }
        }
        return new SellerClaimSearchCondition(marketId, tab == null ? ClaimTab.COLLECT_WAIT : tab, types, reason,
                KstDates.startOfDay(resolvedFrom), KstDates.at(resolvedTo, LocalTime.MAX), claimId, orderNumber);
    }

    /** 자릿수가 넘치는 접수번호는 없는 번호다 — 0 건이 되도록 존재할 수 없는 id 로 바꾼다. */
    private static Long parseId(String digits) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private List<SellerClaimListItem> assembleRows(List<OrderClaim> claims, LocalDateTime now) {
        if (claims.isEmpty()) {
            return List.of();
        }
        List<Long> claimIds = claims.stream().map(OrderClaim::getId).toList();
        Set<Long> collectionIds = claims.stream().map(c -> c.getCollection().getId()).collect(Collectors.toSet());
        Set<Long> groupIds = claims.stream().map(c -> c.getDeliveryGroup().getId()).collect(Collectors.toSet());

        Map<Long, List<OrderClaim>> boxes = claimRepository.findByCollectionIds(collectionIds).stream()
                .collect(Collectors.groupingBy(c -> c.getCollection().getId()));
        Map<Long, List<OrderProduct>> itemsByGroup = orderProductRepository.findByDeliveryGroupIds(groupIds).stream()
                .collect(Collectors.groupingBy(item -> item.getDeliveryGroup().getId()));
        Map<Long, Map<ClaimAttachmentOwner, Integer>> photos = new HashMap<>();
        for (Object[] row : attachmentRepository.countByClaimIds(claimIds)) {
            photos.computeIfAbsent((Long) row[0], id -> new EnumMap<>(ClaimAttachmentOwner.class))
                    .put((ClaimAttachmentOwner) row[1], ((Long) row[2]).intValue());
        }
        Map<Long, List<OrderClaimCharge>> chargesByCollection = chargeRepository.findByCollectionIds(collectionIds)
                .stream()
                .collect(Collectors.groupingBy(charge -> charge.getCollection().getId()));
        return claims.stream().map(claim -> {
            Map<ClaimAttachmentOwner, Integer> counts = photos.getOrDefault(claim.getId(), Map.of());
            return assembler.toListItem(claim,
                    boxes.getOrDefault(claim.getCollection().getId(), List.of(claim)),
                    itemsByGroup.getOrDefault(claim.getDeliveryGroup().getId(), List.of()),
                    counts.getOrDefault(ClaimAttachmentOwner.CONSUMER, 0),
                    counts.getOrDefault(ClaimAttachmentOwner.SELLER, 0),
                    chargesByCollection.getOrDefault(claim.getCollection().getId(), List.of()), now);
        }).toList();
    }

    /**
     * 환불 예정액은 요청(박스) 단위다 — 배송비 차감이 요청당 한 번이라, 항목의 상품 금액과 요청의 차감·예정액을 따로 내린다.
     * 요청 취소로 사라진 항목과 거절된 항목은 예정액에 넣지 않는다. 판정이 다 끝났으면 확정액을 쓴다.
     */
    private SellerClaimDetailResponse.Refund refund(OrderClaim claim, OrderClaimCollection collection,
                                                    List<OrderClaim> boxClaims) {
        if (claim.getType() != ClaimType.RETURN) {
            return null;
        }
        long itemAmount = (long) claim.getOrderProduct().getPrice() * claim.getQuantity();
        long expected;
        if (collection.getRefundAmount() != null) {
            expected = collection.getRefundAmount();
        } else {
            long goods = boxClaims.stream()
                    .filter(c -> c.getRejectedAt() == null && c.getResult() != ClaimResult.CANCELLED)
                    .mapToLong(c -> (long) c.getOrderProduct().getPrice() * c.getQuantity())
                    .sum();
            expected = Math.max(0, goods - collection.getReturnDeduction());
        }
        return new SellerClaimDetailResponse.Refund(itemAmount, collection.getReturnDeduction(), expected,
                assembler.refundBasisLabel(collection));
    }

    private SellerClaimDetailResponse.Result result(OrderClaim claim, OrderDeliveryGroup group) {
        if (claim.getStatus() != ClaimStatus.COMPLETED) {
            return null;
        }
        ClaimResult result = claim.getResult();
        return new SellerClaimDetailResponse.Result(
                claim.getReshipDeliveredAt(),
                result == ClaimResult.EXCHANGED && group.getConfirmRestartAt() != null
                        ? group.getConfirmRestartAt().plusDays(orderProperties.getPurchaseConfirmDays()) : null,
                result != ClaimResult.REJECTED ? null : claim.getDisposedAt() != null ? "DISPOSED" : "RETURNED",
                claim.getDisposedAt());
    }

    private static List<String> urls(List<OrderClaimAttachment> attachments, ClaimAttachmentOwner owner) {
        return attachments.stream().filter(a -> a.getOwner() == owner).map(OrderClaimAttachment::getImageUrl).toList();
    }
}
