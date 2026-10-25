package showroomz.global.payment.portone.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import showroomz.global.payment.portone.PortOneProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 본인인증 게이트웨이 배선(57 테스트 시나리오 WR 절 · 설계서 6-1) — {@code portone.enabled} 와 {@code portone.identity.enabled} 두 스위치로
 * 실연동 · Fake 가 <b>정확히 하나</b> 뜬다. 결제는 운영 자격으로 켜 두고 본인인증만 Fake 로 둘 수 있어야 한다(채널 계약 지연 대비).
 *
 * <p>통합 테스트 컨텍스트는 {@code portone.enabled=false} 라 실연동 쪽 배선은 여기서만 본다.
 */
@DisplayName("[57 WR] 본인인증 게이트웨이 — 스위치별 빈 배선")
class PortOneIdentityGatewayWiringTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PortOneProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class, PortOneIdentityV2Gateway.class, FakeIdentityGateway.class);

    private static final String[] IDENTITY_KEYS = {
            "portone.store-id=store-x", "portone.api-secret=secret-x", "portone.channel-keys.identity=channel-identity-x"};

    @Test
    @DisplayName("WR-01 설정 없음(기본 enabled=true · identity.enabled=true) — 실연동을 고르고 키가 없으니 기동 실패")
    void defaultIsRealAndFailsWithoutKeys() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("portone.store-id");
        });
    }

    @Test
    @DisplayName("WR-02 두 스위치 켜짐 + 키 있음 — PortOneIdentityV2Gateway 하나만")
    void realWhenBothEnabled() {
        runner.withPropertyValues(IDENTITY_KEYS).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PortOneIdentityGateway.class);
            assertThat(context).getBean(PortOneIdentityGateway.class).isInstanceOf(PortOneIdentityV2Gateway.class);
            assertThat(context.getBean(PortOneIdentityGateway.class).channelKey()).isEqualTo("channel-identity-x");
        });
    }

    @Test
    @DisplayName("WR-03 portone.enabled=false(로컬 · CI) — Fake 하나만 · 키 없이 뜬다")
    void fakeWhenPortOneDisabled() {
        runner.withPropertyValues("portone.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PortOneIdentityGateway.class);
            assertThat(context).getBean(PortOneIdentityGateway.class).isInstanceOf(FakeIdentityGateway.class);
        });
    }

    @Test
    @DisplayName("WR-04 결제는 켜고 본인인증만 끔(identity.enabled=false) — Fake · 본인인증 채널 키 없이 뜬다")
    void fakeWhenOnlyIdentityDisabled() {
        runner.withPropertyValues("portone.enabled=true", "portone.identity.enabled=false",
                "portone.store-id=store-x", "portone.api-secret=secret-x").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PortOneIdentityGateway.class);
            assertThat(context).getBean(PortOneIdentityGateway.class).isInstanceOf(FakeIdentityGateway.class);
        });
    }

    @Test
    @DisplayName("WR-05 두 스위치 켜짐 · 본인인증 채널 키만 비어 있음 — 기동 실패 · 메시지가 키 이름과 끄는 방법을 알려 준다")
    void failsWithoutIdentityChannel() {
        runner.withPropertyValues("portone.store-id=store-x", "portone.api-secret=secret-x").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("portone.channel-keys.identity")
                    .hasMessageContaining("portone.identity.enabled=false");
        });
    }

    @Test
    @DisplayName("WR-06 identity 설정 기본값 — ttl 10분 · 중복 차단 켜짐(확정)")
    void identityDefaults() {
        runner.withPropertyValues("portone.enabled=false").run(context -> {
            PortOneProperties.Identity identity = context.getBean(PortOneProperties.class).getIdentity();
            assertThat(identity.isEnabled()).isTrue();
            assertThat(identity.getTtlMinutes()).isEqualTo(10);
            assertThat(identity.isDuplicateBlock()).isTrue();
        });
    }
}
