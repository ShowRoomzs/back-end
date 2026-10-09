package showroomz.api.admin.transaction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.transaction.dto.AdminOrderExceptionDto;
import showroomz.api.admin.transaction.dto.AdminOrderExceptionDto.ExceptionKind;
import showroomz.api.admin.transaction.dto.AdminOrderExceptionDto.ExceptionTab;
import showroomz.api.admin.transaction.service.AdminOrderExceptionAssembler.Row;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.service.BusinessDayCalculator;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.TrackingAlert;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 어드민 예외 관리(06d · 40 설계서) — 처리 지연(당사자 기한 경과)과 배송 예외(택배 · 추적 이상)의 모니터. <b>쓰기가 없다</b> — [열기]로
 * 06a · 06b 상세에 가서 근거를 읽고 조치한다. 독촉 버튼도 없다 — 기한 경과 알림은 시스템 자동이고 횟수만 보여 준다. 취소 요청 미응답은
 * 1영업일 자동 승인이라 처리 지연에 오지 않는다.
 *
 * <p>유형 7종이 두 엔티티 · 다섯 조회에서 나오고 재발송 지연은 영업일 판정이라 SQL 로 못 간다 — <b>메모리 합집합</b> 뒤 필터 · 검색 ·
 * 정렬 · 페이지를 여기서 한다(40 설계서 0-3). 유형별 조회 상한({@code order.exception.fetch-limit})에 닿으면 경고 로그를 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOrderExceptionService {

    private static final String CLAIM_PREFIX = "CLM-";

    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderClaimRepository claimRepository;
    private final BusinessDayCalculator businessDayCalculator;
    private final ActOnBehalfPolicy actOnBehalfPolicy;
    private final AdminOrderExceptionAssembler assembler;
    private final OrderProperties orderProperties;
    private final DeliveryTrackerProperties trackerProperties;

    public AdminOrderExceptionDto.ExceptionPage getExceptions(ExceptionTab tab, ExceptionKind kind, String keyword,
                                                              PagingRequest paging) {
        int size = paging.getSize();
        if (size < 1 || size > orderProperties.getListPageSizeMax()) {
            throw new BusinessException(ErrorCode.ORDER_PAGE_SIZE_INVALID);
        }
        ExceptionTab resolved = tab == null ? ExceptionTab.DELAY : tab;
        if (kind != null && kind.tab() != resolved) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "그 유형은 이 탭에 없습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        String text = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<Row> rows = (resolved == ExceptionTab.DELAY ? delays(now) : deliveryExceptions(now)).stream()
                .filter(row -> kind == null || row.kind() == kind)
                .filter(row -> text == null || matches(row, text))
                // 경과 오래된 순 고정 — 가장 오래 방치된 것이 위(시안 셀렉트의 선택지가 하나뿐이다 · §40-5 #6).
                .sorted(Comparator.comparing(Row::basisAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Row::targetNumber))
                .toList();
        Pageable pageable = PageRequest.of(Math.max(paging.getPage() - 1, 0), size);
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + size, rows.size());
        List<AdminOrderExceptionDto.ExceptionItem> items = rows.subList(from, to).stream()
                .map(row -> assembler.item(row, now)).toList();
        return new AdminOrderExceptionDto.ExceptionPage(now,
                PageResponse.of(new PageImpl<>(items, pageable, rows.size())));
    }

    /** 탭 · 유형 건수 · 대행 가능 · 배지 — 폴링 대상이라 문장 조립을 건너뛴다(40 설계서 7-3 #2). */
    public AdminOrderExceptionDto.ExceptionSummary getSummary() {
        LocalDateTime now = LocalDateTime.now();
        List<Row> delays = delays(now);
        List<Row> deliveries = deliveryExceptions(now);
        Map<String, Long> kindCounts = new LinkedHashMap<>();
        for (ExceptionKind kind : ExceptionKind.values()) {
            kindCounts.put(kind.name(), 0L);
        }
        delays.forEach(row -> kindCounts.merge(row.kind().name(), 1L, Long::sum));
        deliveries.forEach(row -> kindCounts.merge(row.kind().name(), 1L, Long::sum));
        Map<String, Long> tabCounts = new LinkedHashMap<>();
        tabCounts.put(ExceptionTab.DELAY.name(), (long) delays.size());
        tabCounts.put(ExceptionTab.DELIVERY.name(), (long) deliveries.size());
        String scope = "DELAY".equalsIgnoreCase(orderProperties.getException().getBadgeScope()) ? "DELAY" : "ALL";
        long badge = "DELAY".equals(scope) ? delays.size() : delays.size() + deliveries.size();
        return new AdminOrderExceptionDto.ExceptionSummary(now, tabCounts, kindCounts,
                delays.stream().filter(Row::actOnBehalf).count(), badge, scope);
    }

    // ------------------------------------------------------------------ 처리 지연(40 설계서 1-1)

    private List<Row> delays(LocalDateTime now) {
        Pageable cap = cap();
        List<Row> rows = new ArrayList<>();
        List<OrderDeliveryGroup> shipOverdue = deliveryGroupRepository.findShipOverdueForAdmin(now, cap);
        warnIfCapped(ExceptionKind.SHIP_OVERDUE, shipOverdue.size());
        for (OrderDeliveryGroup group : shipOverdue) {
            rows.add(new Row(ExceptionKind.SHIP_OVERDUE, group, null, group.getShipDueAt(), group.getShipDueAt(),
                    actOnBehalfPolicy.canActOnBehalf(group, now)));
        }
        List<OrderClaim> inspectOverdue = claimRepository.findInspectOverdue(now, cap);
        warnIfCapped(ExceptionKind.INSPECT_OVERDUE, inspectOverdue.size());
        for (OrderClaim claim : inspectOverdue) {
            // 검수 지연의 대행은 조건만 보인다 — 운영자는 검수를 대신하지 않는다(40 설계서 0-8).
            rows.add(new Row(ExceptionKind.INSPECT_OVERDUE, claim.getDeliveryGroup(), claim, claim.getInspectDueAt(),
                    claim.getInspectDueAt(), actOnBehalfPolicy.thresholdReached(claim.getInspectNoticeCount())));
        }
        int reshipDays = orderProperties.getException().getReshipDueBusinessDays();
        List<OrderClaim> reshipReady = claimRepository.findReshipReadyBefore(now.minusDays(reshipDays), cap);
        warnIfCapped(ExceptionKind.RESHIP_DELAYED, reshipReady.size());
        for (OrderClaim claim : reshipReady) {
            LocalDateTime due = businessDayCalculator.dueAt(claim.getStageEnteredAt(), reshipDays);
            if (due.isBefore(now)) {
                rows.add(new Row(ExceptionKind.RESHIP_DELAYED, claim.getDeliveryGroup(), claim, due, due, false));
            }
        }
        return rows;
    }

    // ------------------------------------------------------------------ 배송 예외(플랫폼 미개입 · 40 설계서 1-2)

    private List<Row> deliveryExceptions(LocalDateTime now) {
        Pageable cap = cap();
        List<Row> rows = new ArrayList<>();
        List<OrderDeliveryGroup> groups = deliveryGroupRepository.findDeliveryExceptionsForAdmin(cap);
        warnIfCapped(ExceptionKind.TRACKING_STALLED, groups.size());
        for (OrderDeliveryGroup group : groups) {
            if (group.getFulfillmentStatus() == FulfillmentStatus.RETURNING) {
                rows.add(new Row(ExceptionKind.RETURNING, group, null, null, group.getReturnDetectedAt(), false));
            } else if (group.getTrackingAlert() == TrackingAlert.PICKUP_UNCONFIRMED) {
                rows.add(new Row(ExceptionKind.PICKUP_UNCONFIRMED, group, null, null, group.getShippedAt(), false));
            } else if (group.getTrackingAlert() == TrackingAlert.STALLED) {
                rows.add(new Row(ExceptionKind.TRACKING_STALLED, group, null, null,
                        group.getLastTrackingAt() != null ? group.getLastTrackingAt() : group.getShippedAt(), false));
            }
        }
        // 추적 스텁(Noop)이면 회수 송장은 한 번도 조회되지 않는다 — 전부 24시간 뒤 오경보가 되므로 유형을 비운다(40 설계서 7-2 #2).
        if (trackerProperties.isEnabled()) {
            int hours = orderProperties.getException().getCollectionUnscannedHours();
            List<OrderClaim> unscanned = claimRepository.findCollectionUnscanned(now.minusHours(hours), cap);
            warnIfCapped(ExceptionKind.COLLECTION_UNSCANNED, unscanned.size());
            for (OrderClaim claim : unscanned) {
                rows.add(new Row(ExceptionKind.COLLECTION_UNSCANNED, claim.getDeliveryGroup(), claim, null,
                        claim.getCollection().getInvoiceRegisteredAt(), false));
            }
        }
        return rows;
    }

    /** 검색 — 주문번호 · 하위주문번호 · 브랜드명 부분 일치, {@code CLM-N}은 접수번호 정확 일치(40 설계서 2-1). */
    private static boolean matches(Row row, String keyword) {
        if (keyword.regionMatches(true, 0, CLAIM_PREFIX, 0, CLAIM_PREFIX.length())) {
            return row.claim() != null && row.claim().claimNumber().equalsIgnoreCase(keyword);
        }
        String lower = keyword.toLowerCase(Locale.ROOT);
        OrderDeliveryGroup group = row.group();
        return contains(group.getOrder().getOrderNumber(), lower) || contains(group.getSubOrderNumber(), lower)
                || contains(group.getMarketName(), lower);
    }

    private static boolean contains(String value, String lowerKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private Pageable cap() {
        return PageRequest.of(0, Math.max(1, orderProperties.getException().getFetchLimit()));
    }

    private void warnIfCapped(ExceptionKind kind, int size) {
        if (size >= orderProperties.getException().getFetchLimit()) {
            log.warn("예외 관리 원천 조회 상한 도달 — 배치 장애 신호일 수 있다 - kind: {}, limit: {}", kind, size);
        }
    }
}
