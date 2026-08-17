package showroomz.global.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 작성자 닉네임 마스킹 (§23-3) — 파트너센터는 가린 닉네임만 본다.
 *
 * <p>가리는 길이를 원본 길이에 맞추지 않는 것이 핵심이다. 남은 글자 수만큼 별을 찍으면
 * 닉네임 길이가 드러나 대조 단서가 된다 — 항상 고정 길이로 가린다.
 */
class NicknameMaskerTest {

    @Test
    @DisplayName("첫 글자만 남기고 가린다")
    void keepsOnlyFirstCharacter() {
        assertThat(NicknameMasker.mask("구름많음")).isEqualTo("구****");
    }

    @Test
    @DisplayName("가린 길이는 원본 길이와 무관하게 일정하다 — 길이가 단서가 되지 않는다")
    void maskLengthDoesNotLeakOriginalLength() {
        assertThat(NicknameMasker.mask("가나")).isEqualTo("가****");
        assertThat(NicknameMasker.mask("가나다라마바사아자차")).isEqualTo("가****");
    }

    @Test
    @DisplayName("한 글자 닉네임도 첫 글자는 남는다")
    void singleCharacterNicknameIsMasked() {
        assertThat(NicknameMasker.mask("가")).isEqualTo("가****");
    }

    @Test
    @DisplayName("앞뒤 공백은 다듬고 첫 글자를 고른다 — 공백이 첫 글자로 남지 않는다")
    void surroundingWhitespaceIsTrimmed() {
        assertThat(NicknameMasker.mask("  구름  ")).isEqualTo("구****");
    }

    @Test
    @DisplayName("영문·숫자도 같은 규칙으로 가린다")
    void asciiNicknameIsMasked() {
        assertThat(NicknameMasker.mask("mia123")).isEqualTo("m****");
    }

    /** 닉네임이 비어 있어도 파트너센터 목록에 빈 칸이 생기면 안 된다. */
    @Test
    @DisplayName("닉네임이 없으면 알 수 없음으로 표기한다")
    void missingNicknameFallsBack() {
        assertThat(NicknameMasker.mask(null)).isEqualTo("알 수 없음");
        assertThat(NicknameMasker.mask("")).isEqualTo("알 수 없음");
        assertThat(NicknameMasker.mask("   ")).isEqualTo("알 수 없음");
    }
}
