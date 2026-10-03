package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 배송 이상 2단(§34-6) — 이행 상태와 별 축. 감시 배치가 쓰고 지운다.
 *
 * <p>2단의 이름에 「배송 지연」을 쓰지 않는다 — 발송기한(브랜드 의무)과 혼동된다.
 * 추적 정지는 택배사 데이터가 멈춘 상태다.
 */
@Getter
@RequiredArgsConstructor
public enum TrackingAlert {

    /** 송장 등록 후 24시간 추적 미조회 — 집화 스캔 전에는 데이터가 없는 게 정상이라 등록 직후 경고하지 않는다. */
    PICKUP_UNCONFIRMED("집화 확인 필요", OrderBadgeTone.WARNING),
    /** 집화 후 7일 갱신 없음 — 플랫폼은 분실·누락 판정에 관여하지 않는다(약관 제17조③). 송장 수정만 열린다. */
    STALLED("추적 정지", OrderBadgeTone.DANGER);

    private final String label;
    private final OrderBadgeTone tone;
}
