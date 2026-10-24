package showroomz.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.settlement.service.SettlementPartnerIds;
import showroomz.domain.settlement.type.SettlementPayee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PG 셀러 · 지급 참조 식별자 규칙(토스 설계서 3-4) — 결정적이라 재시도가 멱등하고, 테스트 모드는 접두로 갈린다. 토스 길이 제약을 지킨다. */
@DisplayName("PG 셀러 · 지급 참조 식별자")
class SettlementPartnerIdsTest {

    @Test
    @DisplayName("refSellerId — brand-{marketId} · creator-{creatorId} · 테스트 접두 t- · 7자 하한(brand-1) · 20자 초과 거절 · 플랫폼은 예외")
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
    @DisplayName("refPayoutId — {번호}-{수취자}-{회차} · 같은 행 재지시는 같은 ref · 재분배는 새 ref · 50자 초과 거절")
    void payoutRefs() {
        assertThat(SettlementPartnerIds.payoutRef("", "STL-2610-006", SettlementPayee.BRAND, 0))
                .isEqualTo("STL-2610-006-BRAND-0")
                .isEqualTo(SettlementPartnerIds.payoutRef("", "STL-2610-006", SettlementPayee.BRAND, 0));
        assertThat(SettlementPartnerIds.payoutRef("t-", "STL-2610-006", SettlementPayee.CREATOR, 1))
                .isEqualTo("t-STL-2610-006-CREATOR-1");
        assertThatThrownBy(() -> SettlementPartnerIds.payoutRef("", "X".repeat(45), SettlementPayee.CREATOR, 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("refPayoutId");
    }

    @Test
    @DisplayName("Idempotency-Key — 참조 id 에서 결정적(UUID) · 다른 ref 는 다른 키")
    void idempotencyKey() {
        String key = SettlementPartnerIds.idempotencyKey("STL-2610-006-BRAND-0");
        assertThat(key).hasSize(36).isEqualTo(SettlementPartnerIds.idempotencyKey("STL-2610-006-BRAND-0"));
        assertThat(SettlementPartnerIds.idempotencyKey("STL-2610-006-BRAND-1")).isNotEqualTo(key);
    }

    @Test
    @DisplayName("계좌 지문 — 같은 계좌는 같고 한 글자만 달라도 다르며 공백 · null 은 정규화된다")
    void accountHash() {
        String a = SettlementPartnerIds.accountHash("088", "110123456789", "글로우랩");
        assertThat(a).hasSize(64).isEqualTo(SettlementPartnerIds.accountHash(" 088", "110123456789 ", "글로우랩"));
        assertThat(SettlementPartnerIds.accountHash("088", "110123456780", "글로우랩")).isNotEqualTo(a);
        assertThat(SettlementPartnerIds.accountHash(null, null, null)).hasSize(64);
    }

    @Test
    @DisplayName("법인 판정 — 사업자번호 가운데 81~88 → 법인 · 그 밖 개인사업자 · 「법인」 문구면 법인 · 10자리 아니면 개인사업자")
    void corporate() {
        assertThat(SettlementPartnerIds.isCorporate("1208112345", null)).isTrue();
        assertThat(SettlementPartnerIds.isCorporate("1208812345", null)).isTrue();
        assertThat(SettlementPartnerIds.isCorporate("1200112345", null)).isFalse();
        assertThat(SettlementPartnerIds.isCorporate("1200112345", "법인사업자")).isTrue();
        assertThat(SettlementPartnerIds.isCorporate("12345", null)).isFalse();
        assertThat(SettlementPartnerIds.isCorporate(null, null)).isFalse();
    }
}
