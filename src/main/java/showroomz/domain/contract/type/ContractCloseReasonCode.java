package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 브랜드의 [계약 취소] 사유 5종(시안 C4).
 * ETC면 메모가 필수다 — 기존 INQUIRY_DELETE_REASON_DETAIL_REQUIRED와 같은 패턴.
 *
 * <p>인플루언서의 거절 사유는 아직 확정 전이라(설계서 미결 #8) 여기에 섞지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum ContractCloseReasonCode {
    CONDITION_REVIEW("조건 재검토 필요"),
    OUT_OF_STOCK("상품 재고 부족"),
    SCHEDULE_CHANGE("공구 일정 변경"),
    NEGOTIATION_STOPPED("상대와 협의 중단"),
    ETC("기타");

    private final String label;

    public boolean requiresMemo() {
        return this == ETC;
    }
}
