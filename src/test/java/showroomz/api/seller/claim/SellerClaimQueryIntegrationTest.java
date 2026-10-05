package showroomz.api.seller.claim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.order.SellerOrderTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.service.OrderClaimService.Item;
import showroomz.domain.order.service.OrderClaimService.RequestCommand;
import showroomz.domain.order.service.OrderClaimService.RequestResult;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.support.BrandFixture;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 반품·교환 조회(35 설계서 6절 #15 · #16 · #25) + 주문 관리 화면의 클레임 오버레이(5-2).
 * 클레임은 도메인 서비스로 만들고, 검수·재발송 API 가 아직 없는 단계는 SQL 로 재현한다.
 */
@IntegrationTest
class SellerClaimQueryIntegrationTest extends SellerOrderTestSupport {

    private static final String CLAIMS = "/v1/seller/claims";

    @Autowired private OrderClaimService claimService;

    @Test
    @DisplayName("탭 카운트 — 전체는 여섯 탭의 합이고 KPI 는 작업 탭 카운트다. 결제 대기(접수 전)는 어디에도 없다(#15 · #25)")
    void summaryCounts() throws Exception {
        claimOf(deliveredGroup(1));
        setClaim(claimOf(deliveredGroup(1)), "status = 'COLLECTING'");
        setClaim(claimOf(deliveredGroup(1)), "status = 'ARRIVED'");
        setClaim(claimOf(deliveredGroup(1)), "status = 'RECEIVED'");
        setClaim(claimOf(deliveredGroup(1)), "type = 'EXCHANGE', status = 'RESHIP_READY'");
        setClaim(claimOf(deliveredGroup(1)), "status = 'REJECT_HOLD', rejected_at = CURRENT_TIMESTAMP");
        setClaim(claimOf(deliveredGroup(1)), "status = 'REFUND_PENDING'");
        setClaim(claimOf(deliveredGroup(1)), "status = 'COMPLETED', result = 'CANCELLED', completed_at = CURRENT_TIMESTAMP");
        Long pending = claimOf(deliveredGroup(1));
        setClaim(pending, "type = 'EXCHANGE', status = 'PAYMENT_PENDING'");

        sellerGet(CLAIMS + "/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.tabCounts.ALL").value(8))
                .andExpect(jsonPath("$.tabCounts.COLLECT_WAIT").value(1))
                .andExpect(jsonPath("$.tabCounts.COLLECTING").value(1))
                .andExpect(jsonPath("$.tabCounts.INSPECTION").value(2))
                .andExpect(jsonPath("$.tabCounts.RESHIP").value(1))
                .andExpect(jsonPath("$.tabCounts.REJECT_HOLD").value(1))
                .andExpect(jsonPath("$.tabCounts.DONE").value(2))
                .andExpect(jsonPath("$.kpi.collectWait").value(1))
                .andExpect(jsonPath("$.kpi.inspection").value(2))
                .andExpect(jsonPath("$.kpi.reship").value(1))
                .andExpect(jsonPath("$.kpi.overdue").value(0))
                .andExpect(jsonPath("$.typeCounts.RETURN").value(7))
                .andExpect(jsonPath("$.typeCounts.EXCHANGE").value(1));

        // 목록도 같은 기준이다 — 전체 탭에 8건, 결제 대기 건은 목록에도 상세에도 없다.
        sellerGet(CLAIMS + "?tab=ALL").andExpect(jsonPath("$.pageInfo.totalResults").value(8));
        sellerGet(CLAIMS + "?tab=ALL&keyword=CLM-" + pending).andExpect(jsonPath("$.content", empty()));
        sellerGet(CLAIMS + "/" + pending).andExpect(status().isNotFound());
        sellerGet(CLAIMS + "?tab=INSPECTION").andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].stage").value("INSPECTION"));
    }

    @Test
    @DisplayName("목록 — 탭 생략은 회수 대기 · 한 박스의 클레임은 묶음으로 인접하고 행 확장은 하위주문 항목 전체다")
    void listRows() throws Exception {
        OrderDeliveryGroup box = deliveredTwoItemGroup();
        RequestResult result = claimService.request(new RequestCommand(consumer.getId(), box.getId(), ClaimType.RETURN,
                ClaimReason.DAMAGED_OR_DEFECTIVE, "뚜껑이 깨져서 왔어요", List.of("https://img.test/a.jpg", "https://img.test/b.jpg"),
                items(box).stream().map(p -> new Item(p.getId(), p.getQuantity())).toList(), null, null),
                LocalDateTime.now());
        Long first = result.claimIds().get(0);
        Long single = claimOf(deliveredGroup(2), 1);

        sellerGet(CLAIMS).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                // 회수 대기는 신청 오래된순 — 박스의 두 건이 먼저, 인접해서 온다.
                .andExpect(jsonPath("$.content[0].claimId").value(first))
                .andExpect(jsonPath("$.content[0].claimNumber").value("CLM-" + first))
                .andExpect(jsonPath("$.content[0].typeLabel").value("반품"))
                .andExpect(jsonPath("$.content[0].consumerName").value("김수민"))
                .andExpect(jsonPath("$.content[0].reasonLabel").value("배송 상품 파손 및 불량"))
                .andExpect(jsonPath("$.content[0].consumerAttachmentCount").value(2))
                .andExpect(jsonPath("$.content[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.content[0].statusLabel").value("회수 대기"))
                .andExpect(jsonPath("$.content[0].stage").value("COLLECT_WAIT"))
                .andExpect(jsonPath("$.content[0].statusTone").value("NEUTRAL"))
                .andExpect(jsonPath("$.content[0].overdue").value(false))
                .andExpect(jsonPath("$.content[0].collection.size").value(2))
                .andExpect(jsonPath("$.content[0].collection.leadClaimNumber").value("CLM-" + first))
                .andExpect(jsonPath("$.content[0].collection.trackingNumber").value(nullValue()))
                .andExpect(jsonPath("$.content[0].shipLabel").value(nullValue()))
                .andExpect(jsonPath("$.content[0].storage").value(nullValue()))
                .andExpect(jsonPath("$.content[0].outcome").value(nullValue()))
                .andExpect(jsonPath("$.content[0].actions.canConfirmReceipt").value(false))
                .andExpect(jsonPath("$.content[0].orderItems", hasSize(2)))
                .andExpect(jsonPath("$.content[0].orderItems[0].itemStatusLabel").value("반품 신청"))
                .andExpect(jsonPath("$.content[0].orderSummary").value("2개 항목 중 2개 신청"))
                .andExpect(jsonPath("$.content[1].collection.collectionId").value(result.collectionId()))
                // 수량 2 중 1개만 신청한 건.
                .andExpect(jsonPath("$.content[2].claimId").value(single))
                .andExpect(jsonPath("$.content[2].quantity").value(1))
                .andExpect(jsonPath("$.content[2].collection.size").value(1))
                .andExpect(jsonPath("$.content[2].orderItems[0].orderedQuantity").value(2))
                .andExpect(jsonPath("$.content[2].orderItems[0].claimedQuantity").value(1));
    }

    @Test
    @DisplayName("필터 — 접수번호 · 주문번호 · 유형 · 사유. 기간 역전과 1년 초과는 400")
    void filters() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1);
        Long returnClaim = claimOf(group);
        Long exchangeClaim = claimOf(deliveredGroup(1));
        setClaim(exchangeClaim, "type = 'EXCHANGE', exchange_option_name = '리필'");

        sellerGet(CLAIMS + "?keyword=clm" + returnClaim).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].claimId").value(returnClaim));
        sellerGet(CLAIMS + "?keyword=" + orderNumberOf(group)).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].claimId").value(returnClaim));
        sellerGet(CLAIMS + "?keyword=NO-SUCH-ORDER").andExpect(jsonPath("$.content", empty()));
        sellerGet(CLAIMS + "?types=EXCHANGE").andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].claimId").value(exchangeClaim))
                .andExpect(jsonPath("$.content[0].exchangeOptionName").value("리필"));
        sellerGet(CLAIMS + "?types=RETURN,EXCHANGE").andExpect(jsonPath("$.content", hasSize(2)));
        sellerGet(CLAIMS + "?reason=ORDER_MISTAKE").andExpect(jsonPath("$.content", empty()));
        sellerGet(CLAIMS + "?from=2026-10-05&to=2026-10-01").andExpect(status().isBadRequest());
        sellerGet(CLAIMS + "?from=2025-01-01&to=2026-10-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_SEARCH_RANGE_EXCEEDED"));
        // 검색어를 넣어도 요약은 전체 기준이다.
        sellerGet(CLAIMS + "/summary").andExpect(jsonPath("$.tabCounts.ALL").value(2));
    }

    @Test
    @DisplayName("기한 초과 — 회수 대기 방치와 검수 기한 경과만. KPI 와 행의 overdue 가 같은 기준이다")
    void overdue() throws Exception {
        Long waiting = claimOf(deliveredGroup(1));
        Long inspecting = claimOf(deliveredGroup(1));
        LocalDateTime past = LocalDateTime.now().minusHours(1);
        jdbc.update("UPDATE order_claim SET collect_due_at = ? WHERE claim_id = ?", past, waiting);
        jdbc.update("UPDATE order_claim SET status = 'RECEIVED', received_at = ?, inspect_due_at = ? WHERE claim_id = ?",
                past, past, inspecting);

        sellerGet(CLAIMS + "/summary").andExpect(jsonPath("$.kpi.overdue").value(2));
        sellerGet(CLAIMS + "?tab=COLLECT_WAIT").andExpect(jsonPath("$.content[0].overdue").value(true));
        sellerGet(CLAIMS + "?tab=INSPECTION").andExpect(jsonPath("$.content[0].overdue").value(true))
                .andExpect(jsonPath("$.content[0].actions.canInspect").value(true));
    }

    @Test
    @DisplayName("거절 보류 — 보관 기한은 고지 2회부터 생기는 계산값이고, 보낼 물건은 원래 옵션이다")
    void rejectHoldStorage() throws Exception {
        Long claimId = claimOf(deliveredGroup(1));
        // 최종 고지가 한 달 전 — 보관 기한(최종 고지일 + 3개월)이 아직 남아 있다.
        LocalDateTime lastNotice = LocalDateTime.now().minusMonths(1).withNano(0);
        setClaim(claimId, "type = 'EXCHANGE', exchange_option_name = '리필', status = 'REJECT_HOLD', "
                + "rejected_at = CURRENT_TIMESTAMP, reject_reason_code = 'USED', reject_detail = '사용 흔적이 있습니다', "
                + "notice_count = 1");

        sellerGet(CLAIMS + "?tab=REJECT_HOLD")
                .andExpect(jsonPath("$.content[0].rejectReasonLabel").value("개봉·사용 흔적"))
                .andExpect(jsonPath("$.content[0].storage.noticeCount").value(1))
                .andExpect(jsonPath("$.content[0].storage.phase").value("NOTICE_PENDING"))
                .andExpect(jsonPath("$.content[0].storage.storageDueAt").value(nullValue()))
                // 거절 건에 새 옵션을 보내면 안 된다 — 원래 옵션.
                .andExpect(jsonPath("$.content[0].shipLabel").value(items(groupOfClaim(claimId)).get(0).getProductName()
                        + optionSuffix(items(groupOfClaim(claimId)).get(0))));

        jdbc.update("UPDATE order_claim SET notice_count = 2, last_notice_at = ? WHERE claim_id = ?", lastNotice, claimId);
        jdbc.update("INSERT INTO order_claim_notice (claim_id, seq, notified_at, channel, actor_type) VALUES "
                + "(?, 1, ?, 'PUSH', 'SYSTEM'), (?, 2, ?, 'PUSH', 'SYSTEM')", claimId, lastNotice.minusDays(7), claimId,
                lastNotice);

        sellerGet(CLAIMS + "/" + claimId).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.storage.noticeCount").value(2))
                .andExpect(jsonPath("$.summary.storage.phase").value("STORING"))
                .andExpect(jsonPath("$.summary.storage.storageDueAt")
                        .value(jsonTime(lastNotice.toLocalDate().plusMonths(3).atTime(LocalTime.of(23, 59, 59)))))
                .andExpect(jsonPath("$.rejectDetail").value("사용 흔적이 있습니다"))
                .andExpect(jsonPath("$.notices", hasSize(2)))
                .andExpect(jsonPath("$.notices[1].seq").value(2));
    }

    @Test
    @DisplayName("상세 — 연락처는 마스킹 · 환불 예정액은 요청 단위(항목 금액과 요청의 차감을 따로) · 이력은 최신순")
    void detail() throws Exception {
        OrderDeliveryGroup freeShipping = deliveredGroup(4);
        Long claimId = claimOf(freeShipping, 4);

        sellerGet(CLAIMS + "/" + claimId).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.claimId").value(claimId))
                .andExpect(jsonPath("$.orderNumber").value(orderNumberOf(freeShipping)))
                .andExpect(jsonPath("$.deliveryGroupId").value(freeShipping.getId()))
                .andExpect(jsonPath("$.consumerPhone").value("010-****-5678"))
                .andExpect(jsonPath("$.exchangeFeeCharged").value(nullValue()))
                .andExpect(jsonPath("$.refund.itemAmount").value(4 * CREAM_PRICE))
                .andExpect(jsonPath("$.refund.requestDeduction").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.refund.requestExpectedAmount").value(4 * CREAM_PRICE - DELIVERY_FEE))
                .andExpect(jsonPath("$.refund.basisLabel").value("상품 금액 − 최초 배송비"))
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(jsonPath("$.notices", empty()))
                .andExpect(jsonPath("$.history[0].eventType").value("REQUESTED"))
                .andExpect(jsonPath("$.history[0].actorLabel").value("소비자"))
                .andExpect(jsonPath("$.actions.canPass").value(false));
    }

    @Test
    @DisplayName("남의 마켓 클레임은 404(존재 비노출) · 인플루언서(CREATOR) 토큰은 403(#16)")
    void access() throws Exception {
        Long claimId = claimOf(deliveredGroup(1));
        BrandFixture.Brand other = otherBrand();
        String otherToken = sellerToken(other.seller());
        String creatorToken = bearerToken(consumer.getUsername(), RoleType.CREATOR, consumer.getId());

        sellerGet(otherToken, CLAIMS + "/" + claimId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));
        sellerGet(otherToken, CLAIMS + "?tab=ALL").andExpect(jsonPath("$.content", empty()));
        sellerGet(otherToken, CLAIMS + "/summary").andExpect(jsonPath("$.tabCounts.ALL").value(0));
        for (String url : List.of(CLAIMS, CLAIMS + "/summary", CLAIMS + "/" + claimId)) {
            mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("주문 관리 — 진행 중 클레임 수를 내리고, 보류 클레임이 있으면 구매확정 D-N 이 빈다. 거절된 건은 세되 D-N 을 막지 않는다")
    void orderOverlay() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1);
        sellerGet(SELLER_ORDERS + "?tab=DELIVERED")
                .andExpect(jsonPath("$.content[0].overlays.openClaimCount").value(0))
                .andExpect(jsonPath("$.content[0].confirmRemainingDays").isNumber());

        Long claimId = claimOf(group);
        sellerGet(SELLER_ORDERS + "?tab=DELIVERED")
                .andExpect(jsonPath("$.content[0].overlays.openClaimCount").value(1))
                .andExpect(jsonPath("$.content[0].confirmRemainingDays").value(nullValue()));
        orderDetail(group.getId())
                .andExpect(jsonPath("$.overlays.openClaimCount").value(1))
                .andExpect(jsonPath("$.timeline.confirmDueAt").value(nullValue()));

        setClaim(claimId, "status = 'REJECT_HOLD', rejected_at = CURRENT_TIMESTAMP");
        sellerGet(SELLER_ORDERS + "?tab=DELIVERED")
                .andExpect(jsonPath("$.content[0].overlays.openClaimCount").value(1))
                .andExpect(jsonPath("$.content[0].confirmRemainingDays").isNumber());
    }

    // ------------------------------------------------------------------ 픽스처

    private OrderDeliveryGroup deliveredGroup(int quantity) throws Exception {
        return delivered(shipped(prepared(paidGroup(creamVariant, quantity)), "CJ", newInvoice()),
                LocalDateTime.now().minusHours(1).withNano(0));
    }

    private OrderDeliveryGroup deliveredTwoItemGroup() throws Exception {
        return delivered(shipped(prepared(paidTwoItemGroup()), "CJ", newInvoice()),
                LocalDateTime.now().minusHours(1).withNano(0));
    }

    private static String newInvoice() {
        return String.valueOf(100_000_000_000L + (long) (Math.random() * 899_999_999_999L));
    }

    /** 첫 항목 전량을 단순 변심으로 반품 신청. */
    private Long claimOf(OrderDeliveryGroup group) {
        return claimOf(group, items(group).get(0).getQuantity());
    }

    private Long claimOf(OrderDeliveryGroup group, int quantity) {
        return claimService.request(new RequestCommand(consumer.getId(), group.getId(), ClaimType.RETURN,
                ClaimReason.CHANGE_OF_MIND, null, List.of(), List.of(new Item(items(group).get(0).getId(), quantity)),
                null, null), LocalDateTime.now()).claimIds().get(0);
    }

    private void setClaim(Long claimId, String assignments) {
        jdbc.update("UPDATE order_claim SET " + assignments + " WHERE claim_id = " + claimId);
    }

    private OrderDeliveryGroup groupOfClaim(Long claimId) {
        Long groupId = jdbc.queryForObject("SELECT delivery_group_id FROM order_claim WHERE claim_id = ?", Long.class,
                claimId);
        return deliveryGroupRepository.findOwned(groupId, brand.marketId()).orElseThrow();
    }

    private static String optionSuffix(OrderProduct product) {
        return product.getOptionName() == null || product.getOptionName().isBlank() ? "" : " " + product.getOptionName();
    }
}
