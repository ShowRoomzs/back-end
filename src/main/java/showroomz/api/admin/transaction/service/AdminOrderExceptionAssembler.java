package showroomz.api.admin.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.admin.transaction.dto.AdminOrderExceptionDto;
import showroomz.api.admin.transaction.dto.AdminOrderExceptionDto.ExceptionKind;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimCollection;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.utils.BusinessCalendar;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 어드민 예외 관리(06d) 행 조립 — 유형 7종 × 열 4개(기한 · 경과 · 다음 단계 · 처리 주체)의 <b>문장을 서버가 만든다</b>(40 설계서 0-6).
 * 숫자(정렬 · 색)와 문장(표시)을 둘 다 내린다. 「연락 두절」이라는 말은 쓰지 않는다 — 상태가 아니라 조건이다.
 */
@Component
@RequiredArgsConstructor
public class AdminOrderExceptionAssembler {

    /** 자동 알림 배치 회차({@code OrderOverdueNoticeScheduler} — 영업일 10 · 15시 · 같은 영업일 1회). */
    static final List<LocalTime> NOTICE_SLOTS = List.of(LocalTime.of(10, 0), LocalTime.of(15, 0));
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM.dd");

    private final BusinessCalendar calendar;
    private final ActOnBehalfPolicy actOnBehalfPolicy;
    private final OrderProperties orderProperties;
    private final DeliveryTrackerProperties trackerProperties;
    private final StalledPolicy stalledPolicy;

    /**
     * 원천 한 건 — 하위주문 또는 클레임. 조회 · 건수는 이 값으로 하고 문장은 목록에서만 만든다(요약은 문장을 건너뛴다).
     *
     * @param dueAt   처리 지연의 기한(배송 예외는 null)
     * @param basisAt 정렬 기준 — 처리 지연은 기한, 배송 예외는 기준 시각
     */
    record Row(ExceptionKind kind, OrderDeliveryGroup group, OrderClaim claim, LocalDateTime dueAt,
               LocalDateTime basisAt, boolean actOnBehalf) {

        String targetNumber() {
            return kind == ExceptionKind.INSPECT_OVERDUE || kind == ExceptionKind.RESHIP_DELAYED
                    ? claim.claimNumber() : group.getOrder().getOrderNumber();
        }
    }

    AdminOrderExceptionDto.ExceptionItem item(Row row, LocalDateTime now) {
        OrderDeliveryGroup group = row.group();
        OrderClaim claim = row.claim();
        ExceptionKind kind = row.kind();
        boolean delay = kind.tab() == AdminOrderExceptionDto.ExceptionTab.DELAY;
        AdminOrderExceptionDto.Link link = new AdminOrderExceptionDto.Link(
                claim == null ? AdminOrderExceptionDto.LinkType.ORDER : AdminOrderExceptionDto.LinkType.CLAIM,
                group.getOrder().getId(), group.getId(), claim == null ? null : claim.getId());
        long elapsedHours = hours(row.basisAt(), now);

        if (delay) {
            int businessDays = calendar.businessDaysBetween(row.dueAt().toLocalDate(), now.toLocalDate());
            Integer noticeCount = switch (kind) {
                case SHIP_OVERDUE -> group.getOverdueNoticeCount();
                case INSPECT_OVERDUE -> claim.getInspectNoticeCount();
                default -> null;
            };
            LocalDateTime lastNoticeAt = switch (kind) {
                case SHIP_OVERDUE -> group.getLastOverdueNoticeAt();
                case INSPECT_OVERDUE -> claim.getLastInspectNoticeAt();
                default -> null;
            };
            LocalDateTime nextNoticeAt = noticeCount == null || actOnBehalfPolicy.thresholdReached(noticeCount) ? null
                    : nextNoticeAt(lastNoticeAt, now);
            return new AdminOrderExceptionDto.ExceptionItem(kind.tab(), kind, kind.getLabel(), row.targetNumber(),
                    group.getSubOrderNumber(), group.getOrder().getId(), group.getId(), claim == null ? null : claim.getId(),
                    group.getMarketId(), group.getMarketName(), row.dueAt(), dueBasisLabel(row), row.basisAt(), null,
                    elapsedHours, businessDays, businessDays + "영업일", true, noticeCount, lastNoticeAt, nextNoticeAt,
                    row.actOnBehalf(), nextStepLabel(kind, noticeCount, nextNoticeAt), nextStepNote(kind, noticeCount),
                    null, null, link);
        }

        AdminOrderExceptionDto.Invoice invoice;
        if (kind == ExceptionKind.COLLECTION_UNSCANNED) {
            OrderClaimCollection collection = claim.getCollection();
            invoice = new AdminOrderExceptionDto.Invoice(collection.getCarrier(),
                    collection.getCarrier() == null ? null : collection.getCarrier().getLabel(),
                    collection.getTrackingNumber());
        } else {
            invoice = new AdminOrderExceptionDto.Invoice(group.getCarrier(),
                    group.getCarrier() == null ? null : group.getCarrier().getLabel(), group.getTrackingNumber());
        }
        long days = elapsedHours / 24;
        String basisLabel = switch (kind) {
            case PICKUP_UNCONFIRMED -> "등록 후 " + trackerProperties.getPickupAlertHours() + "시간";
            case TRACKING_STALLED -> "집화 후 " + trackerProperties.getStallAlertDays() + "일";
            case RETURNING -> "반송 감지";
            default -> "등록 후 " + orderProperties.getException().getCollectionUnscannedHours() + "시간";
        };
        String elapsedLabel = switch (kind) {
            case TRACKING_STALLED -> days + "일 무갱신";
            case RETURNING -> "감지 후 " + days + "일";
            default -> elapsedHours + "시간";
        };
        String handlerLabel = switch (kind) {
            case PICKUP_UNCONFIRMED -> "브랜드 확인 · 시스템 알림";
            case TRACKING_STALLED -> row.actOnBehalf()
                    ? "소비자·브랜드가 택배사 조회 · " + stalledPolicy.days() + "일 경과 — 운영자 판정(분실 · 배송완료) 주문 상세"
                    : "소비자·브랜드가 택배사 조회";
            case RETURNING -> "완료 감지 시 PG 자동 환불";
            default -> "소비자가 회수 송장을 확인 · 수정";
        };
        return new AdminOrderExceptionDto.ExceptionItem(kind.tab(), kind, kind.getLabel(), row.targetNumber(),
                group.getSubOrderNumber(), group.getOrder().getId(), group.getId(), claim == null ? null : claim.getId(),
                group.getMarketId(), group.getMarketName(), null, null, row.basisAt(), basisLabel, elapsedHours, null,
                elapsedLabel, true, null, null, null, row.actOnBehalf(), null, null, invoice, handlerLabel, link);
    }

