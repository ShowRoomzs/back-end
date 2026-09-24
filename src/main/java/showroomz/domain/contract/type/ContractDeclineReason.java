package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인플루언서의 [거절] 사유 5종(시안 S5 · §27 설계서 5-1).
 *
 * <p><b>취소 사유 {@link ContractCloseReasonCode}와 별도 enum이다.</b> 값이 하나도 겹치지
 * 않고(취소는 재고 부족 · 일정 변경 · 협의 중단 …), 한 enum에 합치면 거절 모달에 브랜드용
 * 선택지가 뜰 수 있는 구조가 된다.
 *
 * <p>{@code close_reason_code} 컬럼은 둘이 공유하되 <b>해석은 {@code close_actor_type}이 정한다</b> —
 * 종결 응답이 {@code closeActorType}을 반드시 함께 내리는 이유가 이것이다(설계서 0-5).
 *
 * <p>ETC여도 메모를 필수로 걸지 않는다 — 시안 S5의 메모 라벨에 {@code *}가 없다. 파트너 취소가
 * ETC에 메모를 필수로 건 것과 대칭이 깨진 지점이라 §28-8 D #10에서 함께 정리한다(설계서 미결 #2).
 */
@Getter
@RequiredArgsConstructor
public enum ContractDeclineReason {
    CONDITION_RENEGOTIATION("조건 재협의 필요"),
    SCHEDULE_MISMATCH("일정이 맞지 않음"),
    NOT_FIT_SHOWROOM("상품이 내 쇼룸과 맞지 않음"),
    CONTENT_BURDEN("콘텐츠 의무가 과함"),
    ETC("기타");

    private final String label;
}
