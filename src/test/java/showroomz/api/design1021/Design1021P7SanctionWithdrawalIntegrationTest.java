package showroomz.api.design1021;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.member.brand.entity.BrandSanction;
import showroomz.domain.member.brand.repository.BrandSanctionRepository;
import showroomz.domain.member.brand.service.BrandSanctionService;
import showroomz.domain.member.brand.type.SanctionReleaseReason;
import showroomz.domain.member.creator.service.CreatorSanctionService;
import showroomz.domain.member.creator.service.CreatorWithdrawalService;
import showroomz.domain.member.creator.type.CreatorSuspensionReason;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.ClaimReason;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 P7 — 계정 이용 정지 기간 · 자동 해제 · 이의 신청 기한(7-2) · 정지 중 탈퇴(7-3) · 소비자 탈퇴 차단(7-4).
 */
@DisplayName("1021 P7 — 정지 기간 · 자동 해제 · 정지 중 탈퇴 · 소비자 탈퇴 차단")
class Design1021P7SanctionWithdrawalIntegrationTest extends SettlementTestSupport {

    @Autowired private BrandSanctionService brandSanctionService;
    @Autowired private BrandSanctionRepository brandSanctions;
    @Autowired private CreatorSanctionService creatorSanctionService;
    @Autowired private CreatorWithdrawalService creatorWithdrawalService;

    @Test
    @DisplayName("기-02 — 종료일 지정 정지 · 계정 상태 기간 블록 · 지난 종료일 400 · 기간 중엔 그대로 · 다음 날 시스템 자동 해제(TERM_ENDED)")
    void brandTermAndAutoRelease() throws Exception {
        LocalDate today = LocalDate.now();
        adminPost("/v1/admin/brands/" + brand.marketId() + "/suspend",
                Map.of("reason", "PRODUCT_INFO_FALSIFIED", "endsOn", today.minusDays(1).toString()))
                .andExpect(status().isBadRequest());
        adminPost("/v1/admin/brands/" + brand.marketId() + "/suspend",
                Map.of("reason", "PRODUCT_INFO_FALSIFIED", "endsOn", today.plusDays(6).toString()))
                .andExpect(status().isOk())
                // 55 계획서 M5 — 어드민 상세의 정지 블록에도 같은 기간 블록이 실린다
                .andExpect(jsonPath("$.currentStatus.sanction.term.endsOn").value(today.plusDays(6).toString()))
                .andExpect(jsonPath("$.currentStatus.sanction.term.periodDays").value(7))
                .andExpect(jsonPath("$.currentStatus.sanction.term.appealDeadlineAt").exists());

        JsonNode account = json(sellerGet("/v1/seller/account-status").andExpect(status().isOk()));
        assertThat(account.at("/sanction/term/endsOn").asText()).isEqualTo(today.plusDays(6).toString());
        assertThat(account.at("/sanction/term/periodDays").asInt()).isEqualTo(7);
        assertThat(account.at("/sanction/term/autoReleaseAt").asText()).startsWith(today.plusDays(7).toString());
        assertThat(account.at("/sanction/term/appealDeadlineAt").isNull()).isFalse();
        assertThat(account.at("/sanction/term/appealReviewBusinessDays").isNull()).isTrue();

        // 운영자는 「정지 기간 종료」를 고를 수 없다.
        adminPost("/v1/admin/brands/" + brand.marketId() + "/release", Map.of("reason", "TERM_ENDED"))
                .andExpect(status().isBadRequest());

        assertThat(inTransaction(() -> brandSanctionService.releaseByTerm(brand.marketId(),
                today.plusDays(6).atTime(23, 59)))).isNull();
        assertThat(brandSanctions.findMemberIdsToAutoRelease(today.plusDays(7))).contains(brand.marketId());
        BrandSanction released = inTransaction(() -> brandSanctionService.releaseByTerm(brand.marketId(),
                today.plusDays(7).atTime(0, 5)));
        assertThat(released).isNotNull();

        BrandSanction stored = brandSanctions.findById(released.getId()).orElseThrow();
        assertThat(stored.getReleaseReason()).isEqualTo(SanctionReleaseReason.TERM_ENDED);
        assertThat(stored.isAutoReleased()).isTrue();
        assertThat(stored.getReleasedBy()).isNull();
        sellerGet("/v1/seller/account-status").andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.sanction").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM brand_admin_history WHERE market_id = ? "
                + "AND event_type = 'SUSPENSION_RELEASED' AND actor_type = 'SYSTEM'", Long.class, brand.marketId()))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("기-03 — 정지 중인 인플루언서도 탈퇴 신청이 정지로 막히지 않는다 · 「허위 기재」 사유는 지급 정지 아님")
    void suspendedCreatorMayRequestWithdrawal() {
        LocalDateTime now = LocalDateTime.now();
        var sanction = inTransaction(() -> creatorSanctionService.suspend(creator.getId(),
                CreatorSuspensionReason.FALSE_INFORMATION, null, now.toLocalDate().plusDays(3), operator.getId(),
                "운영자", now));
        assertThat(sanction.isPayoutHeld()).isFalse();
        assertThat(sanction.getEndsOn()).isEqualTo(now.toLocalDate().plusDays(3));

        try {
            inTransaction(() -> creatorWithdrawalService.request(creator.getId(), "활동 종료", now));
        } catch (BusinessException e) {
            // 진행 중 공구 · 정산 같은 탈퇴 조건은 그대로다 — 정지만 사유가 아니면 된다.
            assertThat(e.getErrorCode()).isNotEqualTo(ErrorCode.CREATOR_SUSPENDED);
        }
    }

    @Test
    @DisplayName("기-31 — 진행 중 반품이 있으면 탈퇴 불가 · blockers 분해 · 결제 전 주문은 막지 않는다")
    void consumerWithdrawalBlockers() throws Exception {
        purchaseUnpaid();
        withdrawal().andExpect(jsonPath("$.blockers.ongoingOrders").value(0))
                .andExpect(jsonPath("$.withdrawable").value(true));

        OrderDeliveryGroup group = deliveredFrom(purchase(creamVariant, 1).group());
        returnClaim(group, itemsOf(group).get(0), 1, ClaimReason.CHANGE_OF_MIND);
        // 구매확정 뒤 운영자가 연 반품과 같은 모양 — 진행 중 주문으로는 세지 않고 반품 사유로만 막는다.
        jdbc.update("UPDATE order_product SET status = 'PURCHASE_CONFIRMED' WHERE delivery_group_id = ?", group.getId());

        withdrawal().andExpect(jsonPath("$.withdrawable").value(false))
                .andExpect(jsonPath("$.blockers.ongoingOrders").value(0))
                .andExpect(jsonPath("$.blockers.openClaims").value(1))
                .andExpect(jsonPath("$.blockers.unpaidCharges").value(0))
                .andExpect(jsonPath("$.blockers.pendingTransfers").value(0));
    }

    // ------------------------------------------------------------------ helpers

    private org.springframework.test.web.servlet.ResultActions withdrawal() throws Exception {
        return mockMvc.perform(get("/v1/user/withdrawal").header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isOk());
    }

    /** 결제 대기로 남은 주문 — 진행 중 주문으로 세지 않는다(14절 Q22). */
    private void purchaseUnpaid() throws Exception {
        placeCardOrder(creamVariant, 1);
    }
}
