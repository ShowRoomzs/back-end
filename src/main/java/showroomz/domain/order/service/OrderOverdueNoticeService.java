package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.order.event.OrderOverdueNoticeEvent;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 처리 지연 자동 알림(1009 기획 수정본 8-4) — 발송 기한 경과 · 검수 기한 경과 건에 <b>영업일마다 한 번</b> 자동 알림을 센다.
 * 운영자 독촉 버튼은 없다(대표 확정). 알림 횟수가 쌓이면(3회) 어드민 대행이 열린다 — 판정은 조회 쪽이 한다.
 */
@Service
@RequiredArgsConstructor
public class OrderOverdueNoticeService {

    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderClaimRepository claimRepository;
    private final BusinessCalendar businessCalendar;
    private final ApplicationEventPublisher eventPublisher;

    /** 오늘이 영업일이 아니면 아무것도 하지 않는다 — 주말 · 공휴일에는 알림 회차가 쌓이지 않는다. */
    public boolean isNoticeDay(LocalDateTime now) {
        return businessCalendar.isBusinessDay(now.toLocalDate());
    }

    @Transactional(readOnly = true)
    public List<Long> findShipOverdue(LocalDateTime now, int limit) {
        return deliveryGroupRepository.findShipOverdueToNotify(now, now.toLocalDate().atStartOfDay(),
                PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<Long> findInspectOverdue(LocalDateTime now, int limit) {
        return claimRepository.findInspectOverdueToNotify(now, now.toLocalDate().atStartOfDay(), PageRequest.of(0, limit));
    }

    @Transactional
    public boolean noticeShipOverdue(Long deliveryGroupId, LocalDateTime now) {
        if (deliveryGroupRepository.recordOverdueNotice(deliveryGroupId, now, now.toLocalDate().atStartOfDay()) != 1) {
            return false;
        }
        deliveryGroupRepository.findById(deliveryGroupId).ifPresent(group -> eventPublisher.publishEvent(
                new OrderOverdueNoticeEvent(OrderOverdueNoticeEvent.Kind.SHIP_OVERDUE, deliveryGroupId,
                        group.getMarketId(), group.getOverdueNoticeCount(), now)));
        return true;
    }

    @Transactional
    public boolean noticeInspectOverdue(Long claimId, LocalDateTime now) {
        if (claimRepository.recordInspectNotice(claimId, now, now.toLocalDate().atStartOfDay()) != 1) {
            return false;
        }
        claimRepository.findById(claimId).ifPresent(claim -> eventPublisher.publishEvent(
                new OrderOverdueNoticeEvent(OrderOverdueNoticeEvent.Kind.INSPECT_OVERDUE, claimId, claim.getMarketId(),
                        claim.getInspectNoticeCount(), now)));
        return true;
    }
}
