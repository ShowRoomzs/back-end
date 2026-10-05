package showroomz.api.admin.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import showroomz.api.admin.thread.type.AdminChannelMemberStatus;
import showroomz.api.admin.thread.type.AdminChannelTab;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.member.user.type.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** 회원번호 · 탭 · 회원 상태 — 어드민 소통 스레드의 작은 규칙들(36 설계 2-3 · 3-1). */
class AdminChannelMemberNumberTest {

    @Test
    @DisplayName("브랜드는 BRD-{마켓 id}, 인플루언서는 INF-{크리에이터 id}다. id가 없으면 null이다")
    void formatsByTab() {
        assertThat(AdminChannelMemberNumber.format(AdminChannelTab.BRAND, 1017L)).isEqualTo("BRD-1017");
        assertThat(AdminChannelMemberNumber.format(AdminChannelTab.INFLUENCER, 3021L)).isEqualTo("INF-3021");
        assertThat(AdminChannelMemberNumber.format(AdminChannelTab.BRAND, null)).isNull();
    }

    @Test
    @DisplayName("접두사는 대소문자 · 앞뒤 공백을 가리지 않는다")
    void parsesCaseInsensitively() {
        assertThat(AdminChannelMemberNumber.parseOrNull(AdminChannelTab.BRAND, "brd-1017")).isEqualTo(1017L);
        assertThat(AdminChannelMemberNumber.parseOrNull(AdminChannelTab.BRAND, "  BRD- 1017 ")).isEqualTo(1017L);
        assertThat(AdminChannelMemberNumber.parseOrNull(AdminChannelTab.INFLUENCER, "Inf-3021")).isEqualTo(3021L);
    }

    @Test
    @DisplayName("다른 탭의 접두사는 회원번호로 보지 않는다 — 이름 검색으로 남는다")
    void otherTabPrefixIsNotMemberNumber() {
        assertThat(AdminChannelMemberNumber.looksLikeMemberNumber(AdminChannelTab.INFLUENCER, "BRD-1017")).isFalse();
        assertThat(AdminChannelMemberNumber.looksLikeMemberNumber(AdminChannelTab.BRAND, "INF-3021")).isFalse();
        assertThat(AdminChannelMemberNumber.looksLikeMemberNumber(AdminChannelTab.BRAND, "글로우랩")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BRD-", "BRD-abc", "BRD-12a", "BRD--1", "BRD-1.5", "BRD-99999999999999999999"})
    @DisplayName("접두사 뒤가 숫자가 아니거나 범위를 넘으면 null — 호출부는 「일치 없음」으로 다룬다")
    void invalidDigitsAreNull(String keyword) {
        assertThat(AdminChannelMemberNumber.looksLikeMemberNumber(AdminChannelTab.BRAND, keyword)).isTrue();
        assertThat(AdminChannelMemberNumber.parseOrNull(AdminChannelTab.BRAND, keyword)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "BR", "B"})
    @DisplayName("비었거나 접두사보다 짧은 검색어는 회원번호가 아니다")
    void blankIsNotMemberNumber(String keyword) {
        assertThat(AdminChannelMemberNumber.looksLikeMemberNumber(AdminChannelTab.BRAND, keyword)).isFalse();
        assertThat(AdminChannelMemberNumber.parseOrNull(AdminChannelTab.BRAND, keyword)).isNull();
    }

    @Test
    @DisplayName("탭은 운영팀 연결 타입과 1:1이다")
    void tabMapsToOperatorConnectionType() {
        assertThat(AdminChannelTab.BRAND.getConnectionType()).isEqualTo(ConnectionType.OPERATOR_MARKET);
        assertThat(AdminChannelTab.INFLUENCER.getConnectionType()).isEqualTo(ConnectionType.OPERATOR_CREATOR);
        assertThat(AdminChannelTab.of(ConnectionType.OPERATOR_MARKET)).isEqualTo(AdminChannelTab.BRAND);
        assertThat(AdminChannelTab.of(ConnectionType.OPERATOR_CREATOR)).isEqualTo(AdminChannelTab.INFLUENCER);
    }

    @Test
    @DisplayName("브랜드 · 인플루언서 상태가 한 축으로 맞춰지고, 탈퇴만 쓰기가 막힌다(정지는 사유를 설명할 수 있어야 한다)")
    void memberStatusAndWritable() {
        assertThat(AdminChannelMemberStatus.of(MarketStatus.ACTIVE)).isEqualTo(AdminChannelMemberStatus.ACTIVE);
        assertThat(AdminChannelMemberStatus.of(MarketStatus.DORMANT)).isEqualTo(AdminChannelMemberStatus.DORMANT);
        assertThat(AdminChannelMemberStatus.of(MarketStatus.SUSPENDED)).isEqualTo(AdminChannelMemberStatus.SUSPENDED);
        assertThat(AdminChannelMemberStatus.of(MarketStatus.WITHDRAWN)).isEqualTo(AdminChannelMemberStatus.WITHDRAWN);
        assertThat(AdminChannelMemberStatus.of(UserStatus.NORMAL)).isEqualTo(AdminChannelMemberStatus.ACTIVE);
        assertThat(AdminChannelMemberStatus.of(UserStatus.DORMANT)).isEqualTo(AdminChannelMemberStatus.DORMANT);
        assertThat(AdminChannelMemberStatus.of(UserStatus.SUSPENDED)).isEqualTo(AdminChannelMemberStatus.SUSPENDED);
        assertThat(AdminChannelMemberStatus.of(UserStatus.WITHDRAWN)).isEqualTo(AdminChannelMemberStatus.WITHDRAWN);
        assertThat(AdminChannelMemberStatus.of((MarketStatus) null)).isEqualTo(AdminChannelMemberStatus.ACTIVE);
        assertThat(AdminChannelMemberStatus.of((UserStatus) null)).isEqualTo(AdminChannelMemberStatus.ACTIVE);

        assertThat(AdminChannelMemberStatus.values())
                .filteredOn(s -> !s.isWritable())
                .containsExactly(AdminChannelMemberStatus.WITHDRAWN);
    }
}
