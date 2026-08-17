package showroomz.api.admin.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §25-1 목록 마스킹 — 이름 가운데 1자 · 휴대폰 가운데 4자리.
 *
 * <p>C15 내 정보 화면의 규칙(끝 글자)과 다르다는 점이 이 테스트의 요지다. 두 규칙을 한 유틸로
 * 합치는 리팩터링이 들어오면 여기가 먼저 깨진다.
 */
class AdminUserMaskerTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "홍길동, 홍*동",
            "박지은, 박*은",
            "김민, 김*",
            "김, *",
            "남궁민수, 남**수"
    })
    @DisplayName("이름은 첫 글자와 끝 글자만 남기고 가운데를 가린다")
    void maskName(String raw, String masked) {
        assertThat(AdminUserMasker.maskName(raw)).isEqualTo(masked);
    }

    @Test
    @DisplayName("이름이 없으면 null이다 — 가릴 값이 없는 것과 가려진 값은 다르다")
    void maskNameOfBlankIsNull() {
        assertThat(AdminUserMasker.maskName(null)).isNull();
        assertThat(AdminUserMasker.maskName("  ")).isNull();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "01012341234, 010-****-1234",
            "010-1234-1234, 010-****-1234"
    })
    @DisplayName("휴대폰은 저장 형태와 무관하게 뒤 4자리만 남는다 — 검색 축도 이 4자리다")
    void maskPhoneNumber(String raw, String masked) {
        assertThat(AdminUserMasker.maskPhoneNumber(raw)).isEqualTo(masked);
    }
}
