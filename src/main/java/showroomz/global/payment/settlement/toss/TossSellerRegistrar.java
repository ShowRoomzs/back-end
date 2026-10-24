package showroomz.global.payment.settlement.toss;

import lombok.extern.slf4j.Slf4j;
import showroomz.domain.settlement.port.SettlementPartnerGateway;
import showroomz.domain.settlement.service.SettlementPartnerIds;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.config.properties.TossPayoutsProperties;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;

import showroomz.global.payment.settlement.toss.TossPayoutsDtos.Company;
import showroomz.global.payment.settlement.toss.TossPayoutsDtos.Individual;
import showroomz.global.payment.settlement.toss.TossPayoutsDtos.Seller;
import showroomz.global.payment.settlement.toss.TossPayoutsDtos.SellerRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 토스 셀러 등록 · 갱신(44 토스 설계서 4-1 · 4-2). 계좌 · 사업자 검증은 토스가 등록 호출 안에서 한다(0-7) — 여기서는 프로필 완전성 · 형식만
 * 먼저 본다. id 는 토스가 발급하므로 응답의 {@code id} 를 꼭 돌려준다(0-5). 멱등 키는 참조 id 에서(0-6).
 */
@Slf4j
public class TossSellerRegistrar implements SettlementPartnerGateway {

    static final String TYPE_CORPORATE = "CORPORATE";
    static final String TYPE_INDIVIDUAL_BUSINESS = "INDIVIDUAL_BUSINESS";
    static final String TYPE_INDIVIDUAL = "INDIVIDUAL";
    /** 지급대행이 지원하지 않는 은행 — HSBC(1-9). */
    static final Set<String> UNSUPPORTED_BANK_CODES = Set.of("054");
    private static final int ACCOUNT_NUMBER_MAX = 14;
    private static final int HOLDER_MAX = 50;
    private static final int PHONE_MIN = 8;

    private final TossPayoutsClient client;
    private final boolean test;
    private final String refPrefix;
    private final String corporateOverrideBrn;

    public TossSellerRegistrar(TossPayoutsClient client, boolean test, TossPayoutsProperties properties) {
        this.client = client;
        this.test = test;
        this.refPrefix = test ? properties.getPartnerRefPrefixTest() : "";
        this.corporateOverrideBrn = test && properties.getTest().hasCorporateOverride()
                ? digits(properties.getTest().getCorporateOverrideBrn()) : null;
    }

    @Override
    public String partnerRefPrefix() {
        return refPrefix;
    }

    @Override
    public String env() {
        return test ? "TEST" : "LIVE";
    }

    @Override
    public PartnerStatus statusOf(String rawStatus) {
        return TossPayoutFailureClassifier.partnerStatusOf(rawStatus);
    }

    @Override
    public String blockCodeOf(String rawStatus) {
        return TossPayoutFailureClassifier.blockCodeOf(rawStatus);
    }

    @Override
    public boolean isLimited(String rawStatus) {
        return TossPayoutFailureClassifier.STATUS_PARTIALLY_APPROVED.equals(rawStatus);
    }

