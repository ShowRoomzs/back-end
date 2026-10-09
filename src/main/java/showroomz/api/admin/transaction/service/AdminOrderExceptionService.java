package showroomz.api.admin.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.api.admin.transaction.dto.AdminTransactionDto.ExceptionKind;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.service.BusinessDayCalculator;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.TrackingAlert;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 어드민 예외 관리(06d · 1009 기획 수정본 8-4) — 처리 지연(당사자 기한 경과)과 배송 예외(택배 · 추적 이상)의 모니터. <b>실행 버튼이
 * 없다</b> — [열기]로 06a · 06b 상세에 가서 근거를 읽고 조치한다. 독촉 버튼도 없다 — 기한 경과 알림은 시스템 자동이고 횟수만 보여 준다.
 * 취소 요청 미응답은 1영업일 자동 승인이라 처리 지연에 오지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOrderExceptionService {

    /** 재발송 지연 기한 — 검수 통과 · 재발송비 결제 + N영업일(근거 대기 · 시안 2). */
    private static final int RESHIP_DUE_BUSINESS_DAYS = 2;
    /** 회수 송장 미조회 — 소비자 입력 송장도 집화 확인과 같은 24시간 기준. */
    private static final int COLLECTION_UNSCANNED_HOURS = 24;

    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderClaimRepository claimRepository;
    private final BusinessDayCalculator businessDayCalculator;
    private final ActOnBehalfPolicy actOnBehalfPolicy;

    public List<AdminTransactionDto.ExceptionItem> getExceptions(AdminTransactionDto.ExceptionTab tab) {
        LocalDateTime now = LocalDateTime.now();
        return (tab == AdminTransactionDto.ExceptionTab.DELIVERY ? deliveryExceptions(now) : delays(now)).stream()
                .sorted(Comparator.comparingLong(AdminTransactionDto.ExceptionItem::elapsedHours).reversed())
                .toList();
    }

    public AdminTransactionDto.ExceptionSummary getSummary() {
        LocalDateTime now = LocalDateTime.now();
        long delay = delays(now).size();
        long delivery = deliveryExceptions(now).size();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(AdminTransactionDto.ExceptionTab.DELAY.name(), delay);
        counts.put(AdminTransactionDto.ExceptionTab.DELIVERY.name(), delivery);
        return new AdminTransactionDto.ExceptionSummary(counts, delay + delivery);
    }

    // ------------------------------------------------------------------ 처리 지연

    private List<AdminTransactionDto.ExceptionItem> delays(LocalDateTime now) {
        List<AdminTransactionDto.ExceptionItem> rows = new ArrayList<>();
        for (OrderDeliveryGroup group : deliveryGroupRepository.findShipOverdueForAdmin(now)) {
            boolean actOnBehalf = actOnBehalfPolicy.canActOnBehalf(group, now);
            rows.add(groupRow(ExceptionKind.SHIP_OVERDUE, group, group.getShipDueAt(), now, group.getOverdueNoticeCount(),
                    actOnBehalf, actOnBehalf ? "브랜드 무응답 · 운영자 대행 가능(송장 대행 · 직권 취소)"
                            : "브랜드가 발송해야 합니다 · 자동 알림 중"));
        }
        for (OrderClaim claim : claimRepository.findInspectOverdue(now)) {
            rows.add(claimRow(ExceptionKind.INSPECT_OVERDUE, claim, claim.getInspectDueAt(), now,
                    claim.getInspectNoticeCount(), actOnBehalfPolicy.thresholdReached(claim.getInspectNoticeCount()),
                    "브랜드가 검수해야 합니다 · 자동 알림 중(운영자는 검수를 대신하지 않는다)"));
        }
        for (OrderClaim claim : claimRepository.findReshipReady()) {
            LocalDateTime due = businessDayCalculator.dueAt(claim.getStageEnteredAt(), RESHIP_DUE_BUSINESS_DAYS);
            if (due.isBefore(now)) {
                rows.add(claimRow(ExceptionKind.RESHIP_DELAYED, claim, due, now, null, false,
                        "브랜드가 재발송 송장을 등록해야 합니다"));
            }
        }
        return rows;
    }

    // ------------------------------------------------------------------ 배송 예외(플랫폼 미개입)

    private List<AdminTransactionDto.ExceptionItem> deliveryExceptions(LocalDateTime now) {
        List<AdminTransactionDto.ExceptionItem> rows = new ArrayList<>();
        for (OrderDeliveryGroup group : deliveryGroupRepository.findDeliveryExceptionsForAdmin()) {
            if (group.getFulfillmentStatus() == FulfillmentStatus.RETURNING) {
                rows.add(groupRow(ExceptionKind.RETURNING, group, group.getReturnDetectedAt(), now, null, false,
                        "반송 완료가 감지되면 PG 가 자동 환불합니다 · 이 주문으로 재발송하지 않습니다"));
            } else if (group.getTrackingAlert() == TrackingAlert.PICKUP_UNCONFIRMED) {
                rows.add(groupRow(ExceptionKind.PICKUP_UNCONFIRMED, group, group.getShippedAt(), now, null, false,
                        "브랜드가 택배사 접수 · 송장번호를 확인해야 합니다"));
            } else if (group.getTrackingAlert() == TrackingAlert.STALLED) {
                rows.add(groupRow(ExceptionKind.TRACKING_STALLED, group, group.getLastTrackingAt(), now, null, false,
                        "소비자 · 브랜드가 택배사에 직접 조회합니다 · 분실 보상은 택배사와 브랜드 사이의 일입니다"));
            }
        }
        for (OrderClaim claim : claimRepository.findCollectionUnscanned(now.minusHours(COLLECTION_UNSCANNED_HOURS))) {
            rows.add(claimRow(ExceptionKind.COLLECTION_UNSCANNED, claim, claim.getCollection().getInvoiceRegisteredAt(),
                    now, null, false, "소비자가 회수 송장을 확인 · 수정해야 합니다"));
        }
        return rows;
    }

    private static AdminTransactionDto.ExceptionItem groupRow(ExceptionKind kind, OrderDeliveryGroup group,
                                                              LocalDateTime basisAt, LocalDateTime now,
                                                              Integer noticeCount, boolean actOnBehalf, String handler) {
        return new AdminTransactionDto.ExceptionItem(kind, kind.getLabel(), group.getOrder().getId(),
                group.getOrder().getOrderNumber(), group.getId(), null, group.getMarketName(), basisAt,
                hours(basisAt, now), noticeCount, actOnBehalf, handler);
    }

    private static AdminTransactionDto.ExceptionItem claimRow(ExceptionKind kind, OrderClaim claim,
                                                              LocalDateTime basisAt, LocalDateTime now,
                                                              Integer noticeCount, boolean actOnBehalf, String handler) {
        OrderDeliveryGroup group = claim.getDeliveryGroup();
        return new AdminTransactionDto.ExceptionItem(kind, kind.getLabel(), group.getOrder().getId(),
                group.getOrder().getOrderNumber(), group.getId(), claim.getId(), group.getMarketName(), basisAt,
                hours(basisAt, now), noticeCount, actOnBehalf, handler);
    }

    private static long hours(LocalDateTime basisAt, LocalDateTime now) {
        return basisAt == null ? 0 : Math.max(0, Duration.between(basisAt, now).toHours());
    }
}