    /** 기한 보조 문장 — 기한의 출처(40 설계서 1-1). */
    private String dueBasisLabel(Row row) {
        return switch (row.kind()) {
            case SHIP_OVERDUE -> {
                Integer n = row.group().getShipDueBusinessDays();
                yield "공구 마감 + " + (n == null ? "?" : n) + "영업일(주문 시점 값)";
            }
            case INSPECT_OVERDUE -> "입고 + " + orderProperties.getClaim().getInspectDueBusinessDays() + "영업일";
            case RESHIP_DELAYED -> (row.claim().getRejectedAt() != null ? "재발송비 결제" : "검수 통과") + " + "
                    + orderProperties.getException().getReshipDueBusinessDays() + "영업일";
            default -> null;
        };
    }

    /** 다음 단계 문장(40 설계서 2-1-1). */
    private String nextStepLabel(ExceptionKind kind, Integer noticeCount, LocalDateTime nextNoticeAt) {
        if (kind == ExceptionKind.RESHIP_DELAYED) {
            return "브랜드가 재발송 송장을 등록해야 합니다";
        }
        int threshold = actOnBehalfPolicy.threshold();
        if (noticeCount != null && noticeCount >= threshold) {
            return kind == ExceptionKind.SHIP_OVERDUE
                    ? "자동 알림 " + threshold + "회 무응답 · 대행 가능"
                    : "자동 알림 " + threshold + "회 무응답 · 운영자 환불 가능";
        }
        int next = (noticeCount == null ? 0 : noticeCount) + 1;
        return "자동 알림 대기 · " + next + "회차" + (nextNoticeAt == null ? "" : " " + nextNoticeAt.format(MONTH_DAY));
    }

    private String nextStepNote(ExceptionKind kind, Integer noticeCount) {
        if (kind == ExceptionKind.RESHIP_DELAYED) {
            return "자동 알림 없음 — 근거 대기";
        }
        if (noticeCount == null || !actOnBehalfPolicy.thresholdReached(noticeCount)) {
            return null;
        }
        return kind == ExceptionKind.SHIP_OVERDUE ? "송장 대행 · 직권 취소 — 주문 상세"
                : "운영자 사유 환불 편입 — 클레임 상세(검수는 대신하지 않는다)";
    }

    /**
     * 다음 자동 알림 회차(40 설계서 3-2) — 오늘 아직 알리지 않았고 오늘이 영업일이면 지금 이후 가장 이른 회차(10 · 15시), 아니면 다음
     * 영업일 10시. 저장하지 않는다 — 배치가 돌면 다음 폴링에서 따라온다.
     */
    LocalDateTime nextNoticeAt(LocalDateTime lastNoticeAt, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        boolean notifiedToday = lastNoticeAt != null && !lastNoticeAt.toLocalDate().isBefore(today);
        if (!notifiedToday && calendar.isBusinessDay(today)) {
            for (LocalTime slot : NOTICE_SLOTS) {
                LocalDateTime at = today.atTime(slot);
                if (at.isAfter(now)) {
                    return at;
                }
            }
        }
        return calendar.addBusinessDays(today, 1).atTime(NOTICE_SLOTS.get(0));
    }

    private static long hours(LocalDateTime basisAt, LocalDateTime now) {
        return basisAt == null ? 0 : Math.max(0, Duration.between(basisAt, now).toHours());
    }
}
