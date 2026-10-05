package showroomz.api.app.claim;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.claim.ClaimTestSupport;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.service.OrderClaimService.RequestResult;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackEvent;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회수 조회 · 재발송 배송 조회의 나머지 분기(보강 시나리오 7-2절 TK-01 ~ TK-07). 반품 회수 조회의 5단계와 재발송 조회의
 * 기본 흐름은 {@code SellerClaimReshipIntegrationTest}가 덮었다.
 */
@IntegrationTest
class UserClaimTrackingEdgeIntegrationTest extends ClaimTestSupport {

    @Test
    @DisplayName("[TK-01] 교환 회수 조회 — 마지막 칸은 「새 상품」 · 옵션은 「받은 → 바꿀」 · 통과 직후는 준비 중, 재발송 송장이 서면 출발")
    void exchangeCollectionTracking() throws Exception {
        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        OrderProduct product = items(group).get(0);
        Long claimId = exchangeClaim(group, refill);
        String url = USER_CLAIMS + "/" + claimId + "/collection-tracking";

        userGet(url).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EXCHANGE"))
                .andExpect(jsonPath("$.stages[4]").value("새 상품"))
                .andExpect(jsonPath("$.item.optionLabel").value(product.getOptionName() + " → 리필"));

        passed(claimId);
        userGet(url).andExpect(jsonPath("$.stageIndex").value(4))
                .andExpect(jsonPath("$.headline.text").value("검수가 끝나 새 상품을 준비하고 있어요"))
                .andExpect(jsonPath("$.events[0].source").value("BRAND"))
                .andExpect(jsonPath("$.events[0].description").value("검수 완료 · 새 상품 발송 준비"));

        registerReship(claimId, "CJ", newInvoice()).andExpect(jsonPath("$.succeeded").value(1));
        userGet(url).andExpect(jsonPath("$.headline.text").value("검수가 끝나 새 상품이 출발했어요"));
    }

