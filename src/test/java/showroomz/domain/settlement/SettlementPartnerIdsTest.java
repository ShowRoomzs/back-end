package showroomz.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.settlement.service.SettlementPartnerIds;
import showroomz.domain.settlement.type.SettlementPayee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PG 참조 식별자 · 계좌 지문 — 결정적이라 재시도가 멱등하고, 테스트 모드는 접두로 갈린다. */
@DisplayName("PG 참조 식별자 · 계좌 지문")
class SettlementPartnerIdsTest {

    @Test
    @DisplayName("시뮬레이터 기본 참조 id — brand-{marketId} · creator-{creatorId} · 테스트 접두 t- · 7자 하한(brand-1) · 20자 초과 거절 · 플랫폼은 예외")
    void partnerRefs() {
        assertThat(SettlementPartnerIds.partnerRef("", SettlementPayee.BRAND, 12L)).isEqualTo("brand-12");
        assertThat(SettlementPartnerIds.partnerRef("", SettlementPayee.BRAND, 1L)).isEqualTo("brand-1").hasSize(7);
        assertThat(SettlementPartnerIds.partnerRef("t-", SettlementPayee.CREATOR, 7L)).isEqualTo("t-creator-7");
        assertThatThrownBy(() -> SettlementPartnerIds.partnerRef("", SettlementPayee.PLATFORM, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettlementPartnerIds.partnerRef("", SettlementPayee.CREATOR, 123_456_789_012_345L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("refSellerId");
    }

    @Test
    @DisplayName("계좌 지문 — 같은 계좌는 같고 한 글자만 달라도 다르며 공백 · null 은 정규화된다")
    void accountHash() {
        String a = SettlementPartnerIds.accountHash("088", "110123456789", "글로우랩");
        assertThat(a).hasSize(64).isEqualTo(SettlementPartnerIds.accountHash(" 088", "110123456789 ", "글로우랩"));
        assertThat(SettlementPartnerIds.accountHash("088", "110123456780", "글로우랩")).isNotEqualTo(a);
        assertThat(SettlementPartnerIds.accountHash(null, null, null)).hasSize(64);
    }
}
