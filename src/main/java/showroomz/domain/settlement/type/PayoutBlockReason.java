package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.Set;

/**
 * 지급 보류 사유(44 어드민 설계서 5-4 · 44 토스 설계서 4-3 · 5-5). 앞 둘은 인플루언서 몫만 막고({@code PayoutBlockPolicy}), PG 셀러
 * 사유는 수취자 행의 {@code fail_code} 로 남아 지급 배치 앞단 · {@code seller.changed} 웹훅이 다시 판정한다.
 */
@Getter
@RequiredArgsConstructor
public enum PayoutBlockReason {
    /** 사업자 ∧ 인플루언서 세금계산서가 확인(VERIFIED) 전 — M4 확인 → 확인일 + N영업일. */
    TAX_INVOICE_UNVERIFIED("세금계산서 확인 전", "발행 확인 후 + %d영업일"),
    /** 비사업자 ∧ 주민등록번호 미등록 — 스튜디오 기본정보에서 등록하면 지급 배치 앞단이 다시 판정한다. */
    /** @deprecated 1021 기-12 로 지급 보류 사유에서 빠졌다 — 저장된 fail_code 를 읽기 위해서만 남긴다. */
    @Deprecated
    RESIDENT_NUMBER_MISSING("주민등록번호 미등록", "주민등록번호 등록 후 지급"),

    // ---- PG 셀러 등록(44 토스 설계서 4-3 · 5-5) — 수취자 행의 fail_code 로 남고 지급 배치 앞단이 다시 등록 · 상태 조회를 시도한다.
    /** PG 셀러 미등록 · 프로필 불완전 · id 복구 실패 · PG 호출 실패. */
    PARTNER_NOT_READY("PG 셀러 등록 대기", "PG 셀러 등록 후 지급"),
    /** 등록 계좌의 예금주가 회원 정보와 다르다(토스 {@code INVALID_ACCOUNT_INFO · NOT_SELLER_ACCOUNT}). */
    ACCOUNT_HOLDER_MISMATCH("예금주 불일치", "계좌 변경 후 지급"),
    /** 사업자번호 무효 · 휴 · 폐업(토스 {@code INVALID_BUSINESS_NUMBER}). */
    COMPANY_NOT_IN_BUSINESS("사업자 휴·폐업", "사업자 정보 확인 후 지급"),
    /** 등록 계좌 없음 — 지시 전에 알았다. */
    ACCOUNT_MISSING("등록 계좌 없음", "계좌 등록 후 지급"),
    /** 계좌번호 >14자 · 예금주 >50자 — PG 가 받지 않는 형식(사전 검사). */
    ACCOUNT_INVALID("계좌 형식 오류", "계좌 변경 후 지급"),
    /** PG 미지원 은행(HSBC 054). */
    UNSUPPORTED_BANK("미지원 은행", "계좌 변경 후 지급"),
    /** 셀러 {@code APPROVAL_REQUIRED} — 수취자 본인이 토스 본인인증 문자를 끝내야 한다(0-8). 재분배 대상이 아니다. */
    IDENTITY_VERIFICATION_REQUIRED("본인인증 대기", "토스 본인인증 후 지급"),
    /** 셀러 {@code KYC_REQUIRED} 또는 주당 한도 초과 예상 — KYC 통과 뒤 자동 해제. 재분배 대상이 아니다. */
    KYC_REQUIRED("KYC 심사 대기", "KYC 완료 후 지급"),
    /** 지급대행 계약 없음 · 이용료 미납 · 계정 이용 정지 — 운영자. */
    PAYOUT_CONTRACT_BLOCKED("PG 지급대행 이용 불가", "PG 계약 확인 후 지급"),

    // ---- 계정 이용 정지(§51-3 · 52 설계서 0-4) — 재등록 · 수취자 알림 대상이 아니다. 운영자의 정지 해제가 푼다.
    /** 브랜드 정지 · 지급 정지 사유(정산 정보 불일치 · 사업자 정보 허위 기재) — 해제 다음 영업일에 지급. */
    BRAND_SUSPENDED("계정 이용 정지 · 지급 정지", "정지 해제 다음 영업일 지급"),
    /** 인플루언서 정지 · 정산 정보 불일치(53 설계서 4-3) — 리워드만 멈춘다. 해제 다음 영업일에 지급. */
    CREATOR_SUSPENDED("지급 정지 · 계정 이용 정지", "정지 해제 다음 영업일 지급"),

    // ---- 인플루언서 정산 계좌 변경(50 설계서 1-3 · 6-5) — PG 재등록 경로가 아니다. 계좌 확인이 끝나면 확인 훅이 푼다.
    /** 스튜디오에서 계좌를 바꿨고 실명 확인(토스 셀러 계좌 검증)이 아직 끝나지 않았다 · 실패했다. */
    CREATOR_ACCOUNT_UNVERIFIED("정산 계좌 확인 중", "계좌 확인 후 지급");

    /** PG 셀러 사유 — 지급 배치 앞단이 다시 등록 · 상태 조회를 시도하는 코드. */
    public static final Set<String> PARTNER_CODES = Set.of(PARTNER_NOT_READY.name(),
            ACCOUNT_HOLDER_MISMATCH.name(), COMPANY_NOT_IN_BUSINESS.name(), ACCOUNT_MISSING.name(), ACCOUNT_INVALID.name(),
            UNSUPPORTED_BANK.name(), IDENTITY_VERIFICATION_REQUIRED.name(), KYC_REQUIRED.name(),
            PAYOUT_CONTRACT_BLOCKED.name());

    /** 수취자 본인이 풀어야 하는 사유 — 운영자 알림 대신 수취자 알림(4-3). 재분배(M3) 대상이 아니다. */
    public static final Set<String> PAYEE_ACTION_CODES = Set.of(IDENTITY_VERIFICATION_REQUIRED.name(),
            KYC_REQUIRED.name());

    public static Optional<PayoutBlockReason> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (PayoutBlockReason reason : values()) {
            if (reason.name().equals(code)) {
                return Optional.of(reason);
            }
        }
        return Optional.empty();
    }

    private final String label;
    private final String dueNoteFormat;
}
