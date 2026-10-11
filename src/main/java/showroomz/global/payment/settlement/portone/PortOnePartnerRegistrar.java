package showroomz.global.payment.settlement.portone;

import io.portone.sdk.server.common.Bank;
import io.portone.sdk.server.common.Currency;
import io.portone.sdk.server.errors.PlatformPartnerIdAlreadyExistsException;
import io.portone.sdk.server.errors.PlatformPartnerNotFoundException;
import io.portone.sdk.server.platform.PlatformClient;
import io.portone.sdk.server.platform.PlatformPartner;
import io.portone.sdk.server.platform.PlatformPartnerTaxationType;
import io.portone.sdk.server.platform.UpdatePlatformPartnerBodyAccount;
import io.portone.sdk.server.platform.UpdatePlatformPartnerBodyContact;
import io.portone.sdk.server.platform.UpdatePlatformPartnerBodyType;
import io.portone.sdk.server.platform.UpdatePlatformPartnerBodyTypeBusiness;
import io.portone.sdk.server.platform.UpdatePlatformPartnerBodyTypeWhtPayer;
import io.portone.sdk.server.platform.account.PlatformAccountHolder;
import io.portone.sdk.server.platform.company.GetPlatformCompanyStatePayload;
import io.portone.sdk.server.platform.partner.CreatePlatformPartnerBodyAccount;
import io.portone.sdk.server.platform.partner.CreatePlatformPartnerBodyContact;
import io.portone.sdk.server.platform.partner.CreatePlatformPartnerBodyType;
import io.portone.sdk.server.platform.partner.CreatePlatformPartnerBodyTypeBusiness;
import io.portone.sdk.server.platform.partner.CreatePlatformPartnerBodyTypeWhtPayer;
import lombok.extern.slf4j.Slf4j;
import showroomz.domain.settlement.port.SettlementPartnerGateway;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;

import java.util.List;
import java.util.Optional;

/**
 * 포트원 파트너 등록 · 갱신(44_포트원_파트너정산_연동_BE_설계서.md 4-2). 계좌 예금주 조회 → 사업자 상태 조회 → 생성(이미 있으면 갱신).
 * id 는 호출자가 정한다(멱등 · 0-7). 모든 호출은 모드의 {@code test} 플래그를 단다.
 */
@Slf4j
public class PortOnePartnerRegistrar implements SettlementPartnerGateway {

    private static final String IN_BUSINESS = "IN_BUSINESS";

    private final PlatformClient client;
    private final boolean test;
    private final String partnerIdPrefix;
    private final PortOnePlatformCalls calls;

    public PortOnePartnerRegistrar(PlatformClient client, boolean test, String partnerIdPrefix, long timeoutMillis) {
        this.client = client;
        this.test = test;
        this.partnerIdPrefix = partnerIdPrefix;
        this.calls = new PortOnePlatformCalls(timeoutMillis);
    }

    @Override
    public String partnerIdPrefix() {
        return partnerIdPrefix;
    }

    @Override
    public PartnerResult ensure(PartnerProfile profile) {
        // 프로필 완전성(4-1 · 4-2) — 포트원을 부르기 전에 우리가 아는 사유로 거절한다.
        if (profile.account().isMissing()) {
            return PartnerResult.failed(PayoutBlockReason.ACCOUNT_MISSING.name(), "등록 계좌 없음");
        }
        Optional<Bank> bank = PortOneBanks.byCode(profile.account().bankCode());
        if (bank.isEmpty()) {
            return PartnerResult.failed(PortOneFailureClassifier.UNSUPPORTED_BANK,
                    "포트원 미지원 은행 — " + profile.account().bankName() + "(" + profile.account().bankCode() + ")");
        }
        if (profile.business() != null && isBlank(profile.business().registrationNumber())) {
            return PartnerResult.failed(PortOneFailureClassifier.PARTNER_NOT_READY, "사업자등록번호 없음");
        }
        if (profile.business() == null && isBlank(profile.birthdate())) {
            return PartnerResult.failed(PortOneFailureClassifier.PARTNER_NOT_READY, "원천징수 대상자의 생년월일 없음");
        }
        if (isBlank(profile.contact().phoneNumber()) || isBlank(profile.contact().email())) {
            return PartnerResult.failed(PortOneFailureClassifier.PARTNER_NOT_READY, "연락처(전화 · 이메일) 없음");
        }
        try {
            // ① 예금주 — 우리 예금주와 다르면 등록하지 않는다(지급일에 터지는 것보다 지금 막는 쪽이 낫다).
            PlatformAccountHolder holder = calls.await(client.getAccount().getPlatformAccountHolder(bank.get(),
                    profile.account().accountNumber(), test, profile.birthdate(),
                    profile.business() == null ? null : profile.business().registrationNumber()), "계좌 예금주 조회");
            if (holder.getHolderName() != null && profile.account().holder() != null
                    && !normalize(holder.getHolderName()).equals(normalize(profile.account().holder()))) {
                return PartnerResult.failed(PortOneFailureClassifier.ACCOUNT_HOLDER_MISMATCH,
                        "예금주 불일치 — 은행 「%s」 · 회원 정보 「%s」".formatted(holder.getHolderName(), profile.account().holder()));
            }
            // ② 사업자 상태
            String companyVerificationId = null;
            if (profile.business() != null) {
                GetPlatformCompanyStatePayload company = calls.await(client.getCompany()
                        .getPlatformCompanyState(profile.business().registrationNumber(), test), "사업자 상태 조회");
                String status = company.getCompanyState().getBusinessStatus().getValue();
                if (!IN_BUSINESS.equals(status)) {
                    return PartnerResult.failed(PortOneFailureClassifier.COMPANY_NOT_IN_BUSINESS,
                            "사업자 상태 " + status + " — " + profile.business().registrationNumber());
                }
                companyVerificationId = company.getCompanyVerificationId();
            }
            // ③ 생성 또는 갱신
            PlatformPartner partner = profile.existing()
                    ? update(profile, bank.get(), holder.getAccountVerificationId(), companyVerificationId)
                    : create(profile, bank.get(), holder.getAccountVerificationId(), companyVerificationId);
            return toResult(partner);
        } catch (PaymentGatewayRejectedException e) {
            log.warn("포트원 파트너 등록 거절 - partnerId: {}, {}", profile.partnerId(), e.getMessage());
            return PartnerResult.failed(PortOneFailureClassifier.partnerFailCode(e), e.getMessage());
        }
    }

