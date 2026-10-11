package showroomz.domain.groupbuy.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("공구 이력 「계약 체결완료 · 서명자」 — 양측 중 나중에 서명한 쪽의 표시명")
class GroupBuyConcludedSignerTest {

    private static final LocalDateTime SIGNED = LocalDateTime.of(2026, 8, 14, 9, 30);

    @Test
    @DisplayName("인플루언서가 나중에 서명했으면 쇼룸명 · 같은 시각이어도 인플루언서(서명 순서 브랜드 → 인플루언서)")
    void creatorSignedLast() {
        assertThat(groupBuy(SIGNED.minusHours(3), SIGNED).concludedSignerName()).isEqualTo("뷰티_소연");
        assertThat(groupBuy(SIGNED, SIGNED).concludedSignerName()).isEqualTo("뷰티_소연");
    }

    @Test
    @DisplayName("브랜드가 나중에 서명했으면 브랜드명")
    void brandSignedLast() {
        assertThat(groupBuy(SIGNED, SIGNED.minusHours(1)).concludedSignerName()).isEqualTo("글로우랩");
    }

    @Test
    @DisplayName("서명 시각이 하나라도 비면 null")
    void missingSignature() {
        assertThat(groupBuy(null, SIGNED).concludedSignerName()).isNull();
        assertThat(groupBuy(SIGNED, null).concludedSignerName()).isNull();
    }

    private static GroupBuy groupBuy(LocalDateTime brandSignedAt, LocalDateTime creatorSignedAt) {
        Market market = new Market(null, "글로우랩", "02-1234-5678");
        Creator creator = Creator.builder().showroomName("뷰티_소연").build();
        Contract contract = Contract.builder().market(market).creator(creator)
                .brandSignedAt(brandSignedAt).creatorSignedAt(creatorSignedAt).build();
        return GroupBuy.builder().contract(contract).market(market).creator(creator).build();
    }
}
