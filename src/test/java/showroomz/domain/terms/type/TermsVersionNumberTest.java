package showroomz.domain.terms.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TermsVersionNumberTest {

    @Test
    @DisplayName("숫자와 점만 허용한다 — 접두 v·공백·문자는 형식 위반이다")
    void validatesFormat() {
        assertThat(TermsVersionNumber.isValidFormat("1.0")).isTrue();
        assertThat(TermsVersionNumber.isValidFormat("3")).isTrue();
        assertThat(TermsVersionNumber.isValidFormat("3.2.1")).isTrue();

        assertThat(TermsVersionNumber.isValidFormat("v3.2")).isFalse();
        assertThat(TermsVersionNumber.isValidFormat("3.2 ")).isFalse();
        assertThat(TermsVersionNumber.isValidFormat("3.2b")).isFalse();
        assertThat(TermsVersionNumber.isValidFormat("3.")).isFalse();
        assertThat(TermsVersionNumber.isValidFormat("")).isFalse();
        assertThat(TermsVersionNumber.isValidFormat(null)).isFalse();
    }

    @Test
    @DisplayName("비교는 구간별 숫자로 한다 — 문자열 비교면 v3.10이 v3.9보다 작아진다")
    void comparesBySegment() {
        assertThat(TermsVersionNumber.of("3.10")).isGreaterThan(TermsVersionNumber.of("3.9"));
        assertThat(TermsVersionNumber.of("3.2")).isGreaterThan(TermsVersionNumber.of("3.1"));
        assertThat(TermsVersionNumber.of("4.0")).isGreaterThan(TermsVersionNumber.of("3.99"));
        assertThat(TermsVersionNumber.of("2.0")).isLessThan(TermsVersionNumber.of("10.0"));
    }

    @Test
    @DisplayName("없는 구간은 0으로 본다 — v3.1과 v3.1.0은 같은 값이다")
    void treatsMissingSegmentAsZero() {
        assertThat(TermsVersionNumber.of("3.1")).isEqualByComparingTo(TermsVersionNumber.of("3.1.0"));
        assertThat(TermsVersionNumber.of("3")).isEqualByComparingTo(TermsVersionNumber.of("3.0"));
        assertThat(TermsVersionNumber.of("3.1.1")).isGreaterThan(TermsVersionNumber.of("3.1"));
    }
}
