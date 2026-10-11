package showroomz.api.admin.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.global.config.properties.OrderProperties;

import java.time.LocalDateTime;

/**
 * 운영자 대행 조건(37 설계서 0-4 · 40 설계서 0-4) — <b>상태가 아니라 조건</b>이라 저장하지 않는다. 06a 조회({@code actions}) ·
 * 06a 커맨드(대행 송장 · 직권 취소 가드) · 06d(처리 지연 「대행 가능」)가 이 한 곳을 부른다 — 같은 건이 화면마다 다르게 보이지
 * 않게.
 *
 * <p>해제는 조건이 사라질 때다. 브랜드의 「응답」은 송장 등록(→ 배송중) 또는 직권 취소(→ 취소)뿐이고, 준비 시작(신규 → 상품준비중)은
 * 응답이 아니다 — 기한은 여전히 지났고 알림은 계속 쌓인다. 알림 횟수는 되돌리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ActOnBehalfPolicy {

    private final OrderProperties orderProperties;

    /** 발송 기한 경과 — 아직 발송 전(신규 · 상품준비중)이고 기한이 지났다. 공구 진행 중(기한 없음)은 아니다. */
    public static boolean isShipOverdue(OrderDeliveryGroup group, LocalDateTime now) {
        return FulfillmentStatus.WORKABLE.contains(group.getFulfillmentStatus())
                && group.getShipDueAt() != null && group.getShipDueAt().isBefore(now);
    }

    /** 대행 가능 — 발송 기한 경과 ∧ 자동 알림 N회(기본 3 · 근거 대기) 무응답. */
    public boolean canActOnBehalf(OrderDeliveryGroup group, LocalDateTime now) {
        return isShipOverdue(group, now) && thresholdReached(group.getOverdueNoticeCount());
    }

    /** 자동 알림이 대행 조건 횟수에 닿았는가 — 검수 지연(조건만 표시)도 같은 횟수를 쓴다. */
    public boolean thresholdReached(int noticeCount) {
        return noticeCount >= threshold();
    }

    public int threshold() {
        return orderProperties.getActOnBehalfNoticeThreshold();
    }
}
