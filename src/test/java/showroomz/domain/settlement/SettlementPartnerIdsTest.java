package showroomz.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.settlement.service.SettlementPartnerIds;
import showroomz.domain.settlement.type.SettlementPayee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PG 파트너 · 정산건 id 규칙(포트원 설계서 3-3) — 결정적이라 재시도가 멱등하고, 테스트 모드는 접두로 갈린다. */
@DisplayName("PG 파트너 · 정산건 식별자")
class SettlementPartnerIdsTest {

    @Test
    @DisplayName("파트너 id — brand-{marketId} · creator-{creatorId} · 테스트 접두 t- · 플랫폼은 예외")
    void partnerIds() {
        assertThat(SettlementPartnerIds.partnerId("", SettlementPayee.BRAND, 12L)).isEqualTo("brand-12");
        assertThat(SettlementPartnerIds.partnerId("t-", SettlementPayee.CREATOR, 7L)).isEqualTo("t-creator-7");
        assertThatThrownBy(() -> SettlementPartnerIds.partnerId("", SettlementPayee.PLATFORM, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정산건 id — {번호}-{수취자}-{회차} · 같은 행 재지시는 같은 id · 재분배는 새 id")
    void transferIds() {
        assertThat(SettlementPartnerIds.transferId("", "STL-2610-006", SettlementPayee.BRAND, 0))
                .isEqualTo("STL-2610-006-BRAND-0")
                .isEqualTo(SettlementPartnerIds.transferId("", "STL-2610-006", SettlementPayee.BRAND, 0));
        assertThat(SettlementPartnerIds.transferId("t-", "STL-2610-006", SettlementPayee.CREATOR, 1))
                .isEqualTo("t-STL-2610-006-CREATOR-1");
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
