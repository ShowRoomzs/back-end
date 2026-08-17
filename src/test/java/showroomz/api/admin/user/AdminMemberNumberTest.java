package showroomz.api.admin.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** §25-3 회원번호 — 표기와 검색 축 판별. */
class AdminMemberNumberTest {

    @Test
    @DisplayName("회원 ID를 CST- 접두사로 포맷한다")
    void formatsUserId() {
        assertThat(AdminMemberNumber.format(88231L)).isEqualTo("CST-88231");
        assertThat(AdminMemberNumber.format(null)).isNull();
    }

    @Test
    @DisplayName("CST- 로 시작하는 검색어만 회원번호 축이다")
    void detectsMemberNumberAxis() {
        assertThat(AdminMemberNumber.looksLikeMemberNumber("CST-88231")).isTrue();
        assertThat(AdminMemberNumber.looksLikeMemberNumber("cst-88231")).isTrue();
        assertThat(AdminMemberNumber.looksLikeMemberNumber("88231")).isFalse();
        assertThat(AdminMemberNumber.looksLikeMemberNumber("딸기라떼")).isFalse();
        assertThat(AdminMemberNumber.looksLikeMemberNumber(null)).isFalse();
    }

    @Test
    @DisplayName("접두사 뒤가 숫자가 아니면 null이다 — 호출부는 이걸 0건으로 다뤄야 한다")
    void parsesOnlyDigits() {
        assertThat(AdminMemberNumber.parseOrNull("CST-88231")).isEqualTo(88231L);
        assertThat(AdminMemberNumber.parseOrNull("cst- 88231 ")).isEqualTo(88231L);
        assertThat(AdminMemberNumber.parseOrNull("CST-88231a")).isNull();
        assertThat(AdminMemberNumber.parseOrNull("CST-")).isNull();
        assertThat(AdminMemberNumber.parseOrNull("88231")).isNull();
    }
}
