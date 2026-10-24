package showroomz.domain.member.creator.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주민등록번호 형식 검사 · 마스킹 — 스튜디오 온보딩 시나리오 BZ-02 · BZ-04 · BZ-04b. 없는 날짜(BZ-05)는 기대값이 미결이라 고정하지 않는다.
 */
@DisplayName("주민등록번호 — 정규화 · 형식 · 마스킹")
class ResidentRegistrationNumberTest {

    @Test
    @DisplayName("[BZ-02] 하이픈 · 공백을 지운 13자리로 정규화한다")
    void normalizesSeparators() {
        assertThat(ResidentRegistrationNumber.normalize("900101-2345678")).isEqualTo("9001012345678");
        assertThat(ResidentRegistrationNumber.normalize("900101 2345678")).isEqualTo("9001012345678");
        assertThat(ResidentRegistrationNumber.normalize("9001012345678")).isEqualTo("9001012345678");
    }

    @Test
    @DisplayName("[BZ-04] 자릿수 · 문자 · 월 · 일 · 성별 자리가 틀리면 null")
    void rejectsInvalidFormats() {
        String[] invalids = {null, "", "900101-234567", "900101-23456789", "90010a-2345678",
                "900001-2345678", "901301-2345678", "900100-2345678", "900132-2345678",
                "900101-0345678", "900101-9345678"};
        for (String invalid : invalids) {
            assertThat(ResidentRegistrationNumber.normalize(invalid)).as(String.valueOf(invalid)).isNull();
        }
    }

    @Test
    @DisplayName("[BZ-04b] 월 · 일 경계와 성별 자리 1 · 8 은 통과 — 체크섬은 보지 않는다")
    void acceptsBoundaries() {
        for (String valid : new String[]{"900101-1000000", "901231-8999999", "000131-3000000", "900112-4123456"}) {
            assertThat(ResidentRegistrationNumber.normalize(valid)).as(valid).isNotNull();
        }
    }

    @Test
    @DisplayName("마스킹은 뒤 7자리를 성별 자리까지 가리고, 이전 규칙 저장값도 같은 표기로 읽는다")
    void masksWholeBackPart() {
        assertThat(ResidentRegistrationNumber.mask("9001012345678")).isEqualTo("900101-*******");
        assertThat(ResidentRegistrationNumber.display("900101-2******")).isEqualTo("900101-*******");
        assertThat(ResidentRegistrationNumber.display("900101-*******")).isEqualTo("900101-*******");
        assertThat(ResidentRegistrationNumber.display(null)).isNull();
        assertThat(ResidentRegistrationNumber.display("garbage")).isEqualTo("garbage");
    }
}
