package showroomz.global.payment.portone.identity;

import io.portone.sdk.server.common.Gender;
import io.portone.sdk.server.common.PgProvider;
import io.portone.sdk.server.common.PortOneVersion;
import io.portone.sdk.server.common.SelectedChannel;
import io.portone.sdk.server.common.SelectedChannelType;
import io.portone.sdk.server.identityverification.FailedIdentityVerification;
import io.portone.sdk.server.identityverification.IdentityVerification;
import io.portone.sdk.server.identityverification.IdentityVerificationFailure;
import io.portone.sdk.server.identityverification.IdentityVerificationOperator;
import io.portone.sdk.server.identityverification.IdentityVerificationRequestedCustomer;
import io.portone.sdk.server.identityverification.IdentityVerificationVerifiedCustomer;
import io.portone.sdk.server.identityverification.ReadyIdentityVerification;
import io.portone.sdk.server.identityverification.VerifiedIdentityVerification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.global.payment.portone.PaymentGatewayException;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SDK 응답 → 우리 모델 매핑(57 설계서 2-2 · 실호출 없음). SDK 객체를 직접 만들어 넣는다 — 응답 스키마의 정본은 jar 다(R-2).
 */
@DisplayName("[57 MP] 포트원 본인인증 게이트웨이 — SDK 객체 → 모델 매핑")
class PortOneIdentityV2GatewayTest {

    private static final Instant AT = Instant.parse("2026-10-24T01:03:12Z");
    private static final SelectedChannel CHANNEL = new SelectedChannel(SelectedChannelType.Test.INSTANCE, "ch-id",
            "channel-key-identity", "KCP 본인인증", PgProvider.KcpV2.INSTANCE, "mid");

    @Test
    @DisplayName("MP-01 VERIFIED — verifiedCustomer 전 필드 · 전화번호는 숫자만 · verifiedAt 은 KST")
    void verifiedIsMapped() {
        IdentityVerificationVerifiedCustomer customer = new IdentityVerificationVerifiedCustomer("cid", "홍길동",
                IdentityVerificationOperator.SktMvno.INSTANCE, "010-1234-5678", "1998-04-12", Gender.Female.INSTANCE, Boolean.FALSE, "CI-1", "DI-1");
        VerifiedIdentityVerification sdk = new VerifiedIdentityVerification("idv1", CHANNEL, customer, null, AT, AT, AT, AT,
                "pgtx-1", "{\"raw\":true}", PortOneVersion.V2.INSTANCE);

        IdentityVerificationResult result = PortOneIdentityV2Gateway.toModel(sdk);

        assertThat(result.status()).isEqualTo(IdentityVerificationResult.Status.VERIFIED);
        assertThat(result.id()).isEqualTo("idv1");
        assertThat(result.pgTxId()).isEqualTo("pgtx-1");
        assertThat(result.verifiedAt()).isEqualTo(LocalDateTime.of(2026, 10, 24, 10, 3, 12));
        IdentityVerificationResult.VerifiedCustomer c = result.customer();
        assertThat(c.name()).isEqualTo("홍길동");
        assertThat(c.birthDate()).isEqualTo("1998-04-12");
        assertThat(c.gender()).isEqualTo("FEMALE");
        assertThat(c.phoneNumber()).isEqualTo("01012345678");
        assertThat(c.operator()).isEqualTo("SKT_MVNO");
        assertThat(c.foreigner()).isFalse();
        assertThat(c.ci()).isEqualTo("CI-1");
        assertThat(c.di()).isEqualTo("DI-1");
        // CI · DI 는 toString 으로도 새지 않는다
        assertThat(c.toString()).doesNotContain("CI-1").doesNotContain("DI-1").doesNotContain("홍길동");
    }

    @Test
    @DisplayName("MP-02 VERIFIED 인데 선택 필드가 비어도(ci · operator · gender) null 로 매핑된다")
    void verifiedWithMissingOptionalFields() {
        IdentityVerificationVerifiedCustomer customer = new IdentityVerificationVerifiedCustomer(null, "홍길동",
                null, null, "1998-04-12", null, null, null, "DI-1");
        // pgTxId · pgRawResponse 는 SDK 에서 non-null 이다(생성자가 NPE) — 선택 필드는 customer 안의 것들만
        VerifiedIdentityVerification sdk = new VerifiedIdentityVerification("idv1", CHANNEL, customer, null, AT, AT, AT, AT,
                "pgtx-1", "{}", PortOneVersion.V2.INSTANCE);

        IdentityVerificationResult.VerifiedCustomer c = PortOneIdentityV2Gateway.toModel(sdk).customer();

        assertThat(c.ci()).isNull();
        assertThat(c.operator()).isNull();
        assertThat(c.gender()).isNull();
        assertThat(c.phoneNumber()).isNull();
        assertThat(c.foreigner()).isNull();
        assertThat(c.di()).isEqualTo("DI-1");
    }

    @Test
    @DisplayName("MP-03 FAILED — failure{reason · pgCode · pgMessage} · pgTxId")
    void failedIsMapped() {
        FailedIdentityVerification sdk = new FailedIdentityVerification("idv2", CHANNEL,
                new IdentityVerificationRequestedCustomer(null, null, null, null, null, null), null, AT, AT, AT,
                new IdentityVerificationFailure("PG_PROVIDER", "CS24", "정보조회가 올바르지 않습니다."), PortOneVersion.V2.INSTANCE, "pgtx-2");

        IdentityVerificationResult result = PortOneIdentityV2Gateway.toModel(sdk);

        assertThat(result.status()).isEqualTo(IdentityVerificationResult.Status.FAILED);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.customer()).isNull();
        assertThat(result.failure().pgCode()).isEqualTo("CS24");
        assertThat(result.failure().pgMessage()).isEqualTo("정보조회가 올바르지 않습니다.");
        assertThat(result.failure().reason()).isEqualTo("PG_PROVIDER");
        assertThat(result.pgTxId()).isEqualTo("pgtx-2");
    }

    @Test
    @DisplayName("MP-04 READY — 상태만")
    void readyIsMapped() {
        ReadyIdentityVerification sdk = new ReadyIdentityVerification("idv3", CHANNEL,
                new IdentityVerificationRequestedCustomer(null, null, null, null, null, null), null, AT, AT, AT,
                PortOneVersion.V2.INSTANCE, null);

        IdentityVerificationResult result = PortOneIdentityV2Gateway.toModel(sdk);

        assertThat(result.status()).isEqualTo(IdentityVerificationResult.Status.READY);
        assertThat(result.customer()).isNull();
        assertThat(result.failure()).isNull();
    }

    @Test
    @DisplayName("MP-05 Unrecognized — 새 SDK 가 모르는 상태를 주면 결과를 모르는 것으로(PaymentGatewayException)")
    void unrecognizedIsUnknown() {
        assertThatThrownBy(() -> PortOneIdentityV2Gateway.toModel(IdentityVerification.Unrecognized.INSTANCE))
                .isInstanceOf(PaymentGatewayException.class);
    }
}
