package showroomz.api.design1021;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.seller.claim.ClaimTestSupport;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.order.entity.OrderDeliveryGroup;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 7-4 — 소비자 탈퇴 차단(기-31 【잠정】): 진행 중 주문 · 반품 · 교환 · 미결제 재발송비 · 송금 대기 환급. 결제 전 주문 · 구매확정은 막지 않는다.
 */
@DisplayName("1021 7-4 소비자 탈퇴 차단 — 진행 중 주문 · 반품 진행 · 재발송비 미결제 · 송금 대기 · 구매확정 허용 · 결제 전 제외")
class Design1021ConsumerWithdrawalIntegrationTest extends ClaimTestSupport {

    private String admin;

    @BeforeEach
    void setUpAdmin() {
        Seller operator = fixture.createAdmin("withdrawal-1021@showroomz.test", "운영자");
        admin = adminToken(operator);
    }

    @Test
    @DisplayName("결제 전 주문만 있으면 탈퇴 가능 — 진행 중 주문 0")
    void unpaidOrderDoesNotBlock() throws Exception {
        placeCardOrder(creamVariant, 1);

        withdrawal().andExpect(jsonPath("$.withdrawable").value(true))
                .andExpect(jsonPath("$.ongoingOrderCount").value(0))
                .andExpect(jsonPath("$.blockers.ongoingOrders").value(0));
    }

    @Test
    @DisplayName("결제 완료 주문 — 진행 중 주문으로 차단 · 탈퇴 요청 409(진행 중 주문 문장)")
    void paidOrderBlocks() throws Exception {
        paidGroup(creamVariant, 1);

        withdrawal().andExpect(jsonPath("$.withdrawable").value(false))
                .andExpect(jsonPath("$.blockers.ongoingOrders").value(1));
        withdraw().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_BLOCKED_BY_ORDER"))
                .andExpect(jsonPath("$.message").value("진행 중인 주문이 있어 지금은 탈퇴할 수 없습니다."));
    }

    @Test
    @DisplayName("구매확정 뒤 운영자가 연 반품이 진행 중이면 차단 — 진행 중 주문은 0 이어도 반품 · 교환 사유로 409")
    void openClaimAfterConfirmationBlocks() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        returnClaim(group);
        jdbc.update("UPDATE order_product SET status = 'PURCHASE_CONFIRMED' WHERE delivery_group_id = ?", group.getId());

        withdrawal().andExpect(jsonPath("$.withdrawable").value(false))
                .andExpect(jsonPath("$.blockers.ongoingOrders").value(0))
                .andExpect(jsonPath("$.blockers.openClaims").value(1));
        withdraw().andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("진행 중인 반품 · 교환이 있어 지금은 탈퇴할 수 없습니다."));
    }

    @Test
    @DisplayName("전체 반려 — 반려 보류(진행 중) · 재발송 배송비 결제 대기 두 사유가 함께 잡힌다")
    void rejectedClaimWithUnpaidCharge() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        rejectedClaim(returnClaim(group));
        jdbc.update("UPDATE order_product SET status = 'PURCHASE_CONFIRMED' WHERE delivery_group_id = ?", group.getId());

        withdrawal().andExpect(jsonPath("$.withdrawable").value(false))
                .andExpect(jsonPath("$.blockers.openClaims").value(1))
                .andExpect(jsonPath("$.blockers.unpaidCharges").value(1));
    }

    @Test
    @DisplayName("반려 이의 인용으로 끝났지만 운임 환급이 송금 대기 — 받을 돈이 남아 차단(송금 대기 문장) · 송금 완료 기록 뒤 탈퇴 가능")
    void pendingTransferBlocksUntilRecorded() throws Exception {
        freeShippingFrom(50_000);
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 2);
        Long claimId = rejectedClaim(returnClaim(group));
        adminPost("/v1/admin/claims/" + claimId + "/dispute-acceptance", Map.of("detail", "배송 시점 오염"))
                .andExpect(status().isOk());

        withdrawal().andExpect(jsonPath("$.withdrawable").value(false))
                .andExpect(jsonPath("$.blockers.openClaims").value(0))
                .andExpect(jsonPath("$.blockers.unpaidCharges").value(0))
                .andExpect(jsonPath("$.blockers.pendingTransfers").value(1));
        withdraw().andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("아직 송금되지 않은 환급금이 있어 지금은 탈퇴할 수 없습니다."));

        Long transferId = jdbc.queryForObject("SELECT refund_task_id FROM order_refund_task WHERE delivery_group_id = ? "
                + "AND method = 'BANK_TRANSFER'", Long.class, group.getId());
        adminPost("/v1/admin/refunds/" + transferId + "/transfer-complete",
                Map.of("bankCode", "088", "accountNumber", "110123456789", "holderName", "김수민", "transferConfirmed", true))
                .andExpect(status().isOk());
        withdrawal().andExpect(jsonPath("$.blockers.pendingTransfers").value(0))
                .andExpect(jsonPath("$.withdrawable").value(true));
    }

    @Test
    @DisplayName("구매확정만 남았으면 탈퇴된다")
    void purchaseConfirmedAllows() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        jdbc.update("UPDATE order_product SET status = 'PURCHASE_CONFIRMED' WHERE delivery_group_id = ?", group.getId());

        withdrawal().andExpect(jsonPath("$.withdrawable").value(true));
        withdraw().andExpect(status().is2xxSuccessful());
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions withdrawal() throws Exception {
        return mockMvc.perform(get("/v1/user/withdrawal").header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isOk());
    }

    private ResultActions withdraw() throws Exception {
        return mockMvc.perform(delete("/v1/user/auth/withdraw").header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"agreeConsent\":true}"));
    }

    private ResultActions adminPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }
}
