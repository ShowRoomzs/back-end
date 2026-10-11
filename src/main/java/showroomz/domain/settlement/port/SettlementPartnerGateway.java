package showroomz.domain.settlement.port;

import showroomz.domain.settlement.type.SettlementPayee;

/**
 * 수취자를 PG 지급 시스템의 「파트너」(돈 받을 사람)로 등록 · 갱신하는 포트(44_포트원_파트너정산_연동_BE_설계서.md 4절).
 * 포트원 구현은 {@code PortOnePartnerRegistrar}, {@code SIMULATED} 는 항상 승인하는 Noop 이다.
 *
 * <p>호출자는 {@link PartnerProfile#existing()} 으로 「우리 DB 에 연결 기록이 있는가」만 알려 준다 — 생성 · 갱신 · 「이미 있음」의
 * 판단은 구현이 한다(멱등 · 0-7).
 */
public interface SettlementPartnerGateway {

    /** 파트너 id 접두 — 테스트 모드는 운영 id 와 섞이지 않게 {@code t-}(3-3). */
    default String partnerIdPrefix() {
        return "";
    }

    /** 바깥 시스템에 실제로 등록하는가 — {@code SIMULATED}(Noop)는 false 라 회원 정보에 파트너 연결을 남기지 않는다. */
    default boolean isExternal() {
        return true;
    }

    PartnerResult ensure(PartnerProfile profile);

    /**
     * @param partnerId  3-3 규칙으로 호출자가 정한 id
     * @param business   사업자(브랜드 · 사업자 인플루언서)면 값, 비사업자면 null
     * @param birthdate  비사업자 인플루언서(원천징수 대상자)의 생년월일 {@code yyyy-MM-dd}
     * @param existing   우리 DB 에 이 파트너 id 가 연결돼 있는가
     */
    record PartnerProfile(SettlementPayee payee, Long refId, String partnerId, String name, Contact contact,
                          Account account, Business business, String birthdate, boolean existing) {
    }

    record Contact(String name, String phoneNumber, String email) {
    }

    /**
     * @param bankCode 표준 은행 코드(3자리 · {@code bank.bank_code}) — 구현이 PG 은행 enum 으로 바꾼다. 이름을 코드로 되돌릴 수
     *                 없으면 null(구현이 {@code UNSUPPORTED_BANK} 로 거절한다). 계좌가 비어 있어도 그대로 넘긴다({@code ACCOUNT_MISSING})
     */
    record Account(String bankCode, String bankName, String accountNumber, String holder) {

        public boolean isMissing() {
            return bankName == null || bankName.isBlank() || accountNumber == null || accountNumber.isBlank();
        }
    }

    record Business(String companyName, String registrationNumber, String representativeName, String address,
                    String businessType) {
    }

    /** @param failCode 실패 사유 코드 — {@code PayoutBlockReason} 이름 또는 어댑터 분류 코드 · 성공이면 null */
    record PartnerResult(PartnerStatus status, String failCode, String failReason) {

        public static PartnerResult approved() {
            return new PartnerResult(PartnerStatus.APPROVED, null, null);
        }

        public static PartnerResult failed(String failCode, String failReason) {
            return new PartnerResult(PartnerStatus.FAILED, failCode, failReason);
        }

        public boolean isApproved() {
            return status == PartnerStatus.APPROVED;
        }
    }

    enum PartnerStatus { APPROVED, PENDING, REJECTED, FAILED }
}