    @Test
    @DisplayName("[TK-02] 회수 송장이 없는 요청(회수 대기) — 바는 빈 칸 · 택배사 없음 · 「회수 송장을 등록해 주세요」")
    void collectionTrackingWithoutInvoice() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long claimId = requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, allItems(group), null)
                .claimIds().get(0);

        userGet(USER_CLAIMS + "/" + claimId + "/collection-tracking").andExpect(status().isOk())
                .andExpect(jsonPath("$.stageIndex").value(-1))
                .andExpect(jsonPath("$.trackable").value(false))
                .andExpect(jsonPath("$.carrier").value(nullValue()))
                .andExpect(jsonPath("$.trackingNumber").value(nullValue()))
                .andExpect(jsonPath("$.invoiceEditable").value(false))
                .andExpect(jsonPath("$.headline.text").value("회수 송장을 등록해 주세요"));
    }

    @Test
    @DisplayName("[TK-03] 이동 중 — 첫 스캔이 최근이면 도착 예정일을 내리고, 예정일이 이미 지났으면 날짜 없이 「이동 중」")
    void arrivalEstimate() throws Exception {
        String recent = newInvoice();
        Long recentClaim = returnClaimWith(recent);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        claimService.applyCollectionTracking(collectionIdOf(recentClaim), DeliveryCarrier.CJ, recent,
                snapshotOf(now.minusHours(1), null, moving(now.minusHours(2)), moving(now.minusHours(1))), now);
        userGet(USER_CLAIMS + "/" + recentClaim + "/collection-tracking")
                .andExpect(jsonPath("$.stageIndex").value(1))
                .andExpect(jsonPath("$.headline.date").value(notNullValue()))
                .andExpect(jsonPath("$.headline.text").value("도착 예정이에요"))
                .andExpect(jsonPath("$.headline.sub").value(notNullValue()));

        String stale = newInvoice();
        Long staleClaim = returnClaimWith(stale);
        claimService.applyCollectionTracking(collectionIdOf(staleClaim), DeliveryCarrier.CJ, stale,
                snapshotOf(now.minusDays(20), null, moving(now.minusDays(30)), moving(now.minusDays(20))), now);
        userGet(USER_CLAIMS + "/" + staleClaim + "/collection-tracking")
                .andExpect(jsonPath("$.stageIndex").value(1))
                .andExpect(jsonPath("$.headline.date").value(nullValue()))
                .andExpect(jsonPath("$.headline.text").value("브랜드로 이동 중이에요"));
    }

    @Test
    @DisplayName("[TK-04] 철회된 요청은 열리고(당시 기준) · 결제 대기 요청 · 남의 요청은 404")
    void accessRules() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long withdrawn = requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, allItems(group), null)
                .claimIds().get(0);
        userPost(USER_CLAIMS + "/" + withdrawn + "/withdraw", Map.of()).andExpect(status().isOk());
        userGet(USER_CLAIMS + "/" + withdrawn + "/collection-tracking").andExpect(status().isOk())
                .andExpect(jsonPath("$.stageIndex").value(-1));

        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        OrderDeliveryGroup pendingGroup = deliveredGroup(creamVariant, 1);
        JsonNode created = json(userPost(USER_CLAIMS, claimBody(pendingGroup, "EXCHANGE", "CHANGE_OF_MIND",
                items(pendingGroup).get(0).getId(), null, refill.getVariantId())).andExpect(status().isCreated()));
        Long pending = created.get("claimIds").get(0).asLong();
        for (String path : new String[]{"/collection-tracking", "/reship-tracking"}) {
            userGet(USER_CLAIMS + "/" + pending + path).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));
        }

        Long mine = returnClaimWith(newInvoice());
        Users stranger = createConsumer("stranger", "타인");
        String token = bearerToken(stranger.getEmail(), RoleType.USER, stranger.getId());
        for (String path : new String[]{"/collection-tracking", "/reship-tracking"}) {
            mockMvc.perform(get(USER_CLAIMS + "/" + mine + path).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("[TK-05] 운영자 직권으로 끝난 재발송 — 배송 완료이고 날짜는 종결 시각이다")
    void adminCompletedReship() throws Exception {
        Long claimId = passed(exchangeClaim(deliveredGroup(creamVariant, 1), creamVariant));
        registerReship(claimId, "CJ", newInvoice()).andExpect(jsonPath("$.succeeded").value(1));
        LocalDateTime closedAt = LocalDateTime.now().minusDays(1).withNano(0);
        claimService.completeReshipByAdmin(claimId, 1L, closedAt);

        userGet(USER_CLAIMS + "/" + claimId + "/reship-tracking").andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DELIVERED"))
                .andExpect(jsonPath("$.stageIndex").value(2))
                .andExpect(jsonPath("$.headline.date").value(closedAt.toLocalDate().toString()));
    }

    @Test
    @DisplayName("[TK-06] 보낸 사람 표기 — 한 글자 이름 · 한 단어 주소 · 주소 없음에서도 깨지지 않는다")
    void senderEdgeCases() throws Exception {
        Long first = returnClaimWith(newInvoice());
        setRecipient(first, "김", "세종");
        userGet(USER_CLAIMS + "/" + first + "/collection-tracking")
                .andExpect(jsonPath("$.sender").value("김 · 세종"));

        Long second = returnClaimWith(newInvoice());
        setRecipient(second, "김수민", null);
        userGet(USER_CLAIMS + "/" + second + "/collection-tracking")
                .andExpect(jsonPath("$.sender").value("김수*"));
    }

    @Test
    @DisplayName("[TK-07] 일부 반려 박스 — 택배 구간은 같고 검수 구간만 다르다(통과 4 · 반려 3)")
    void partialRejectionBox() throws Exception {
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        String invoice = newInvoice();
        RequestResult request = requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, allItems(group),
                invoice);
        Long approved = request.claimIds().get(0);
        Long rejected = request.claimIds().get(1);
        rejectedClaim(rejected);
        passed(approved);

        userGet(USER_CLAIMS + "/" + approved + "/collection-tracking")
                .andExpect(jsonPath("$.stageIndex").value(4))
                .andExpect(jsonPath("$.trackingNumber").value(invoice));
        userGet(USER_CLAIMS + "/" + rejected + "/collection-tracking")
                .andExpect(jsonPath("$.stageIndex").value(3))
                .andExpect(jsonPath("$.headline.text").value("검수에서 반려되었어요"))
                .andExpect(jsonPath("$.trackingNumber").value(invoice));
    }

    // ------------------------------------------------------------------ 보조

    private Long returnClaimWith(String invoice) throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        return requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, allItems(group), invoice)
                .claimIds().get(0);
    }

    private static TrackEvent moving(LocalDateTime at) {
        return scan(at, "곤지암Hub", "간선상차", 3);
    }

    /** 원 주문의 수취인 표기만 바꾼다 — 보낸 사람은 원 주문 배송지에서 만든다. */
    private void setRecipient(Long claimId, String name, String address) {
        jdbc.update("UPDATE orders SET recipient_name = ?, address = ? WHERE order_id = "
                + "(SELECT order_id FROM order_claim WHERE claim_id = ?)", name, address, claimId);
    }
}
