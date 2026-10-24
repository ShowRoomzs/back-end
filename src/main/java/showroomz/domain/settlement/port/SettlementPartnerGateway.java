package showroomz.domain.settlement.port;

import showroomz.domain.settlement.type.SettlementPayee;

/**
 * 수취자를 PG 지급 시스템의 「셀러」(돈 받을 사람)로 등록 · 갱신하는 포트(44_토스페이먼츠_지급대행_연동_BE_설계서.md 4절).
 * 토스 구현은 {@code TossSellerRegistrar}, {@code SIMULATED} 는 항상 승인하는 Noop 이다.
 *
 * <p>PG 가 발급한 식별자({@link PartnerProfile#externalId})는 호출자가 보관하고 되돌려 준다 — 토스는 id 를 발급하고 우리 참조
 * ({@link PartnerProfile#partnerRef}) 로는 조회할 수 없다(0-5). 생성 · 갱신 · 「이미 있음」의 판단은 구현이 한다(멱등 · 0-6).
 */
public interface SettlementPartnerGateway {

    /** 참조 id 접두 — 테스트 모드는 운영과 섞이지 않게 {@code t-}(3-4). */
    default String partnerRefPrefix() {
        return "";
    }

    /** 바깥 시스템에 실제로 등록하는가 — {@code SIMULATED}(Noop)는 false 라 회원 정보에 셀러 연결을 남기지 않는다. */
    default boolean isExternal() {
        return true;
    }

    /** 연결의 환경 — {@code TEST} / {@code LIVE}. 회원 정보의 {@code pg_partner_env} 와 다르면 연결 없음으로 본다(3-1). */
    default String env() {
        return "LIVE";
    }

    PartnerResult ensure(PartnerProfile profile);

    /** 웹훅 · 저장된 원시 상태 → 우리 상태. 지급 가능 여부의 유일한 판정식. */
    default PartnerStatus statusOf(String rawStatus) {
        return PartnerStatus.APPROVED;
    }

    /** 대기(PENDING) 원시 상태의 보류 사유 코드 — 본인인증 · KYC(토스 설계서 0-8). */
    default String blockCodeOf(String rawStatus) {
        return "PARTNER_NOT_READY";
    }

    /** 지급 가능하지만 한도가 있는 상태인가(토스 {@code PARTIALLY_APPROVED} · 주당 1천만 원) — 도메인이 먼저 센다(5-2 ①). */
    default boolean isLimited(String rawStatus) {
        return false;
    }

    /**
     * @param partnerRef 3-4 규칙으로 호출자가 정한 참조 id({@code refSellerId})
     * @param externalId PG 가 발급한 셀러 id — 우리 DB 에 연결 기록이 있으면 그 값 · 없으면 null
     * @param business   사업자(브랜드 · 사업자 인플루언서)면 값, 비사업자면 null
     * @param birthdate  비사업자 인플루언서의 생년월일 {@code yyyy-MM-dd} — 토스는 받지 않는다(SIMULATED · Fake 호환용)
     * @param existing   우리 DB 에 이 환경의 연결이 있는가
     */
    record PartnerProfile(SettlementPayee payee, Long refId, String partnerRef, String externalId, String name,
                          Contact contact, Account account, Business business, String birthdate, boolean existing) {
    }

    record Contact(String name, String phoneNumber, String email) {
    }

    /**
     * @param bankCode 표준 은행 코드(3자리 · {@code bank.bank_code}) — 토스는 그대로 받는다. 이름을 코드로 되돌릴 수 없으면 null
     *                 (구현이 {@code UNSUPPORTED_BANK} 로 거절한다). 계좌가 비어 있어도 그대로 넘긴다({@code ACCOUNT_MISSING})
     */
    record Account(String bankCode, String bankName, String accountNumber, String holder) {

        public boolean isMissing() {
            return bankName == null || bankName.isBlank() || accountNumber == null || accountNumber.isBlank();
        }
    }

    /** @param corporate 법인(토스 {@code CORPORATE}) / 개인사업자({@code INDIVIDUAL_BUSINESS}) — 도메인이 판정한다(4-1 D-14) */
    record Business(String companyName, String registrationNumber, String representativeName, String address,
                    String businessType, boolean corporate) {
    }

    /**
     * @param externalId PG 발급 셀러 id — 성공 · 대기 때 값(호출자가 저장한다)
     * @param rawStatus  PG 원시 상태 문자열 — {@code pg_partner_status}
     * @param failCode   실패 · 보류 사유 코드 — {@code PayoutBlockReason} 이름 또는 어댑터 분류 코드 · 성공이면 null
     */
    record PartnerResult(PartnerStatus status, String externalId, String rawStatus, String failCode, String failReason) {

        public static PartnerResult approved(String externalId, String rawStatus) {
            return new PartnerResult(PartnerStatus.APPROVED, externalId, rawStatus, null, null);
        }

        public static PartnerResult pending(String externalId, String rawStatus, String failCode, String failReason) {
            return new PartnerResult(PartnerStatus.PENDING, externalId, rawStatus, failCode, failReason);
        }

        public static PartnerResult failed(String failCode, String failReason) {
            return new PartnerResult(PartnerStatus.FAILED, null, null, failCode, failReason);
        }

        public boolean isApproved() {
            return status == PartnerStatus.APPROVED;
        }

        /** 연결을 저장할 수 있는가 — PG 에 셀러가 생겼다(승인 · 대기 · 거절). 실패는 생기지 않았다. */
        public boolean hasExternalId() {
            return externalId != null && !externalId.isBlank();
        }
    }

    enum PartnerStatus { APPROVED, PENDING, REJECTED, FAILED }
}
