package showroomz.domain.cart.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 장바구니에 담긴 뒤 <b>살 수 없게 된 사유</b> (C8 §담은 뒤 마감·품절).
 *
 * <p>장바구니는 담은 시점과 결제 시점 사이의 시차가 가장 큰 화면이다. 여기서 걸러 주지 않으면
 * 결제 단계에서야 막혀 이탈이 커지므로, 목록 응답이 항목마다 사유를 함께 내려준다.
 *
 * <p>값이 둘뿐인 것은 화면 라벨이 둘이기 때문이다(§05 판매 종료 상태 — "품절" · "마감").
 * 상품이 미진열로 내려간 경우도 {@link #GROUP_BUY_CLOSED}로 접는다 — 사용자에게는
 * "이 공구에서는 더 살 수 없다"로 똑같이 읽히고, 세 번째 라벨을 만들면 화면이 그릴 수 없는
 * 상태가 서버에서만 생긴다.
 *
 * <p>사유가 붙은 항목은 <b>목록에서 지우지 않는다</b>. 담아 둔 것은 사용자의 기억이자 의도이고,
 * 말없이 사라지면 합계가 줄어든 이유도 알 수 없다. 대신 선택에서 빠져 합계·배송비 계산에서
 * 제외되고, 삭제는 사용자가 결정한다.
 */
@Getter
@AllArgsConstructor
public enum CartUnavailableReason {

    /** 공구가 끝났거나(연결 해제) 상품이 내려갔다 — 되돌릴 길이 없어 삭제만 남는다 */
    GROUP_BUY_CLOSED("마감", "공구가 마감되어 주문할 수 없어요"),

    /** 옵션(Variant) 재고 소진 또는 관리자 강제 품절 — 재고는 상품이 아니라 옵션마다 소진된다 */
    SOLD_OUT("품절", "품절되어 주문할 수 없어요");

    /** 썸네일 위에 얹는 짧은 라벨 */
    private final String label;

    /** 수량 스테퍼·가격 자리를 대신하는 사유 문구 */
    private final String message;
}
