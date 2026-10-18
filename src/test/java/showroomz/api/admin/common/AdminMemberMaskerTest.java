package showroomz.api.admin.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 어드민 회원 관리 마스킹(§25-1 · §52-1 · §53-2) — 세 화면이 같은 규칙을 쓴다.
 *
 * <p>C15 내 정보 화면의 규칙(이름 끝 글자 · 생일 월일)과 다르다는 점이 요지다. 두 규칙을 한 메서드로 합치는 리팩터링이 들어오면 여기가 먼저 깨진다.
 */
class AdminMemberMaskerTest {

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
        assertThat(AdminMemberMasker.maskName(raw)).isEqualTo(masked);
    }

    @Test
    @DisplayName("이름이 없으면 null이다 — 가릴 값이 없는 것과 가려진 값은 다르다")
    void maskNameOfBlankIsNull() {
        assertThat(AdminMemberMasker.maskName(null)).isNull();
        assertThat(AdminMemberMasker.maskName("  ")).isNull();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "01012341234, 010-****-1234",
            "010-1234-1234, 010-****-1234"
    })
    @DisplayName("휴대폰은 저장 형태와 무관하게 뒤 4자리만 남는다 — 검색 축도 이 4자리다")
    void maskPhone(String raw, String masked) {
        assertThat(AdminMemberMasker.maskPhone(raw)).isEqualTo(masked);
    }

    @Test
    @DisplayName("생년월일은 연도만 남긴다 · 해제 표기는 점 구분")
    void birthday() {
        assertThat(AdminMemberMasker.maskBirthdayYearOnly("1994-06-02")).isEqualTo("1994.**.**");
        assertThat(AdminMemberMasker.maskBirthdayYearOnly(null)).isNull();
        assertThat(AdminMemberMasker.formatBirthday("1994-06-02")).isEqualTo("1994.06.02");
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "110123456789|110*****6789",
            "3333-01-128842|333*-**-**8842",
            "110-1234-3841|110-****-3841",
            "12345678|123*5678",
            "1234567|***4567",
            "1234|1234"
    })
    @DisplayName("계좌번호는 숫자 앞 3 · 뒤 4 자리만 · 구분자 보존 · 7자리 이하는 뒤 4자리만 — 브랜드 · 인플루언서 상세가 같은 결과")
    void maskAccountNumber(String raw, String masked) {
        assertThat(AdminMemberMasker.maskAccountNumber(raw)).isEqualTo(masked);
    }

    @Test
    @DisplayName("계좌번호가 없으면 null")
    void maskAccountNumberOfBlankIsNull() {
        assertThat(AdminMemberMasker.maskAccountNumber(null)).isNull();
        assertThat(AdminMemberMasker.maskAccountNumber(" ")).isNull();
    }
}
