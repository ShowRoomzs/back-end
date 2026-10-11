package showroomz.api.seller.claim;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.seller.order.SellerOrderTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.service.OrderClaimService.Invoice;
import showroomz.domain.order.service.OrderClaimService.Item;
import showroomz.domain.order.service.OrderClaimService.RequestCommand;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimReshipColumn;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackEvent;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.support.IntegrationTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 재발송 · 추적 · 거절 보류(35 설계서 6절 #7 · #9 · #12 · #13 · #20 ~ #23 · 앱 클레임 설계서 7절 #19 · #23).
 * 택배 연동은 꺼져 있어(배치 미기동) 추적 결과는 {@link TrackSnapshot}으로 주입하고 반영은 운영과 같은 도메인 진입점을 태운다.
 * 고지 · 폐기는 받는 API(어드민)가 아직 없어 도메인 진입점을 직접 부른다.
 */
@IntegrationTest
class SellerClaimReshipIntegrationTest extends SellerOrderTestSupport {

    private static final String CLAIMS = "/v1/seller/claims";
    private static final String USER_CLAIMS = "/v1/user/claims";

    @Autowired private OrderClaimService claimService;

    // ------------------------------------------------------------------ 교환 재발송(#7)

    @Test
    @DisplayName("교환 통과 → 재발송 송장 등록 → 수정 → 도착 — 등록은 완료가 아니라 재발송 중이고, 도착하면 교환 완료 · 구매확정 카운트가 도착 시각부터 다시 선다(#7)")
    void exchangeReship() throws Exception {
        OrderDeliveryGroup group = deliveredGroup();
        Long claimId = passed(exchangeClaim(group));
        Long other = passed(exchangeClaim(deliveredGroup()));
        assertThat(claimRow(claimId)).containsEntry("status", "RESHIP_READY");

        userGet(USER_CLAIMS + "/" + claimId + "/reship-tracking").andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_SHIPPED"))
                .andExpect(jsonPath("$.context").value("EXCHANGE_RESHIP"))
                .andExpect(jsonPath("$.contextLabel").value("교환 상품 발송"))
                .andExpect(jsonPath("$.stageIndex").value(-1))
                .andExpect(jsonPath("$.carrier").value(nullValue()));

        String invoice = newInvoice();
        // 빈 송장 행은 조용히 건너뛰고, 없는 건은 사유와 함께 제외된다.
        sellerPost(CLAIMS + "/reshipments", Map.of("items", List.of(
                reshipRow(claimId, "CJ", invoice), reshipRow(other, "CJ", " "), reshipRow(999_999L, "CJ", newInvoice()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.skipped", hasSize(1)))
                .andExpect(jsonPath("$.skipped[0].claimId").value(999_999))
                .andExpect(jsonPath("$.skipped[0].code").value("CLAIM_STATE_CHANGED"));

        assertThat(claimRow(claimId)).containsEntry("status", "RESHIPPING").containsEntry("result", null);
        assertThat(claimRow(other)).containsEntry("status", "RESHIP_READY");
        sellerGet(CLAIMS + "/" + claimId).andExpect(jsonPath("$.actions.canUpdateReshipment").value(true))
                .andExpect(jsonPath("$.actions.canRegisterReshipment").value(false));
        userGet(USER_CLAIMS + "/" + claimId)
                .andExpect(jsonPath("$.items[0].phase").value("RESHIPPING"))
                .andExpect(jsonPath("$.items[0].actions[*].type", contains("TRACK_RESHIP")))
                .andExpect(jsonPath("$.info.reshipInvoice.trackingNumber").value(invoice));
        // 두 번 등록되지 않는다.
        sellerPost(CLAIMS + "/reshipments", Map.of("items", List.of(reshipRow(claimId, "CJ", newInvoice()))))
                .andExpect(jsonPath("$.succeeded").value(0));

        String corrected = newInvoice();
        sellerPatch(CLAIMS + "/" + claimId + "/reshipment", Map.of("carrier", "HANJIN", "trackingNumber", corrected))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("RESHIPPING"));
        assertThat(claimRow(claimId)).containsEntry("reship_tracking_number", corrected);
        assertThat(events(claimId)).contains("RESHIP_INVOICE_REGISTERED", "RESHIP_INVOICE_UPDATED");
        sellerPatch(CLAIMS + "/" + other + "/reshipment", Map.of("carrier", "CJ", "trackingNumber", newInvoice()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_STATE_CHANGED"));

        LocalDateTime now = LocalDateTime.now().withNano(0);
        // 구 송장의 추적 결과는 덮이지 않는다.
        claimService.applyReshipTracking(claimId, DeliveryCarrier.CJ, invoice,
                snapshot(now, now, scan(now, "강남", "배송완료", 6)), now);
        assertThat(claimRow(claimId)).containsEntry("status", "RESHIPPING");

        claimService.applyReshipTracking(claimId, DeliveryCarrier.HANJIN, corrected,
                snapshot(now.minusHours(2), null, scan(now.minusHours(2), "곤지암Hub", "간선하차", 3)), now);
        userGet(USER_CLAIMS + "/" + claimId + "/reship-tracking")
                .andExpect(jsonPath("$.state").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.stageIndex").value(1))
                .andExpect(jsonPath("$.carrier.code").value("HANJIN"))
                .andExpect(jsonPath("$.scans", hasSize(1)))
                .andExpect(jsonPath("$.scans[0].location").value("곤지암Hub"));

        LocalDateTime deliveredAt = now.minusMinutes(30);
        claimService.applyReshipTracking(claimId, DeliveryCarrier.HANJIN, corrected, snapshot(deliveredAt, deliveredAt,
                scan(now.minusHours(2), "곤지암Hub", "간선하차", 3), scan(deliveredAt, "강남", "배송완료", 6)), now);

        assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "EXCHANGED");
        assertThat(reload(group).getConfirmRestartAt()).isEqualTo(deliveredAt);
        assertThat(events(claimId)).contains("RESHIP_DELIVERED");
        // 도착 시각부터 다시 센다 — 아직 구매확정되지 않는다.
        assertThat(fulfillmentService.confirmIfDue(group.getId(), LocalDateTime.now())).isFalse();
        userGet(USER_CLAIMS + "/" + claimId + "/reship-tracking")
                .andExpect(jsonPath("$.state").value("DELIVERED"))
                .andExpect(jsonPath("$.stageIndex").value(2))
                .andExpect(jsonPath("$.scans", hasSize(2)))
                .andExpect(jsonPath("$.scans[0].description").value("배송완료"));
    }

    // ------------------------------------------------------------------ 송장 중복(#12)

    @Test
    @DisplayName("재발송 송장 전역 중복 — 재발송 중인 다른 건의 송장과 겹치면 그 행만 제외된다(#12)")
    void duplicateInvoice() throws Exception {
        Long first = passed(exchangeClaim(deliveredGroup()));
        Long second = passed(exchangeClaim(deliveredGroup()));
        String invoice = newInvoice();

        sellerPost(CLAIMS + "/reshipments", Map.of("items", List.of(
                reshipRow(first, "CJ", invoice), reshipRow(second, "CJ", invoice))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.skipped[0].claimId").value(second))
                .andExpect(jsonPath("$.skipped[0].code").value("INVOICE_DUPLICATE"));

        assertThat(claimRow(second)).containsEntry("status", "RESHIP_READY");
        // 택배사 없는 행은 형식 오류다.
        sellerPost(CLAIMS + "/reshipments", Map.of("items", List.of(reshipRow(second, null, newInvoice()))))
                .andExpect(jsonPath("$.skipped[0].code").value("INVOICE_FORMAT_INVALID"));
    }

    // ------------------------------------------------------------------ 회수 조회(앱 #19)

    @Test
    @DisplayName("회수 조회 — 택배 이력이 없으면 조회 불가(-1), 집화 0 · 이동 1 · 도착 2 · 입고 확인 3 · 통과 4. 도착이 확인되면 입고 확인 전으로 넘어간다(앱 #19)")
    void collectionTracking() throws Exception {
        OrderDeliveryGroup group = deliveredGroup();
        String invoice = newInvoice();
        var request = claimService.request(new RequestCommand(consumer.getId(), group.getId(), ClaimType.RETURN,
                ClaimReason.CHANGE_OF_MIND, null, List.of(), allItems(group), new Invoice(DeliveryCarrier.CJ, invoice),
                null), LocalDateTime.now());
        Long claimId = request.claimIds().get(0);
        Long collectionId = request.collectionId();
        String url = USER_CLAIMS + "/" + claimId + "/collection-tracking";

        userGet(USER_CLAIMS + "/" + claimId)
                .andExpect(jsonPath("$.items[0].actions[*].type", contains("TRACK_COLLECTION")));
        userGet(url).andExpect(status().isOk())
                .andExpect(jsonPath("$.trackable").value(false))
                .andExpect(jsonPath("$.stageIndex").value(-1))
                .andExpect(jsonPath("$.stages[4]").value("환불"))
                .andExpect(jsonPath("$.headline.text").value("아직 조회되지 않아요"))
                .andExpect(jsonPath("$.carrier.code").value("CJ"))
                .andExpect(jsonPath("$.trackingNumber").value(invoice))
                .andExpect(jsonPath("$.receiver").value(group.getMarketName() + " 반품센터"))
                .andExpect(jsonPath("$.invoiceEditable").value(true))
                .andExpect(jsonPath("$.events", hasSize(0)));

        LocalDateTime now = LocalDateTime.now().withNano(0);
        TrackEvent pickedUp = scan(now.minusHours(5), "강남집배점", "집화처리", 2);
        claimService.applyCollectionTracking(collectionId, DeliveryCarrier.CJ, invoice,
                snapshot(now.minusHours(5), null, pickedUp), now);
        userGet(url).andExpect(jsonPath("$.trackable").value(true))
                .andExpect(jsonPath("$.stageIndex").value(0))
                .andExpect(jsonPath("$.headline.text").value("접수되었어요"))
                // 택배사에 잡힌 뒤에는 송장을 고칠 수 없다.
                .andExpect(jsonPath("$.invoiceEditable").value(false));

        TrackEvent moving = scan(now.minusHours(3), "곤지암Hub", "간선상차", 3);
        claimService.applyCollectionTracking(collectionId, DeliveryCarrier.CJ, invoice,
                snapshot(now.minusHours(3), null, pickedUp, moving), now);
        userGet(url).andExpect(jsonPath("$.stageIndex").value(1))
                .andExpect(jsonPath("$.events", hasSize(2)))
                .andExpect(jsonPath("$.events[0].source").value("COURIER"))
                .andExpect(jsonPath("$.events[0].location").value("곤지암Hub"));
        assertThat(claimRow(claimId)).containsEntry("status", "COLLECTING");

        LocalDateTime arrivedAt = now.minusHours(1);
        claimService.applyCollectionTracking(collectionId, DeliveryCarrier.CJ, invoice,
                snapshot(arrivedAt, arrivedAt, pickedUp, moving, scan(arrivedAt, "브랜드", "배송완료", 6)), now);
        assertThat(claimRow(claimId)).containsEntry("status", "ARRIVED");
        assertThat(events(claimId)).contains("ARRIVED");
        userGet(url).andExpect(jsonPath("$.stageIndex").value(2))
                .andExpect(jsonPath("$.headline.text").value("브랜드에 도착했어요"));

        received(claimId);
        userGet(url).andExpect(jsonPath("$.stageIndex").value(3))
                .andExpect(jsonPath("$.headline.text").value("도착해서 검수 중이에요"))
                .andExpect(jsonPath("$.events[0].source").value("BRAND"))
                .andExpect(jsonPath("$.events[0].description").value("입고 · 검수 시작"));

        sellerPost(CLAIMS + "/" + claimId + "/inspection/pass", Map.of()).andExpect(status().isOk());
        userGet(url).andExpect(jsonPath("$.stageIndex").value(4))
                .andExpect(jsonPath("$.headline.text").value("검수가 끝나 환불이 진행돼요"))
                .andExpect(jsonPath("$.events", hasSize(5)))
                .andExpect(jsonPath("$.events[0].description").value("검수 완료 · 환불 진행"));
        // 환불로 끝나는 반품에는 재발송이 없다.
        userGet(USER_CLAIMS + "/" + claimId + "/reship-tracking").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_STATE_CHANGED"));
    }

    // ------------------------------------------------------------------ 반려 → 결제 → 반송(#9 · 앱 #23)

    @Test
    @DisplayName("반려 → 재발송 배송비 결제 → 반송 송장 → 도착 — 결제가 확정되면 재발송 대기로 가고, 도착하면 거절로 종결된다. 구매확정 카운트는 다시 서지 않는다(#9 · 앱 #23)")
    void rejectPayReship() throws Exception {
        OrderDeliveryGroup group = deliveredGroup();
        Long claimId = received(returnClaim(group));
        sellerPost(CLAIMS + "/" + claimId + "/inspection/reject", rejectBody()).andExpect(status().isOk());
        String payUrl = USER_CLAIMS + "/" + claimId + "/reship-fee/payments";

        userGet(USER_CLAIMS + "/" + claimId + "/collection-tracking")
                .andExpect(jsonPath("$.stageIndex").value(3))
                .andExpect(jsonPath("$.headline.text").value("검수에서 반려되었어요"));
        userGet(USER_CLAIMS + "/" + claimId + "/reship-tracking").andExpect(status().isConflict());

        userPost(payUrl, Map.of("method", "CARD", "cardIssuer", "SHINHAN", "expectedAmount", DELIVERY_FEE + 1))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_AMOUNT_CHANGED"));
        userPost(payUrl, Map.of("expectedAmount", DELIVERY_FEE)).andExpect(status().isBadRequest());
        assertThat(count("order_claim_payment")).isZero();

        JsonNode window = json(userPost(payUrl,
                Map.of("method", "CARD", "cardIssuer", "SHINHAN", "expectedAmount", DELIVERY_FEE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId", startsWith("clm-")))
                .andExpect(jsonPath("$.totalAmount").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.orderName").value("반려 상품 재발송 배송비")));
        String paymentId = window.get("paymentId").asText();
        // 결제창을 열기만 한 것으로는 넘어가지 않는다.
        assertThat(claimRow(claimId)).containsEntry("status", "REJECT_HOLD");

        fake.willReturnPaid(paymentId, DELIVERY_FEE);
        mockMvc.perform(post(USER_CLAIMS + "/payments/" + paymentId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.claimStatus").value("RESHIP_READY"));

        assertThat(jdbc.queryForMap("SELECT * FROM order_claim_charge")).containsEntry("status", "PAID")
                .containsEntry("paid_payment_id", paymentId);
        userGet(USER_CLAIMS + "/" + claimId)
                .andExpect(jsonPath("$.items[0].phase").value("REJECTED_PREPARING"))
                .andExpect(jsonPath("$.reshipFee.state").value("PAID"))
                .andExpect(jsonPath("$.reshipFee.methodLabel").value("신한카드"));
        userPost(payUrl, Map.of("method", "CARD", "cardIssuer", "SHINHAN"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_PAYMENT_NOT_REQUIRED"));

        String invoice = newInvoice();
        sellerPost(CLAIMS + "/reshipments", Map.of("items", List.of(reshipRow(claimId, "CJ", invoice))))
                .andExpect(jsonPath("$.succeeded").value(1));
        userGet(USER_CLAIMS + "/" + claimId)
                .andExpect(jsonPath("$.items[0].phase").value("REJECTED_RESHIPPING"))
                .andExpect(jsonPath("$.items[0].actions[*].type", contains("TRACK_RESHIP")));
        userGet(USER_CLAIMS + "/" + claimId + "/reship-tracking")
                .andExpect(jsonPath("$.context").value("REJECT_RESHIP"))
                .andExpect(jsonPath("$.contextLabel").value("반려 상품 재발송"))
                .andExpect(jsonPath("$.state").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.stageIndex").value(0))
                .andExpect(jsonPath("$.item.optionName").value(items(group).get(0).getOptionName()));

        LocalDateTime deliveredAt = LocalDateTime.now().withNano(0);
        claimService.applyReshipTracking(claimId, DeliveryCarrier.CJ, invoice,
                snapshot(deliveredAt, deliveredAt, scan(deliveredAt, "강남", "배송완료", 6)), deliveredAt);

        assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "REJECTED");
        // 반려로 정지가 풀렸다 — 정지한 시간만큼만 기산점이 밀리고(1009 기획 수정본 4절) 반송 도착은 타이머를 새로 세우지 않는다.
        OrderDeliveryGroup timer = reload(group);
        assertThat(timer.getConfirmPausedAt()).isNull();
        assertThat(timer.getConfirmRestartAt()).isBetween(timer.getDeliveredAt(), timer.getDeliveredAt().plusMinutes(5));
        userGet(USER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.items[0].phase").value("REJECTED_DONE"));
    }

    // ------------------------------------------------------------------ 엑셀(#13)

    @Test
    @DisplayName("재발송 목록 다운로드 — 고른 컬럼 순서 뒤에 빈 택배사·송장번호 2열이 붙고, 보낼 물건이 실리고, 반출 이력과 컬럼 기본값이 남는다(#13)")
    void export() throws Exception {
        Long exchange = passed(exchangeClaim(deliveredGroup()));
        Long rejected = received(returnClaim(deliveredGroup()));
        sellerPost(CLAIMS + "/" + rejected + "/inspection/reject", rejectBody()).andExpect(status().isOk());
        claimService.markReshipFeePaid(chargeId(), "clm-test-1", consumer.getId(), LocalDateTime.now());
        Long collecting = returnClaim(deliveredGroup());

        sellerGet(CLAIMS + "/reshipments/export/template").andExpect(status().isOk())
                .andExpect(jsonPath("$.columns", hasSize(7)))
                .andExpect(jsonPath("$.columns[0]").value("CLAIM_NUMBER"))
                .andExpect(jsonPath("$.available", hasSize(ClaimReshipColumn.values().length)));

        List<String> columns = List.of("RESHIP_REASON", "CLAIM_NUMBER", "RECIPIENT", "OPTION", "RESHIP_REASON");
        byte[] file = sellerPost(CLAIMS + "/reshipments/export", Map.of("columns", columns, "saveAsDefault", true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        List<List<String>> sheet = readSheet(file);
        assertThat(sheet.get(0)).containsExactly(ClaimReshipColumn.RESHIP_REASON.getHeader(), "접수번호",
                ClaimReshipColumn.RECIPIENT.getHeader(), ClaimReshipColumn.OPTION.getHeader(), "택배사", "송장번호");
        assertThat(sheet).hasSize(3);
        assertThat(sheet.subList(1, 3)).extracting(row -> row.get(0) + "|" + row.get(1))
                .containsExactlyInAnyOrder("교환 재발송|CLM-" + exchange, "반려 반송|CLM-" + rejected);
        assertThat(sheet.get(1).get(2)).isEqualTo("김수민");
        assertThat(jdbc.queryForMap("SELECT * FROM purchase_order_download_log WHERE kind = 'CLAIM_RESHIP'"))
                .containsEntry("delivery_group_count", 2);
        sellerGet(CLAIMS + "/reshipments/export/template")
                .andExpect(jsonPath("$.columns", contains("RESHIP_REASON", "CLAIM_NUMBER", "RECIPIENT", "OPTION")));

        // 선택 건 — 재발송 대기가 아닌 건은 조용히 빠지고, 남는 것이 없으면 400.
        assertThat(readSheet(sellerPost(CLAIMS + "/reshipments/export",
                Map.of("columns", List.of("CLAIM_NUMBER"), "claimIds", List.of(exchange, collecting)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray())).hasSize(2);
        sellerPost(CLAIMS + "/reshipments/export",
                Map.of("columns", List.of("CLAIM_NUMBER"), "claimIds", List.of(collecting)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLAIM_EXPORT_EMPTY"));
        sellerPost(CLAIMS + "/reshipments/export", Map.of("columns", List.of()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(CLAIMS + "/reshipments/export/template").header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("columns", List.of("PHONE", "CLAIM_NUMBER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns", contains("PHONE", "CLAIM_NUMBER")));
        assertThat(count("market_purchase_order_template")).isEqualTo(1);
    }

    @Test
    @DisplayName("재발송 송장 업로드 검증 — 머리글로 열을 찾아 행별로 분류만 하고 상태는 바꾸지 않는다. 없는 번호와 남의 번호는 같은 사유다(#13)")
    void parse() throws Exception {
        Long first = passed(exchangeClaim(deliveredGroup()));
        Long second = passed(exchangeClaim(deliveredGroup()));
        Long collecting = returnClaim(deliveredGroup());
        Long blank = passed(exchangeClaim(deliveredGroup()));

        // 열 순서는 상관없다 — 다른 열이 섞여 있어도 된다.
        byte[] file = xlsx(List.of(
                new String[]{"수취인", "송장번호", "접수번호", "택배사"},
                new String[]{"김수민", "6400-1234-5678", "CLM-" + first, "CJ대한통운"},
                new String[]{"김수민", "640099990000", "clm-" + first, "CJ"},
                new String[]{"", "", "", ""},
                new String[]{"김수민", "640011112222", "CLM-999999", "CJ"},
                new String[]{"김수민", "640033334444", "CLM-" + second, "없는택배"},
                new String[]{"김수민", "640055556666", "CLM-" + collecting, "CJ"},
                new String[]{"김수민", "", "CLM-" + blank, "CJ"},
                new String[]{"김수민", "640012345678", "CLM-" + blank, ""}));

        upload(file).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(7))
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.rows[0].rowNumber").value(2))
                .andExpect(jsonPath("$.rows[0].valid").value(true))
                .andExpect(jsonPath("$.rows[0].claimId").value(first))
                .andExpect(jsonPath("$.rows[0].carrier").value("CJ"))
                .andExpect(jsonPath("$.rows[0].trackingNumber").value("640012345678"))
                .andExpect(jsonPath("$.rows[1].errorCode").value("CLAIM_DUPLICATE_IN_FILE"))
                .andExpect(jsonPath("$.rows[2].errorCode").value("CLAIM_NOT_FOUND"))
                .andExpect(jsonPath("$.rows[2].claimId").value(nullValue()))
                .andExpect(jsonPath("$.rows[3].errorCode").value("CARRIER_INVALID"))
                .andExpect(jsonPath("$.rows[4].errorCode").value("NOT_RESHIP_READY"))
                .andExpect(jsonPath("$.rows[5].errorCode").value("TRACKING_REQUIRED"))
                .andExpect(jsonPath("$.rows[6].errorCode").value("CLAIM_DUPLICATE_IN_FILE"));
        assertThat(claimRow(first)).containsEntry("status", "RESHIP_READY");

        // 이미 재발송 중인 건의 송장과 겹치면 중복으로 가른다.
        sellerPost(CLAIMS + "/reshipments", Map.of("items", List.of(reshipRow(first, "CJ", "640012345678"))))
                .andExpect(jsonPath("$.succeeded").value(1));
        upload(xlsx(List.of(new String[]{"접수번호", "택배사", "송장번호"},
                new String[]{"CLM-" + second, "CJ", "640012345678"})))
                .andExpect(jsonPath("$.rows[0].errorCode").value("INVOICE_DUPLICATE"));

        upload(xlsx(List.of(new String[]{"접수번호", "송장번호"}, new String[]{"CLM-" + second, "640012345678"})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLAIM_UPLOAD_HEADER_MISSING"));
        upload("xlsx 아님".getBytes()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SHIPMENT_FILE_INVALID"));
    }

    // ------------------------------------------------------------------ 거절 보류 — 고지 · 폐기(#20 ~ #23)

    @Test
    @DisplayName("거절 보류 — 고지 2회 + 최종 고지 후 3개월이 지나야 폐기를 기록할 수 있다. 폐기하면 청구가 소멸하고 결제도 재발송 조회도 닫힌다(#20 ~ #23)")
    void storageNoticeAndDispose() throws Exception {
        Long claimId = received(returnClaim(deliveredGroup()));
        sellerPost(CLAIMS + "/" + claimId + "/inspection/reject", rejectBody()).andExpect(status().isOk());
        LocalDateTime now = LocalDateTime.now().withNano(0);

        assertDisposeBlocked(claimId, now);
        assertThat(claimService.recordStorageNotice(claimId, "PUSH", FulfillmentActorType.ADMIN, 1L,
                now.minusMonths(5))).isEqualTo(1);
        // 고지 1회로는 보관 기한이 생기지 않는다.
        assertDisposeBlocked(claimId, now);
        sellerGet(CLAIMS + "/" + claimId).andExpect(jsonPath("$.summary.storage.noticeCount").value(1))
                .andExpect(jsonPath("$.summary.storage.phase").value("NOTICE_PENDING"))
                .andExpect(jsonPath("$.summary.storage.storageDueAt").value(nullValue()));

        assertThat(claimService.recordStorageNotice(claimId, "PUSH", FulfillmentActorType.ADMIN, 1L,
                now.minusMonths(2))).isEqualTo(2);
        // 최종 고지에서 3개월이 안 지났다.
        assertDisposeBlocked(claimId, now);
        sellerGet(CLAIMS + "/" + claimId).andExpect(jsonPath("$.summary.storage.phase").value("STORING"));
        assertThat(count("order_claim_notice")).isEqualTo(2);
        assertThat(events(claimId)).contains("STORAGE_NOTICE_SENT");

        // 보관 중에는 결제가 곧 반환 요청이다 — 결제 기한이 지났어도 결제창이 열린다.
        jdbc.update("UPDATE order_claim_charge SET due_at = ?", now.minusMonths(4));
        userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments", Map.of("method", "CARD", "cardIssuer", "SHINHAN"))
                .andExpect(status().isOk());

        claimService.disposeAfterStorage(claimId, 1L, now.plusMonths(2));

        assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "REJECTED");
        assertThat(claimRow(claimId).get("disposed_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM order_claim_charge", String.class)).isEqualTo("VOID");
        assertThat(events(claimId)).contains("DISPOSED");
        userGet(USER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.items[0].phase").value("REJECTED_DISPOSED"));
        userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments", Map.of("method", "CARD", "cardIssuer", "SHINHAN"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_PAYMENT_NOT_REQUIRED"));
        userGet(USER_CLAIMS + "/" + claimId + "/reship-tracking").andExpect(status().isConflict());
        // 종결된 건에는 고지를 더 기록하지 않는다.
        assertThatThrownBy(() -> claimService.recordStorageNotice(claimId, "PUSH", FulfillmentActorType.ADMIN, 1L,
                now)).isInstanceOf(BusinessException.class);
    }

    private void assertDisposeBlocked(Long claimId, LocalDateTime now) {
        assertThatThrownBy(() -> claimService.disposeAfterStorage(claimId, 1L, now))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLAIM_STORAGE_NOT_EXPIRED));
        assertThat(claimRow(claimId)).containsEntry("status", "REJECT_HOLD");
    }

    // ------------------------------------------------------------------ 픽스처

    private OrderDeliveryGroup deliveredGroup() throws Exception {
        return delivered(shipped(prepared(paidGroup(creamVariant, 1)), "CJ", newInvoice()),
                LocalDateTime.now().minusHours(1).withNano(0));
    }

    private static String newInvoice() {
        return String.valueOf(100_000_000_000L + (long) (Math.random() * 899_999_999_999L));
    }

    private List<Item> allItems(OrderDeliveryGroup group) {
        return items(group).stream().map(p -> new Item(p.getId(), p.getQuantity())).toList();
    }

    /** 고객 귀책 반품 — 회수 중으로 시작. */
    private Long returnClaim(OrderDeliveryGroup group) {
        return claimService.request(new RequestCommand(consumer.getId(), group.getId(), ClaimType.RETURN,
                ClaimReason.CHANGE_OF_MIND, null, List.of(), allItems(group),
                new Invoice(DeliveryCarrier.CJ, newInvoice()), null), LocalDateTime.now()).claimIds().get(0);
    }

    /** 브랜드 귀책 교환(받은 옵션 그대로 · 결제 없음) — 회수 중으로 시작. */
    private Long exchangeClaim(OrderDeliveryGroup group) {
        OrderProduct product = items(group).get(0);
        return claimService.request(new RequestCommand(consumer.getId(), group.getId(), ClaimType.EXCHANGE,
                ClaimReason.DAMAGED_OR_DEFECTIVE, "뚜껑이 깨져서 왔어요", List.of(),
                List.of(new Item(product.getId(), product.getQuantity(), creamVariant.getVariantId())),
                new Invoice(DeliveryCarrier.CJ, newInvoice()), null), LocalDateTime.now()).claimIds().get(0);
    }

    private Long received(Long claimId) throws Exception {
        sellerPost(CLAIMS + "/receive", Map.of("claimIds", List.of(claimId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.succeeded").value(1));
        return claimId;
    }

    /** 입고 확인 → 검수 통과. */
    private Long passed(Long claimId) throws Exception {
        sellerPost(CLAIMS + "/" + received(claimId) + "/inspection/pass", Map.of()).andExpect(status().isOk());
        return claimId;
    }

    /** 반려 6항목(1009 기획 수정본 5-b) — 법적 근거 · 소비자 메시지가 필수다. */
    private static Map<String, Object> rejectBody() {
        return Map.of("reasonCode", "USED", "detail", "용기 입구에 사용 흔적이 있습니다.",
                "legalBasis", "ART17_2_2", "consumerMessage", "용기 입구에 사용 흔적이 있습니다.",
                "evidenceImageUrls", List.of("https://img.test/e1.jpg"));
    }

    private static Map<String, Object> reshipRow(Long claimId, String carrier, String trackingNumber) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("claimId", claimId);
        row.put("carrier", carrier);
        row.put("trackingNumber", trackingNumber);
        return row;
    }

    private static TrackEvent scan(LocalDateTime at, String location, String description, int level) {
        return new TrackEvent(at, location, description, level);
    }

    private static TrackSnapshot snapshot(LocalDateTime lastEventAt, LocalDateTime deliveredAt, TrackEvent... events) {
        return new TrackSnapshot(lastEventAt, deliveredAt, false, false, List.of(events),
                events.length == 0 ? null : events[events.length - 1].level());
    }

    /** 문자열 셀만 든 xlsx — 첫 행이 머리글이다. */
    private static byte[] xlsx(List<String[]> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows.get(r).length; c++) {
                    row.createCell(c).setCellValue(rows.get(r)[c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private ResultActions upload(byte[] file) throws Exception {
        return mockMvc.perform(multipart(CLAIMS + "/reshipments/parse")
                .file(new MockMultipartFile("file", "reship.xlsx", XLSX, file))
                .header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    private ResultActions userGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private ResultActions userPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private Long chargeId() {
        return jdbc.queryForObject("SELECT charge_id FROM order_claim_charge", Long.class);
    }

    private Map<String, Object> claimRow(Long claimId) {
        return jdbc.queryForMap("SELECT * FROM order_claim WHERE claim_id = ?", claimId);
    }

    private List<String> events(Long claimId) {
        return jdbc.queryForList("SELECT event_type FROM order_claim_history WHERE claim_id = ? "
                + "ORDER BY claim_history_id", String.class, claimId);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
