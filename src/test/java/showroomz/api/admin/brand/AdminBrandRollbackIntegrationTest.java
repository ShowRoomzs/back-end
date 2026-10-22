package showroomz.api.admin.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.member.brand.service.BrandWithdrawalPurger;
import showroomz.domain.message.service.MessageSanctionChannelGateway;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 52 테스트 시나리오 SP-06 · WA-08 — 조치는 한 트랜잭션이다. 운영팀 카드 · 파기 중 하나가 실패하면 정지 행 · 회원 상태 · 지급 보류 · 이력 ·
 * 파기가 전부 롤백된다. 스파이 빈이 컨텍스트를 갈라 놓으므로 이 두 케이스만 따로 둔다.
 */
@DisplayName("어드민 브랜드 조치 롤백 — 카드 실패 · 파기 실패(52 시나리오 SP-06 · WA-08)")
class AdminBrandRollbackIntegrationTest extends AdminBrandTestSupport {

    @MockitoSpyBean private MessageSanctionChannelGateway channelGateway;
    @MockitoSpyBean private BrandWithdrawalPurger purger;

    @Test
    @DisplayName("SP-06 운영팀 카드가 실패하면 정지 전체가 롤백 — 정지 행 · 상태 · 지급 보류 · 이력 없음")
    void suspensionRollsBackWithCard() throws Exception {
        Settlement s = seedSettlement("롤백 정산", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        setPayout(s.getId(), SettlementPayee.BRAND, PayoutStatus.SCHEDULED, 900_000, null, LocalDate.now().plusDays(3));
        // 트랜잭션 프록시를 거치지 않도록 실제 대상 객체에 스텁한다.
        doThrow(new IllegalStateException("카드 등록 실패(주입)")).when(target(channelGateway))
                .postSuspendedCard(any(), any(), any(), any(), any(), any(), any());

        int status = suspend(brand.marketId(), "SETTLEMENT_INFO_MISMATCH", null).andReturn().getResponse().getStatus();
        assertThat(status).isGreaterThanOrEqualTo(500);

        assertThat(sanctionRepository.findAll()).isEmpty();
        assertThat(marketStatus(brand.marketId())).isEqualTo(MarketStatus.ACTIVE);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.PAYOUT_BLOCKED);
        assertThat(textsAt(json(adminGet(base() + "/histories")).path("content"), "type")).doesNotContain("SUSPENDED");
    }

    @Test
    @DisplayName("WA-08 파기가 실패하면 승인 전체가 롤백 — 신청 대기 · 상태 · 셀러 원본 · 열린 정지 · 상품 진열 그대로")
    void approvalRollsBackWithPurge() throws Exception {
        BrandFixture.Brand owner = cleanBrand("파기실패");
        Long productId = createProduct(owner, "롤백 상품", 10_000).getProductId();
        suspend(owner.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        Long requestId = withdrawalService.request(owner.marketId(), null, null, LocalDateTime.now()).getId();
        doThrow(new IllegalStateException("파기 실패(주입)")).when(target(purger)).purge(any(), any(), any());

        int status = approve(owner.marketId(), requestId).andReturn().getResponse().getStatus();
        assertThat(status).isGreaterThanOrEqualTo(500);

        assertThat(withdrawalRepository.findById(requestId).orElseThrow().isPending()).isTrue();
        assertThat(marketStatus(owner.marketId())).isEqualTo(MarketStatus.SUSPENDED);
        assertThat(sellerRepository.findById(owner.seller().getId()).orElseThrow().getEmail()).isEqualTo(owner.email());
        assertThat(sanctionRepository.findByMarketIdAndReleasedAtIsNull(owner.marketId())).isPresent();
        assertThat(jdbc.queryForObject("SELECT display_status FROM product WHERE product_id = ?", String.class, productId))
                .isEqualTo("DISPLAY");
        assertThat(textsAt(json(adminGet(base(owner.marketId()) + "/histories")).path("content"), "type"))
                .doesNotContain("WITHDRAWAL_APPROVED");
    }

    private static <T> T target(T bean) {
        return org.springframework.test.util.AopTestUtils.getUltimateTargetObject(bean);
    }
}