    private PlatformPartner create(PartnerProfile profile, Bank bank, String accountVerificationId,
                                   String companyVerificationId) {
        CreatePlatformPartnerBodyType type = profile.business() != null
                ? new CreatePlatformPartnerBodyType(new CreatePlatformPartnerBodyTypeBusiness(
                profile.business().companyName(), PlatformPartnerTaxationType.Normal.INSTANCE,
                profile.business().registrationNumber(), profile.business().representativeName(),
                profile.business().address(), profile.business().businessType(), null, companyVerificationId), null, null)
                : new CreatePlatformPartnerBodyType(null, new CreatePlatformPartnerBodyTypeWhtPayer(profile.birthdate()), null);
        try {
            return calls.await(client.getPartner().createPlatformPartner(test, profile.partnerId(), profile.name(),
                    new CreatePlatformPartnerBodyContact(profile.contact().name(), profile.contact().phoneNumber(),
                            profile.contact().email()),
                    new CreatePlatformPartnerBodyAccount(bank, Currency.Krw.INSTANCE, profile.account().accountNumber(),
                            profile.account().holder(), accountVerificationId),
                    null, "SHOWROOMZ " + profile.payee().getLabel(), tags(profile.payee(), profile.business() != null),
                    type, null), "파트너 생성").getPartner();
        } catch (PaymentGatewayRejectedException e) {
            if (e.getCause() instanceof PlatformPartnerIdAlreadyExistsException) {
                // 우리 DB 에 연결이 없었을 뿐 포트원에는 있다(이전 시도의 커밋 실패) — 갱신으로 맞춘다.
                return update(profile, bank, accountVerificationId, companyVerificationId);
            }
            throw e;
        }
    }

    private PlatformPartner update(PartnerProfile profile, Bank bank, String accountVerificationId,
                                   String companyVerificationId) {
        try {
            return doUpdate(profile, bank, accountVerificationId, companyVerificationId);
        } catch (PaymentGatewayRejectedException e) {
            if (e.getCause() instanceof PlatformPartnerNotFoundException && profile.existing()) {
                // 우리 DB 에는 연결이 있는데 포트원에는 없다(모드 전환 · 콘솔 삭제) — 만든다.
                return create(new PartnerProfile(profile.payee(), profile.refId(), profile.partnerId(), profile.name(),
                        profile.contact(), profile.account(), profile.business(), profile.birthdate(), false), bank,
                        accountVerificationId, companyVerificationId);
            }
            throw e;
        }
    }

    private PlatformPartner doUpdate(PartnerProfile profile, Bank bank, String accountVerificationId,
                                     String companyVerificationId) {
        UpdatePlatformPartnerBodyType type = profile.business() != null
                ? new UpdatePlatformPartnerBodyType(new UpdatePlatformPartnerBodyTypeBusiness(
                profile.business().companyName(), PlatformPartnerTaxationType.Normal.INSTANCE,
                profile.business().registrationNumber(), profile.business().representativeName(),
                profile.business().address(), profile.business().businessType(), null, companyVerificationId), null, null)
                : new UpdatePlatformPartnerBodyType(null, new UpdatePlatformPartnerBodyTypeWhtPayer(profile.birthdate()), null);
        return calls.await(client.getPartner().updatePlatformPartner(profile.partnerId(), test, profile.name(),
                new UpdatePlatformPartnerBodyContact(profile.contact().name(), profile.contact().phoneNumber(),
                        profile.contact().email()),
                new UpdatePlatformPartnerBodyAccount(bank, Currency.Krw.INSTANCE, profile.account().accountNumber(),
                        profile.account().holder(), accountVerificationId),
                null, null, null, type, null), "파트너 갱신").getPartner();
    }

    private static PartnerResult toResult(PlatformPartner partner) {
        String status = partner.getStatus() == null ? null : partner.getStatus().getValue();
        if ("APPROVED".equals(status)) {
            return PartnerResult.approved();
        }
        if ("PENDING".equals(status)) {
            return new PartnerResult(PartnerStatus.PENDING, PortOneFailureClassifier.PARTNER_NOT_READY, "포트원 파트너 심사 중");
        }
        return new PartnerResult(PartnerStatus.REJECTED, PortOneFailureClassifier.PARTNER_NOT_READY,
                "포트원 파트너 상태 " + status);
    }

    private static List<String> tags(SettlementPayee payee, boolean business) {
        return payee == SettlementPayee.BRAND ? List.of("brand") : List.of("creator", business ? "business" : "individual");
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
