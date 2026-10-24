package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 [해지 처리] 사유 4종(어드민 01 C7 · 1021 결정 노트 기-05). {@code close_reason_code} 를 취소 · 거절 사유와 함께 쓰므로
 * 값 이름이 겹치지 않게 {@code TERMINATE_} 접두를 둔다({@link ContractCloseReasonLabels} 가 코드만으로 라벨을 고른다).
 */
@Getter
@RequiredArgsConstructor
public enum ContractTerminationReasonCode {
    TERMINATE_CANNOT_PROCEED("당사자 요청 — 공구 진행 불가"),
    TERMINATE_RENEGOTIATION("당사자 요청 — 조건 재협의 필요"),
    TERMINATE_CONTENT_ERROR("계약 내용 오류 — 성립 후 발견"),
    TERMINATE_OTHER("기타(직접 입력)");

    private final String label;
}
