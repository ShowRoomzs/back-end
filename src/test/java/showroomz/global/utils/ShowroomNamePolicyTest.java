package showroomz.global.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §22-1 쇼룸명 규칙 — 2~20자, 한글·영문·숫자·공백만.
 *
 * <p>이 규칙을 가입 온보딩과 쇼룸 관리(#8)가 함께 쓴다. 두 곳이 갈라지면 가입 때 통과한 이름이
 * 수정 화면에서 거부되므로, 규칙이 한 곳이라는 사실을 경계값으로 고정한다.
 */
class ShowroomNamePolicyTest {

    @Test
    @DisplayName("한글·영문·숫자·공백 조합은 허용한다")
    void allowedCharactersPass() {
        assertThat(ShowroomNamePolicy.isValidFormat("소연 뷰티")).isTrue();
        assertThat(ShowroomNamePolicy.isValidFormat("Soyeon Beauty")).isTrue();
        assertThat(ShowroomNamePolicy.isValidFormat("소연2호점")).isTrue();
    }

    @Test
    @DisplayName("2자와 20자는 경계 안이다")
    void boundaryLengthsPass() {
        assertThat(ShowroomNamePolicy.isValidFormat("소연")).isTrue();
        assertThat(ShowroomNamePolicy.isValidFormat("가".repeat(20))).isTrue();
    }

    @Test
    @DisplayName("1자와 21자는 경계 밖이다")
    void outOfBoundaryLengthsFail() {
        assertThat(ShowroomNamePolicy.isValidFormat("소")).isFalse();
        assertThat(ShowroomNamePolicy.isValidFormat("가".repeat(21))).isFalse();
    }

    /** 쇼룸명은 검색 대상이고 URL·표시에 함께 쓰이므로 특수문자를 받지 않는다. */
    @Test
    @DisplayName("특수문자는 거부한다")
    void specialCharactersFail() {
        assertThat(ShowroomNamePolicy.isValidFormat("소연_뷰티")).isFalse();
        assertThat(ShowroomNamePolicy.isValidFormat("소연@뷰티")).isFalse();
        assertThat(ShowroomNamePolicy.isValidFormat("소연-뷰티")).isFalse();
        assertThat(ShowroomNamePolicy.isValidFormat("소연.뷰티")).isFalse();
    }

    @Test
    @DisplayName("이모지도 거부한다")
    void emojiFails() {
        assertThat(ShowroomNamePolicy.isValidFormat("소연💄")).isFalse();
    }

    /** 개별 글자는 허용되지만 줄바꿈은 정규식의 문자 집합 밖이다 — 목록 표시가 깨진다. */
    @Test
    @DisplayName("줄바꿈이 섞인 이름은 거부한다")
    void newlineFails() {
        assertThat(ShowroomNamePolicy.isValidFormat("소연\n뷰티")).isFalse();
    }

    @Test
    @DisplayName("빈 값과 null은 거부한다")
    void blankAndNullFail() {
        assertThat(ShowroomNamePolicy.isValidFormat(null)).isFalse();
        assertThat(ShowroomNamePolicy.isValidFormat("")).isFalse();
    }

    /**
     * 공백만으로 된 두 글자는 정규식을 통과한다 — 형식 검사의 책임 범위가 여기까지라는 뜻이다.
     * 실제로 막는 것은 서비스의 중복·trim 검사이므로, 이 경계를 알고 있다는 사실을 남겨 둔다.
     */
    @Test
    @DisplayName("공백만으로 된 이름은 형식 검사만으로는 걸러지지 않는다 — 서비스가 함께 막는다")
    void whitespaceOnlyPassesFormatCheckByDesign() {
        assertThat(ShowroomNamePolicy.isValidFormat("  ")).isTrue();
    }
}
