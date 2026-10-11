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
    RESIDENT_NUMBER_MISSING("주민등록번호 미등록", "주민등록번호 등록 후 지급"),

    // ---- PG 파트너 등록(44 포트원 설계서 4-3) — 수취자 행의 fail_code 로 남고 지급 배치 앞단이 다시 등록을 시도한다.
    /** PG 파트너 미등록 · 심사 중 · 거절. */
    PARTNER_NOT_READY("PG 파트너 등록 대기", "PG 파트너 등록 후 지급"),
    /** 등록 계좌의 예금주가 회원 정보와 다르다. */
    ACCOUNT_HOLDER_MISMATCH("예금주 불일치", "계좌 변경 후 지급"),
    /** 사업자 휴 · 폐업. */
    COMPANY_NOT_IN_BUSINESS("사업자 휴·폐업", "사업자 정보 확인 후 지급"),
    /** 등록 계좌 없음 — 지시 전에 알았다. */
    ACCOUNT_MISSING("등록 계좌 없음", "계좌 등록 후 지급"),
    /** PG 미지원 은행. */
    UNSUPPORTED_BANK("미지원 은행", "계좌 변경 후 지급"),
    /** PG 상점에 파트너 정산 기능이 꺼져 있다 — 활성화되면 지급 배치 앞단이 다시 등록한다. */
    PLATFORM_NOT_ENABLED("PG 파트너 정산 미활성화", "PG 활성화 후 지급");

    /** 파트너 사유 — 지급 배치 앞단이 다시 등록을 시도하는 코드. */
    public static final java.util.Set<String> PARTNER_CODES = java.util.Set.of(PARTNER_NOT_READY.name(),
            ACCOUNT_HOLDER_MISMATCH.name(), COMPANY_NOT_IN_BUSINESS.name(), ACCOUNT_MISSING.name(),
            UNSUPPORTED_BANK.name(), PLATFORM_NOT_ENABLED.name());

    public static java.util.Optional<PayoutBlockReason> fromCode(String code) {
        if (code == null) {
            return java.util.Optional.empty();
        }
        for (PayoutBlockReason reason : values()) {
            if (reason.name().equals(code)) {
                return java.util.Optional.of(reason);
            }
        }
        return java.util.Optional.empty();
    }

    private final String label;
    private final String dueNoteFormat;
}
