package showroomz.api.app.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 본인인증 판정식 · id 발급(57 설계서 10절 단위) — 통합 테스트가 못 보는 경계값만 본다.
 */
class IdentityVerificationServiceTest {

    @Nested
    @DisplayName("만 14세 판정")
    class MinAge {

        private final LocalDate today = LocalDate.of(2026, 10, 24);

        @Test
        @DisplayName("오늘이 14번째 생일이면 통과")
        void fourteenthBirthdayPasses() {
            assertThat(IdentityVerificationService.isUnderMinAge("2012-10-24", today)).isFalse();
        }

        @Test
        @DisplayName("14번째 생일 하루 전이면 차단")
        void dayBeforeFourteenthBirthdayIsBlocked() {
            assertThat(IdentityVerificationService.isUnderMinAge("2012-10-25", today)).isTrue();
        }

        @Test
        @DisplayName("해석할 수 없는 생년월일은 막지 않는다 — 인증사가 이미 걸렀다")
        void unparsableBirthDateIsNotBlocked() {
            assertThat(IdentityVerificationService.isUnderMinAge("19981204", today)).isFalse();
            assertThat(IdentityVerificationService.isUnderMinAge(null, today)).isFalse();
            assertThat(IdentityVerificationService.isUnderMinAge("", today)).isFalse();
        }
    }

    @Nested
    @DisplayName("identityVerificationId 발급 (R-4 — KCP: 영문·숫자 40자 이하)")
    class IdGenerator {

        @Test
        @DisplayName("idv + 26자 · 영문 대문자 · 숫자만 · 1만 회 전부 통과 · 중복 없음")
        void idsSatisfyKcpConstraint() {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 10_000; i++) {
                String id = IdentityVerificationIdGenerator.next();
                assertThat(id).startsWith("idv").hasSize(29);
                assertThat(IdentityVerificationIdGenerator.KCP_ALLOWED.matcher(id).matches()).as(id).isTrue();
                assertThat(seen.add(id)).as("중복 id: " + id).isTrue();
            }
        }

        @Test
        @DisplayName("앞 10자는 시각이라 나중 id 가 사전순으로 뒤다")
        void timePrefixIsSortable() {
            String earlier = IdentityVerificationIdGenerator.next(1_000_000L);
            String later = IdentityVerificationIdGenerator.next(2_000_000L);

            assertThat(later.substring(3, 13)).isGreaterThan(earlier.substring(3, 13));
        }
    }
}
