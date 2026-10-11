package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인플루언서 몫 지급 보류 사유(44 어드민 설계서 5-4) — 이 둘만 지급을 막는다. 브랜드 세금계산서 발행 대기는 지급과 무관하다.
 */
@Getter
@RequiredArgsConstructor
public enum PayoutBlockReason {
    /** 사업자 ∧ 인플루언서 세금계산서가 확인(VERIFIED) 전 — M4 확인 → 확인일 + N영업일. */
    TAX_INVOICE_UNVERIFIED("세금계산서 확인 전", "발행 확인 후 + %d영업일"),
    /** 비사업자 ∧ 주민등록번호 미등록 — 스튜디오 기본정보에서 등록하면 지급 배치 앞단이 다시 판정한다. */
    RESIDENT_NUMBER_MISSING("주민등록번호 미등록", "주민등록번호 등록 후 지급");

    private final String label;
    private final String dueNoteFormat;
}
