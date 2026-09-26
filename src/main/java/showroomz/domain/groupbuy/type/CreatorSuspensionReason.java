package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인플루언서의 공구 중단 요청 사유(C7) — 30 설계 7-2 #10이 스튜디오 설계로 넘긴 값(31 설계 5-3).
 *
 * <p>브랜드 사유({@link SuspensionReasonCode})와 <b>분리한다</b> — 겹치는 값이 없고, 합치면 C7에 「가격·조건 오기」 같은
 * 브랜드용 선택지가 뜰 수 있는 구조가 된다. {@code reason_code} 컬럼은 공유하고 해석은 {@code requester_type}이 정한다.
 *
 * <p>시안 6종으로 잠정 집행한다({@code [근거 대기 ⑥]}) — 컬럼이 VARCHAR라 값이 바뀌어도 스키마 변경이 없다.
 */
@Getter
@RequiredArgsConstructor
public enum CreatorSuspensionReason {
    PRODUCT_DEFECT("상품에 문제가 있어 추천을 이어갈 수 없음"),
    DELIVERY_FAILURE("배송 지연 · 미발송이 계속됨"),
    CONSUMER_COMPLAINTS("소비자 불만이 반복적으로 접수됨"),
    BRAND_UNREACHABLE("브랜드와 연락이 되지 않음"),
    /** 이 사유로 중단되면 브랜드는 남은 판매 기간을 잃는다 — 서버는 통계로 남길 뿐 금전 처리를 하지 않는다(§29-9). */
    PERSONAL_REASON("개인 사정으로 진행이 어려움"),
    ETC("기타");

    private final String label;
}
