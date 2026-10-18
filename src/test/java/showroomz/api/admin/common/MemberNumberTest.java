package showroomz.api.admin.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.member.common.type.MemberType;

import static org.assertj.core.api.Assertions.assertThat;

/** 회원번호 한 규칙(정합 계획서 R1-2) — 세 유형이 같은 포맷 · 같은 검색 축 판별을 쓴다. */
class MemberNumberTest {

    @Test
    @DisplayName("유형별 접두사로 id 를 포맷한다 · id 가 없으면 null")
    void format() {
        assertThat(MemberNumber.format(MemberType.CONSUMER, 88231L)).isEqualTo("CST-88231");
        assertThat(MemberNumber.format(MemberType.BRAND, 1042L)).isEqualTo("BRD-1042");
        assertThat(MemberNumber.format(MemberType.CREATOR, 3018L)).isEqualTo("INF-3018");
        assertThat(MemberNumber.format(MemberType.BRAND, null)).isNull();
    }

    @Test
    @DisplayName("검색 축 — 그 유형의 접두사만(대소문자 무시) · 접두사 뒤가 숫자가 아니면 null(= 일치 없음)")
    void parse() {
        assertThat(MemberNumber.parseOrNull(MemberType.BRAND, " brd-1042 ")).isEqualTo(1042L);
        assertThat(MemberNumber.parseOrNull(MemberType.BRAND, "BRD-abc")).isNull();
        assertThat(MemberNumber.looksLike(MemberType.BRAND, "BRD-abc")).isTrue();
        assertThat(MemberNumber.looksLike(MemberType.CREATOR, "BRD-1042")).isFalse();
        assertThat(MemberNumber.parseOrNull(MemberType.CREATOR, "BRD-1042")).isNull();
    }
}
