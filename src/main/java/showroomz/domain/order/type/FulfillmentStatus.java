package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * 하위주문 이행 상태 — 화면 7종 + 결제 전(34 설계서 1-2).
 *
 * <p>취소 요청·집화 확인 필요·추적 정지·발송기한 경과는 <b>상태가 아니다</b>(설계서 0-2 · 0-5) —
 * 처리 후 원래 이행 상태로 돌아가야 하는 값을 같은 필드에 덮어쓰면 복귀점이 사라진다.
 *
 * <p>역방향 전이가 하나도 없다 — 준비 시작 되돌리기 없음(§34-4), 배송중 → 준비중 복귀 없음(§34-5 rev.7),
 * 반송은 환불로 종결(RETURNING 에서 나가는 전이 없음).
 */
@Getter
@RequiredArgsConstructor
public enum FulfillmentStatus {

    /** 결제 대기 — 화면 밖. 셀러 조회는 {@code orders.status = PAID}가 전제라 목록에 나타나지 않는다. */
    PENDING("결제 대기", OrderBadgeTone.NEUTRAL),
    NEW("신규(준비 대기)", OrderBadgeTone.WARNING),
    PREPARING("상품준비중", OrderBadgeTone.INFO),
    SHIPPING("배송중", OrderBadgeTone.INFO),
    /** 위험으로 집행한다 — §34-1 상태표가 최신이다(시안 정정 #19). */
    RETURNING("반송중", OrderBadgeTone.DANGER),
    DELIVERED("배송완료", OrderBadgeTone.SUCCESS),
    CONFIRMED("구매확정", OrderBadgeTone.SUCCESS),
    CANCELLED("취소", OrderBadgeTone.NEUTRAL);

    private final String label;
    private final OrderBadgeTone tone;

    /** 작업 큐 2종 — 소비자 단순 취소권이 갈리는 경계이기도 하다(NEW 까지 열려 있다). */
    public static final Set<FulfillmentStatus> WORKABLE = Set.of(NEW, PREPARING);

    /** 전역 송장 중복 검사의 대상 — 종결 전 상태. 택배사는 송장번호를 재사용하므로 종결 건은 겹쳐도 된다. */
    public static final Set<FulfillmentStatus> INVOICE_ACTIVE = Set.of(NEW, PREPARING, SHIPPING, RETURNING, DELIVERED);

    /** 미종결 — 공구 정산 게이트의 「미종결 주문」 판정(설계서 5-3). RETURNING 은 환불 집행 전까지 미종결이다. */
    public boolean isSettlementOpen() {
        return this != CONFIRMED && this != CANCELLED && this != PENDING;
    }
}