    @Override
    public PartnerResult ensure(PartnerProfile profile) {
        PartnerResult precheck = precheck(profile);
        if (precheck != null) {
            return precheck;
        }
        try {
            Seller seller;
            if (profile.existing() && profile.externalId() != null) {
                seller = update(profile);
            } else {
                seller = create(profile);
            }
            return toResult(seller);
        } catch (PaymentGatewayRejectedException e) {
            log.warn("토스 셀러 등록 거절 - ref: {}, {}", profile.partnerRef(), e.getMessage());
            return PartnerResult.failed(TossPayoutFailureClassifier.sellerFailCode(e.getType()), e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 사전 검사(4-2 ①)

    private PartnerResult precheck(PartnerProfile profile) {
        if (profile.account().isMissing()) {
            return PartnerResult.failed(TossPayoutFailureClassifier.ACCOUNT_MISSING, "등록 계좌 없음");
        }
        String bankCode = profile.account().bankCode();
        if (bankCode == null || bankCode.isBlank() || UNSUPPORTED_BANK_CODES.contains(bankCode.trim())) {
            return PartnerResult.failed(TossPayoutFailureClassifier.UNSUPPORTED_BANK,
                    "지급대행 미지원 은행 — " + profile.account().bankName() + "(" + bankCode + ")");
        }
        String number = digits(profile.account().accountNumber());
        if (number.length() > ACCOUNT_NUMBER_MAX) {
            return PartnerResult.failed(TossPayoutFailureClassifier.ACCOUNT_INVALID, "계좌번호 14자 초과");
        }
        if (profile.account().holder() != null && profile.account().holder().length() > HOLDER_MAX) {
            return PartnerResult.failed(TossPayoutFailureClassifier.ACCOUNT_INVALID, "예금주 50자 초과");
        }
        if (isBlank(profile.contact().email())) {
            return PartnerResult.failed(TossPayoutFailureClassifier.PARTNER_NOT_READY, "이메일 없음");
        }
        if (digits(profile.contact().phoneNumber()).length() < PHONE_MIN) {
            return PartnerResult.failed(TossPayoutFailureClassifier.PARTNER_NOT_READY, "전화번호 없음(8자 이상)");
        }
        if (profile.business() != null) {
            if (corporateOverrideBrn == null && digits(profile.business().registrationNumber()).length() != 10) {
                return PartnerResult.failed(TossPayoutFailureClassifier.PARTNER_NOT_READY, "사업자등록번호 10자리 아님");
            }
            if (isBlank(profile.business().representativeName())) {
                return PartnerResult.failed(TossPayoutFailureClassifier.PARTNER_NOT_READY, "대표자명 없음");
            }
        } else if (isBlank(profile.contact().name())) {
            return PartnerResult.failed(TossPayoutFailureClassifier.PARTNER_NOT_READY, "이름 없음");
        }
        return null;
    }

    // ------------------------------------------------------------------ 등록(4-2 ③)

    private Seller create(PartnerProfile profile) {
        String type = businessType(profile, false);
        try {
            return doCreate(profile, type);
        } catch (PaymentGatewayRejectedException e) {
            if (TossPayoutFailureClassifier.DUPLICATE_SELLER.equals(e.getType())) {
                // 우리 DB 에 연결이 없을 뿐 토스에는 있다(이전 시도의 커밋 실패) — 목록에서 id 를 찾아 갱신으로 맞춘다.
                Seller found = client.findSellerByRef(profile.partnerRef());
                if (found == null) {
                    throw new PaymentGatewayRejectedException(e.getHttpStatus(), TossPayoutFailureClassifier.PARTNER_NOT_READY,
                            "중복 셀러 · id 복구 실패 — " + profile.partnerRef(), e);
                }
                log.info("토스 셀러 id 복구 - ref: {}, id: {}", profile.partnerRef(), found.id());
                return update(withExternalId(profile, found.id()));
            }
            if (TossPayoutFailureClassifier.INVALID_BUSINESS_NUMBER.equals(e.getType()) && profile.business() != null
                    && corporateOverrideBrn == null) {
                // 법인 ↔ 개인사업자 판정이 틀렸을 수 있다 — 반대 유형으로 1회(4-1 D-14).
                log.info("토스 셀러 유형 재시도 - ref: {}, type: {} → 반대", profile.partnerRef(), type);
                return doCreate(profile, businessType(profile, true));
            }
            throw e;
        }
    }

    private Seller doCreate(PartnerProfile profile, String type) {
        SellerRequest request = new SellerRequest(profile.partnerRef(), type,
                TYPE_INDIVIDUAL.equals(type) ? null : company(profile),
                TYPE_INDIVIDUAL.equals(type) ? new Individual(profile.contact().name(), profile.contact().email(),
                        digits(profile.contact().phoneNumber())) : null,
                account(profile), metadata(profile));
        return client.createSeller(request, SettlementPartnerIds.idempotencyKey(profile.partnerRef()));
    }

    /** 갱신 — 계좌 · 연락처만(사업자번호 · 유형은 불변 · 1-3). 토스에 없으면 새로 만든다(콘솔 삭제 · 환경 전환). */
    private Seller update(PartnerProfile profile) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("account", account(profile));
        if (profile.business() != null) {
            changes.put("company", new Company(null, profile.business().representativeName(), null,
                    profile.contact().email(), digits(profile.contact().phoneNumber())));
        } else {
            changes.put("individual", new Individual(null, profile.contact().email(), digits(profile.contact().phoneNumber())));
        }
        try {
            return client.updateSeller(profile.externalId(), changes);
        } catch (PaymentGatewayRejectedException e) {
            if (TossPayoutFailureClassifier.NOT_FOUND.equals(e.getType())) {
                log.warn("토스 셀러 없음 — 다시 만든다 - ref: {}, id: {}", profile.partnerRef(), profile.externalId());
                return create(withExternalId(profile, null));
            }
            throw e;
        }
    }

    // ------------------------------------------------------------------ 매핑(4-1)

    private String businessType(PartnerProfile profile, boolean flip) {
        if (profile.business() == null) {
            // 테스트 환경은 법인만 등록된다 — 대체 사업자번호가 있으면 법인으로(0-10).
            return corporateOverrideBrn != null ? TYPE_CORPORATE : TYPE_INDIVIDUAL;
        }
        if (corporateOverrideBrn != null) {
            return TYPE_CORPORATE;
        }
        boolean corporate = profile.business().corporate() != flip;
        return corporate ? TYPE_CORPORATE : TYPE_INDIVIDUAL_BUSINESS;
    }

    private Company company(PartnerProfile profile) {
        String brn = corporateOverrideBrn != null ? corporateOverrideBrn
                : digits(profile.business() == null ? null : profile.business().registrationNumber());
        String name = profile.business() == null ? profile.name() : firstNonBlank(profile.business().companyName(), profile.name());
        String representative = profile.business() == null ? profile.contact().name()
                : firstNonBlank(profile.business().representativeName(), profile.contact().name());
        return new Company(name, representative, brn, profile.contact().email(), digits(profile.contact().phoneNumber()));
    }

    private static TossPayoutsDtos.Account account(PartnerProfile profile) {
        return new TossPayoutsDtos.Account(profile.account().bankCode().trim(), digits(profile.account().accountNumber()),
                profile.account().holder());
    }

    private static Map<String, String> metadata(PartnerProfile profile) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("payee", profile.payee().name());
        metadata.put(profile.payee() == SettlementPayee.BRAND ? "marketId" : "creatorId", String.valueOf(profile.refId()));
        return metadata;
    }

    private static PartnerResult toResult(Seller seller) {
        String raw = seller.status();
        return switch (TossPayoutFailureClassifier.partnerStatusOf(raw)) {
            case APPROVED -> PartnerResult.approved(seller.id(), raw);
            case PENDING -> PartnerResult.pending(seller.id(), raw, TossPayoutFailureClassifier.blockCodeOf(raw),
                    TossPayoutFailureClassifier.STATUS_KYC_REQUIRED.equals(raw) ? "토스 KYC 심사 대기" : "토스 본인인증 대기");
            default -> new PartnerResult(PartnerStatus.REJECTED, seller.id(), raw, TossPayoutFailureClassifier.PARTNER_NOT_READY,
                    "토스 셀러 상태 " + raw);
        };
    }

    private static PartnerProfile withExternalId(PartnerProfile p, String externalId) {
        return new PartnerProfile(p.payee(), p.refId(), p.partnerRef(), externalId, p.name(), p.contact(), p.account(),
                p.business(), p.birthdate(), externalId != null);
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }
}
