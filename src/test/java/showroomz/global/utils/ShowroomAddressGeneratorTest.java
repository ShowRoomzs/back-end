package showroomz.global.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShowroomAddressGeneratorTest {

    private static final java.util.function.Predicate<String> NOTHING_TAKEN = handle -> false;

    @Test
    @DisplayName("영문 쇼룸명은 소문자 핸들로, 공백은 밑줄로 바뀐다")
    void asciiNameBecomesHandle() {
        String handle = ShowroomAddressGenerator.generateUnique("Beauty Soyeon", NOTHING_TAKEN);

        assertThat(handle).isEqualTo("beauty_soyeon");
    }

    @Test
    @DisplayName("한글만으로 된 쇼룸명은 핸들로 옮길 글자가 없어 익명 핸들로 떨어진다")
    void koreanOnlyNameFallsBackToRandomHandle() {
        String handle = ShowroomAddressGenerator.generateUnique("뷰티 소연", NOTHING_TAKEN);

        assertThat(handle).startsWith("sr").hasSize(10);
    }

    @Test
    @DisplayName("이미 쓰이는 핸들이면 숫자 꼬리표를 붙여 비켜간다")
    void takenHandleGetsNumberedSuffix() {
        Set<String> taken = Set.of("beauty_soyeon", "beauty_soyeon_2");

        String handle = ShowroomAddressGenerator.generateUnique("beauty soyeon", taken::contains);

        assertThat(handle).isEqualTo("beauty_soyeon_3");
    }

    @Test
    @DisplayName("핸들은 32자를 넘지 않는다")
    void handleIsCapped() {
        String handle = ShowroomAddressGenerator.generateUnique("a".repeat(80), NOTHING_TAKEN);

        assertThat(handle).hasSize(32);
    }

    @Test
    @DisplayName("구분자만 남는 이름도 빈 핸들을 만들지 않는다")
    void separatorOnlyNameDoesNotProduceEmptyHandle() {
        String handle = ShowroomAddressGenerator.generateUnique("   -  ", NOTHING_TAKEN);

        assertThat(handle).startsWith("sr").hasSize(10);
    }
}
