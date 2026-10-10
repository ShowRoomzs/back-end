package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** M4 반려 사유 3종(44 어드민 설계서 7-6) — 스튜디오 D5b 에 라벨로 뜬다. */
@Getter
@RequiredArgsConstructor
public enum TaxInvoiceRejectReason {
    AMOUNT_MISMATCH("금액 불일치"),
    RECIPIENT_MISMATCH("공급받는자 불일치"),
    NOT_FOUND("국세청 조회 불가");

    private final String label;
}
