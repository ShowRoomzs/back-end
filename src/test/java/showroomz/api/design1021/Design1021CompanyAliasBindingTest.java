package showroomz.api.design1021;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import showroomz.global.config.SettlementPlatformConfig;
import showroomz.global.config.properties.CompanyProperties;
import showroomz.global.config.properties.SettlementProperties;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 1021 설계서 6-5 · 10-1(기-35) — 운영자 사업자 정보는 {@code company.*} 한 출처이고 정산의 {@code settlement.platform.*} 는 그 별칭이다.
 * 실제 {@code application.yml}을 읽어 바인딩한다(기존 {@code SETTLEMENT_PLATFORM_*} 환경변수는 계속 덮어쓴다).
 */
@DisplayName("1021 기-35 — company.* 단일 출처 · settlement.platform.* 별칭 · 옛 환경변수 우선 · prod 런칭 게이트 더미 차단")
class Design1021CompanyAliasBindingTest {

    @Test
    @DisplayName("기본값 — 정산 공급받는자 = 공용 사업자 정보(상호 · 대표 · 등록번호 · 주소 · 수신 이메일)")
    void platformFollowsCompanyDefaults() throws IOException {
        Binder binder = binder(Map.of());
        CompanyProperties company = binder.bind("company", CompanyProperties.class).get();
        SettlementProperties.Platform platform = binder.bind("settlement", SettlementProperties.class).get().getPlatform();

        assertThat(company.getName()).isEqualTo("쇼룸즈");
        assertThat(platform.getBusinessName()).isEqualTo(company.getName());
        assertThat(platform.getRepresentative()).isEqualTo(company.getRepresentative());
        assertThat(platform.getRegistrationNumber()).isEqualTo(company.getRegistrationNumber()).isEqualTo("791-12-03353");
        assertThat(platform.getAddress()).isEqualTo(company.getAddress());
        assertThat(platform.getTaxEmail()).isEqualTo(company.getTaxEmail());
        assertThat(company.getMailOrderReportNumber()).isBlank();
    }

    @Test
    @DisplayName("company.* 를 바꾸면 정산 공급받는자도 따라간다")
    void companyOverrideFlowsToSettlement() throws IOException {
        Binder binder = binder(Map.of("COMPANY_NAME", "주식회사 쇼룸즈", "COMPANY_TAX_EMAIL", "tax@showroomz.shop"));
        SettlementProperties.Platform platform = binder.bind("settlement", SettlementProperties.class).get().getPlatform();

        assertThat(platform.getBusinessName()).isEqualTo("주식회사 쇼룸즈");
        assertThat(platform.getTaxEmail()).isEqualTo("tax@showroomz.shop");
    }

    @Test
    @DisplayName("옛 SETTLEMENT_PLATFORM_* 환경변수가 있으면 그 값이 이긴다(1 릴리스 호환)")
    void legacyEnvWins() throws IOException {
        Binder binder = binder(Map.of("COMPANY_NAME", "쇼룸즈", "SETTLEMENT_PLATFORM_BUSINESS_NAME", "옛 상호"));

        assertThat(binder.bind("settlement", SettlementProperties.class).get().getPlatform().getBusinessName())
                .isEqualTo("옛 상호");
        assertThat(binder.bind("company", CompanyProperties.class).get().getName()).isEqualTo("쇼룸즈");
    }

    @Test
    @DisplayName("prod · 런칭 게이트 — 기본 수신 이메일(test 포함)은 기동을 막고, 실제 값이면 뜬다")
    void launchGateRejectsDummyCompanyInfo() throws IOException {
        SettlementProperties dummy = binder(Map.of("settlement.launch-gate-enabled", "true"))
                .bind("settlement", SettlementProperties.class).get();
        assertThatThrownBy(() -> new SettlementPlatformConfig(dummy, prod()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tax-email");

        SettlementProperties real = binder(Map.of("settlement.launch-gate-enabled", "true",
                "COMPANY_TAX_EMAIL", "tax@showroomz.shop")).bind("settlement", SettlementProperties.class).get();
        assertThatCode(() -> new SettlementPlatformConfig(real, prod())).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------

    private static Binder binder(Map<String, Object> overrides) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        yaml.forEach(source -> environment.getPropertySources().addLast(source));
        environment.getPropertySources().addFirst(new MapPropertySource("overrides", overrides));
        return Binder.get(environment);
    }

    private static MockEnvironment prod() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }
}
