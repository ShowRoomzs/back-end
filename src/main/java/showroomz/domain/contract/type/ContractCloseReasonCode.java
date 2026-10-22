package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * [계약 취소] 사유 5종(시안 C4) — 서명 요청 발송 이후 운영자가 취소할 때 쓴다.
 * 브랜드에게는 계약 취소가 없고 운영자는 대개 브랜드의 요청으로 취소하므로 문구는 브랜드 관점 그대로다.
 * ETC면 메모가 필수다 — 기존 INQUIRY_DELETE_REASON_DETAIL_REQUIRED와 같은 패턴.
 *
 * <p>인플루언서의 거절 사유는 아직 확정 전이라(설계서 미결 #8) 여기에 섞지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum ContractCloseReasonCode {
    /** 어드민 01 C6(1021 기-06) 선택지 — 당사자 요청 2 · 내용 오류 · 기타. */
    PARTY_RENEGOTIATION("당사자 요청 — 조건 재협의 필요"),
    PARTY_CANNOT_PROCEED("당사자 요청 — 공구 진행 불가"),
    CONTENT_ERROR_AFTER_REVIEW("계약 내용 오류 — 검토 통과 후 발견"),
    /** 이하 4종은 1021 이전 선택지 — 저장된 행 표시 · 구 FE 호환으로 남긴다. */
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
