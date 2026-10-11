package showroomz.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import showroomz.global.config.properties.SettlementProperties;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * SHOWROOMZ 사업자 정보 기동 검증(44 어드민 설계서 9-1 · 13절 신규 #6 · 구현 계획서 단계 0 게이트 ①) — 세금계산서 공급받는자 ·
 * 원천징수영수증 발행자다. <b>런칭 게이트가 켜진</b> 운영 프로필({@code prod} · {@code settlement.launch-gate-enabled=true})에서
 * 비었거나 더미 값(TEST · 000-00-00000)이면 <b>뜨지 않는다</b> — 더미 사업자 정보가 찍힌 증빙이 나가면 되돌릴 수 없다.
 * 개발 · QA · 게이트를 끈 개발 단계 {@code prod} 는 더미 값으로 뜬다(경고 로그).
 */
@Slf4j
@Configuration
public class SettlementPlatformConfig {

    private static final String DUMMY_REGISTRATION_NUMBER = "000-00-00000";

    public SettlementPlatformConfig(SettlementProperties properties, Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        if (properties.isLaunchGateEnabled()) {
            validate(properties.getPlatform());
        } else {
            log.warn("정산 런칭 게이트 꺼짐(settlement.launch-gate-enabled=false) — prod 지만 사업자 정보를 검증하지 않습니다. "
                    + "실제 돈이 오가는 운영 서버에서는 반드시 켜세요(44 포트원 설계서 9-4).");
        }
    }

    static void validate(SettlementProperties.Platform platform) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("business-name", platform.getBusinessName());
        values.put("representative", platform.getRepresentative());
        values.put("registration-number", platform.getRegistrationNumber());
        values.put("address", platform.getAddress());
        values.put("tax-email", platform.getTaxEmail());
        values.forEach((key, value) -> {
            if (value == null || value.isBlank() || value.toLowerCase(Locale.ROOT).contains("test")
                    || DUMMY_REGISTRATION_NUMBER.equals(value.trim())) {
                throw new IllegalStateException("settlement.platform." + key + " 가 비었거나 더미 값입니다 — "
                        + "운영 프로필(prod)은 SHOWROOMZ 실제 사업자 정보가 있어야 뜹니다(44 정산 설계서 9-1).");
            }
        });
    }
}
