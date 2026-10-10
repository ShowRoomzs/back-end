package showroomz.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import showroomz.global.config.SettlementPlatformConfig;
import showroomz.global.config.properties.SettlementProperties;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SHOWROOMZ 사업자 정보 기동 검증(44 어드민 설계서 9-1 · 구현 계획서 단계 0 게이트 ①) — 운영 프로필은 더미 값으로 뜨지 않는다. */
@DisplayName("정산 플랫폼 정보 — 운영 프로필 더미 · 빈 값 금지")
class SettlementPlatformConfigTest {

    @Test
    @DisplayName("비운영 프로필 — 더미 값으로 뜬다")
    void dummyOutsideProd() {
        assertThatCode(() -> new SettlementPlatformConfig(properties("SHOWROOMZ TEST", "000-00-00000"),
                new MockEnvironment())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("운영 프로필 — 이름에 TEST · 더미 사업자번호 · 빈 값이면 기동 실패")
    void dummyInProdFails() {
        assertThatThrownBy(() -> new SettlementPlatformConfig(properties("SHOWROOMZ TEST", "123-45-67890"), prod()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("business-name");
        assertThatThrownBy(() -> new SettlementPlatformConfig(properties("주식회사 쇼룸즈", "000-00-00000"), prod()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("registration-number");
        assertThatThrownBy(() -> new SettlementPlatformConfig(properties("주식회사 쇼룸즈", " "), prod()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("운영 프로필 — 실제 값이면 뜬다")
    void realValuesInProd() {
        assertThatCode(() -> new SettlementPlatformConfig(properties("주식회사 쇼룸즈", "123-45-67890"), prod()))
                .doesNotThrowAnyException();
    }

    private static MockEnvironment prod() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }

    private static SettlementProperties properties(String name, String registrationNumber) {
        SettlementProperties properties = new SettlementProperties();
        SettlementProperties.Platform platform = properties.getPlatform();
        platform.setBusinessName(name);
        platform.setRepresentative("김대표");
        platform.setRegistrationNumber(registrationNumber);
        platform.setAddress("서울시 성동구 성수이로 1");
        platform.setTaxEmail("tax@showroomz.shop");
        return properties;
    }
}
