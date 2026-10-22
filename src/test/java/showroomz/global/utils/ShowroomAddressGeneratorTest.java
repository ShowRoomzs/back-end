package showroomz.global.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 쇼룸 주소 핸들 — 계정 아이디(인스타그램 핸들) 기준(스튜디오 온보딩 rev.3 [정정]). */
class ShowroomAddressGeneratorTest {

    private static final java.util.function.Predicate<String> NOTHING_TAKEN = handle -> false;

    @Test
    @DisplayName("계정 아이디가 그대로 핸들이 된다")
    void accountIdBecomesHandle() {
        String handle = ShowroomAddressGenerator.generateUnique("beauty_soyeon", NOTHING_TAKEN);

        assertThat(handle).isEqualTo("beauty_soyeon");
    }

    @Test
    @DisplayName("계정 아이디의 마침표는 밑줄로 바뀌고 앞의 @ 는 버린다")
    void periodBecomesUnderscoreAndAtIsDropped() {
        assertThat(ShowroomAddressGenerator.generateUnique("beauty.soyeon", NOTHING_TAKEN)).isEqualTo("beauty_soyeon");
        assertThat(ShowroomAddressGenerator.generateUnique("@beauty.soyeon", NOTHING_TAKEN)).isEqualTo("beauty_soyeon");
    }

    @Test
    @DisplayName("구분자가 앞뒤에 있거나 연달아 있어도 핸들에는 하나만 남는다")
    void separatorsAreCollapsed() {
        String handle = ShowroomAddressGenerator.generateUnique("_beauty..soyeon_", NOTHING_TAKEN);

        assertThat(handle).isEqualTo("beauty_soyeon");
    }

    @Test
    @DisplayName("대문자 · 공백이 섞인 값도 소문자 핸들로, 공백은 밑줄로 바뀐다")
    void asciiWithSpaceBecomesHandle() {
        String handle = ShowroomAddressGenerator.generateUnique("Beauty Soyeon", NOTHING_TAKEN);

        assertThat(handle).isEqualTo("beauty_soyeon");
    }

    @Test
    @DisplayName("핸들로 옮길 글자가 없으면 익명 핸들로 떨어진다")
    void nonAsciiFallsBackToRandomHandle() {
        String handle = ShowroomAddressGenerator.generateUnique("뷰티 소연", NOTHING_TAKEN);

        assertThat(handle).startsWith("sr").hasSize(10);
    }

    @Test
    @DisplayName("3자에 못 미치는 계정 아이디도 익명 핸들로 떨어진다")
    void tooShortAccountIdFallsBackToRandomHandle() {
        String handle = ShowroomAddressGenerator.generateUnique("ab", NOTHING_TAKEN);

        assertThat(handle).startsWith("sr").hasSize(10);
    }

    @Test
    @DisplayName("이미 쓰이는 핸들이면 숫자 꼬리표를 붙여 비켜간다")
    void takenHandleGetsNumberedSuffix() {
        Set<String> taken = Set.of("beauty_soyeon", "beauty_soyeon_2");

        String handle = ShowroomAddressGenerator.generateUnique("beauty.soyeon", taken::contains);

        assertThat(handle).isEqualTo("beauty_soyeon_3");
    }

    @Test
    @DisplayName("핸들은 32자를 넘지 않는다")
    void handleIsCapped() {
        String handle = ShowroomAddressGenerator.generateUnique("a".repeat(80), NOTHING_TAKEN);

        assertThat(handle).hasSize(32);
    }

    @Test
    @DisplayName("[RI-04 ⑥] 32자 핸들에 꼬리표가 붙으면 앞을 잘라 32자를 넘지 않는다")
    void suffixKeepsHandleWithinCap() {
        String base = "a".repeat(32);

        String handle = ShowroomAddressGenerator.generateUnique(base, Set.of(base)::contains);

        assertThat(handle).hasSize(32).isEqualTo("a".repeat(30) + "_2");
    }

    @Test
    @DisplayName("[RI-04 ⑧] 숫자 꼬리표 _2 ~ _50 이 모두 쓰이면 익명 핸들로 넘어간다")
    void exhaustedSuffixesFallBackToRandomHandle() {
        java.util.function.Predicate<String> taken = handle -> handle.startsWith("beauty_soyeon");

        String handle = ShowroomAddressGenerator.generateUnique("beauty.soyeon", taken);

        assertThat(handle).startsWith("sr").hasSize(10).matches("sr[a-km-z2-9]{8}");
    }

    @Test
    @DisplayName("구분자만 남는 값도 빈 핸들을 만들지 않는다")
    void separatorOnlyValueDoesNotProduceEmptyHandle() {
        String handle = ShowroomAddressGenerator.generateUnique("  ._-  ", NOTHING_TAKEN);

        assertThat(handle).startsWith("sr").hasSize(10);
    }
}
