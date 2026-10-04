package showroomz.api.scenario;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import showroomz.api.admin.contract.AdminContractTestSupport;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.contract.dto.ContractReviewRequestRequest;
import showroomz.api.seller.contract.dto.ContractUpdateRequest;
import showroomz.domain.address.entity.DeliveryAddress;
import showroomz.domain.address.repository.DeliveryAddressRepository;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractHistory;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.contract.type.SecondaryUsePeriodType;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.service.GroupBuyLifecycleService;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderFulfillmentHistoryRepository;
import showroomz.domain.order.service.OrderFulfillmentService;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.global.payment.portone.FakePaymentGateway;
import showroomz.global.payment.portone.PortOnePaymentGateway;
import showroomz.support.BrandFixture;
import showroomz.support.ContractOptions;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 전체 플로우 통합 테스트 시나리오 E2E-0 — <b>한 거래의 일생</b>(dev/전체플로우_통합테스트_시나리오.md 2절).
 *
 * <p>연결 → 계약 체결 → 공구 준비·오픈 → 공구 게시물 → 소비자 구매·결제 → 파트너센터 주문관리 → 배송완료 →
 * 공구 종료·이행 확인 → 구매확정 → 정산 게이트까지, 쓰기는 전부 <b>각 서피스의 실제 API</b>로 한다.
 * 공구 생성도 적재하지 않는다 — 운영자 체결 API 가 만든 공구를 그대로 이어 쓴다.
 *
 * <p>예외는 사람이 없는 구간뿐이다(시나리오 5절). 시각 컬럼(공구 시작·종료, 배송완료 시각)만 SQL로 당기고,
 * 상태를 움직이는 것은 스케줄러가 부르는 서비스(수명주기 · 추적 반영 · 구매확정)를 직접 부른다.
 * 상태 컬럼을 SQL로 바꾸면 이력·부수 효과가 빠지므로 쓰지 않는다.
 *
 * <p>이음새 핵심 셋을 단계 안에서 함께 본다 — ① 체결↔공구 생성 ② 결제↔하위주문 활성화 ③ 구매확정↔정산 게이트.
 * ③은 시나리오 순서와 달리 <b>공구 종료를 구매확정보다 먼저</b> 두어, 미종결 주문이 정산을 막았다가
 * 구매확정이 그 차단을 푸는 것까지 한 흐름에서 확인한다.
 */
@DisplayName("[시나리오 E2E-0] 한 거래의 일생 — 연결부터 정산 게이트까지")
class FullFlowEndToEndIntegrationTest extends AdminContractTestSupport {

    private static final String CONNECTIONS = "/v1/seller/connections";
    private static final String CREATOR_CONNECTIONS = "/v1/creator/connections";
    private static final String SELLER_GROUP_BUYS = "/v1/seller/group-buys";
    private static final String STUDIO_GROUP_BUYS = "/v1/creator/group-buys";
    private static final String ADMIN_GROUP_BUYS = "/v1/admin/group-buys";
    private static final String USER_ORDERS = "/v1/user/orders";
    private static final String USER_PAYMENTS = "/v1/user/payments";
    private static final String SELLER_ORDERS = "/v1/seller/orders";

    private static final int CREAM_GROUP_BUY_PRICE = 38_400;
    private static final int SERUM_GROUP_BUY_PRICE = 25_600;
    private static final int DELIVERY_FEE = 3_000;
    private static final int SHIPPING_LEAD_DAYS = 2;
    private static final String POST_TITLE = "리페어 크림 겨울 공구 오픈";
    private static final String POST_CONTENT = "건조한 계절에 한 달 써 보고 고른 크림입니다.";

    @Autowired private GroupBuyRepository groupBuyRepository;
    @Autowired private GroupBuyPostRepository groupBuyPostRepository;
    @Autowired private GroupBuyLifecycleService lifecycleService;
    @Autowired private OrderDeliveryGroupRepository deliveryGroupRepository;
    @Autowired private OrderFulfillmentHistoryRepository fulfillmentHistoryRepository;
    @Autowired private OrderFulfillmentService fulfillmentService;
    @Autowired private DeliveryAddressRepository deliveryAddressRepository;
    @Autowired private PortOnePaymentGateway gateway;

    private FakePaymentGateway fake;
    private Creator partner;
    private String partnerToken;
    private Users buyer;
    private String buyerToken;

    @BeforeEach
    void setUpDeal() {
        fake = (FakePaymentGateway) gateway;
        fake.reset();
        // 지원 클래스가 깐 연결됨 인플루언서와 별개로, 연결부터 API로 맺을 상대를 둔다.
        partner = createCreator("글로우_지민", "jimin");
        partnerToken = bearerToken(partner.getUser().getUsername(), RoleType.CREATOR, partner.getUser().getId());
        jdbc.update("UPDATE market SET default_delivery_fee = ?, free_shipping_threshold = ?, shipping_lead_days = ? "
                + "WHERE market_id = ?", DELIVERY_FEE, 50_000, SHIPPING_LEAD_DAYS, brand.marketId());
        buyer = createBuyer();
        buyerToken = bearerToken(buyer.getUsername(), RoleType.USER, buyer.getId());
    }

    @AfterEach
    void resetFake() {
        fake.reset();
    }

    @Test
    @DisplayName("연결 → 체결(공구 자동 생성) → 준비·오픈 → 게시물 노출 → 결제(하위주문 탄생) → 발주서·송장 → 배송완료 → 공구 종료·이행 확인 → 구매확정 → 정산 게이트")
    void oneDealLifecycle() throws Exception {
        // ── [0-1] 연결 ────────────────────────────────────────────────────────
        long connectionId = readLong(body(sellerSend(post(CONNECTIONS), Map.of("creatorId", partner.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))), "$.connectionId");
        studioSend(post(CREATOR_CONNECTIONS + "/" + connectionId + "/accept"), null).andExpect(status().isNoContent());

        // ── [0-2] 계약 작성·검토 요청 ─────────────────────────────────────────
        long contractId = readLong(body(sellerSend(post(SELLER_CONTRACTS), Map.of("creatorId", partner.getId()))
                .andExpect(status().isCreated())), "$.contractId");
        long version = readLong(body(sellerSend(get(SELLER_CONTRACTS + "/" + contractId), null)), "$.version");
        sellerSend(put(SELLER_CONTRACTS + "/" + contractId), contractForm(version)).andExpect(status().isOk());
        sellerSend(post(SELLER_CONTRACTS + "/" + contractId + "/review-request"),
                new ContractReviewRequestRequest(List.of())).andExpect(status().isOk());
        Contract contract = contracts.findById(contractId).orElseThrow();
        // 운영자 검토 큐에 도착한다. 인플루언서에게는 아직 도착하지 않은 계약이다(서명 요청 발송부터 보인다).
        detail(contract).andExpect(status().isOk()).andExpect(jsonPath("$.contract.status").value("REVIEW_PENDING"));
        partnerContract(contractId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_RECEIVED"));

        // ── [0-3] 승인 · 서명 · 체결 — 체결 트랜잭션이 공구를 만든다(이음새 ①) ─────────
        LocalDateTime sentAt = LocalDateTime.now();
        approve(contract, checked(sentAt, sentAt.plusDays(7).withHour(23).withMinute(55).withSecond(0).withNano(0)))
                .andExpect(status().isOk());
        partnerContract(contractId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SIGNING"));
        LocalDateTime brandSignedAt = LocalDateTime.now();
        signatures(contract, brandSignedAt, LocalDateTime.now(), versionOf(contract))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONCLUSION_PENDING"));
        upload(contract, ContractDocumentType.SIGNED_PDF, "서명완료_리페어크림공구.pdf").andExpect(status().isOk());
        upload(contract, ContractDocumentType.AUDIT_TRAIL, "감사추적인증서.pdf").andExpect(status().isOk());
        conclude(contract).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUDED"))
                .andExpect(jsonPath("$.groupBuyNumber").value(startsWith("GB-")));

        GroupBuy created = groupBuyRepository.findByContractId(contractId).orElseThrow();
        long groupBuyId = created.getId();
        assertThat(created.getStatus()).isEqualTo(GroupBuyStatus.PREPARING);
        assertThat(reload(contract).getGroupBuyId()).isEqualTo(groupBuyId);
        assertThat(productGroupBuyStatus(cream.getProductId())).isEqualTo(ProductGroupBuyStatus.PREPARING);
        assertThat(historyOf(contract)).extracting(ContractHistory::getEventType)
                .containsSubsequence(ContractEventType.CONCLUDED, ContractEventType.GROUP_BUY_CREATED);
        sellerSend(get(SELLER_GROUP_BUYS + "/" + groupBuyId), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("PREPARING"))
                .andExpect(jsonPath("$.history[*].eventType").value(hasItem("CREATED")));

        // ── [0-4]·[0-5] 준비 게이트 ① 물량 확보 · ② 게시물 제출 · ③ 오픈 승인 ───────────
        sellerSend(post(SELLER_GROUP_BUYS + "/" + groupBuyId + "/stock-confirmation"), null).andExpect(status().isOk());
        studioSend(post(STUDIO_GROUP_BUYS + "/" + groupBuyId + "/post/submission"),
                Map.of("title", POST_TITLE, "content", POST_CONTENT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.post.status").value("PENDING_APPROVAL"));
        adminSend(post(ADMIN_GROUP_BUYS + "/" + groupBuyId + "/open-review/approve"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        sellerSend(get(SELLER_GROUP_BUYS + "/" + groupBuyId), null)
                .andExpect(jsonPath("$.groupBuy.status").value("READY"))
                .andExpect(jsonPath("$.post.status").value("SCHEDULED"));

        // ── [0-6] 자동 오픈 — 시작 버튼은 어디에도 없다. 시작 시각에 스케줄러가 연다 ─────────
        openAtStart(groupBuyId);
        assertThat(productGroupBuyStatus(cream.getProductId())).isEqualTo(ProductGroupBuyStatus.IN_PROGRESS);

        // ── [0-7] 소비자에게 게시물이 나간다 — 대가관계 표시 자동 삽입 · 공구가 ───────────
        Long postId = groupBuyPostRepository.findByGroupBuyId(groupBuyId).orElseThrow().getPostId();
        mockMvc.perform(get("/v1/user/showrooms/posts/" + postId).header(HttpHeaders.AUTHORIZATION, buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("GROUP_BUY"))
                .andExpect(jsonPath("$.groupBuy.groupBuyId").value(groupBuyId))
                .andExpect(jsonPath("$.groupBuy.title").value(POST_TITLE))
                .andExpect(jsonPath("$.groupBuy.adDisclosure.text").value(startsWith("유료 광고 포함")));

        // ── [0-8] 주문 생성 — 금액은 서버가 계산한다(공구가 + 배송비) · 재고 예약 ─────────
        ProductVariant creamVariant = ContractOptions.variantsOf(variants, cream).get(0);
        int stockBefore = stockOf(creamVariant);
        String createdOrder = body(buyerSend(post(USER_ORDERS), Map.of(
                "idempotencyKey", UUID.randomUUID().toString(),
                "direct", Map.of("variantId", creamVariant.getVariantId(), "quantity", 1, "groupBuyId", groupBuyId),
                "payment", Map.of("method", "CARD", "cardIssuer", "SHINHAN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.payment.totalAmount").value(CREAM_GROUP_BUY_PRICE + DELIVERY_FEE)));
        long orderId = readLong(createdOrder, "$.orderId");
        String paymentId = readString(createdOrder, "$.payment.paymentId");
        assertThat(stockOf(creamVariant)).isEqualTo(stockBefore - 1);

        // ── [0-9] 결제 확정 — 같은 트랜잭션에서 하위주문이 태어난다(이음새 ②) ──────────────
        buyerSend(post(USER_PAYMENTS + "/" + paymentId + "/complete"), Map.of()).andExpect(status().isOk());
        List<OrderDeliveryGroup> groups = deliveryGroupRepository.findByOrderId(orderId);
        assertThat(groups).hasSize(1);
        OrderDeliveryGroup group = reloadGroup(groups.get(0).getId());
        assertThat(group.getOrder().getStatus().name()).isEqualTo("PAID");
        assertThat(group.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
        assertThat(group.getSubOrderNumber()).isEqualTo(group.getOrder().getOrderNumber() + "-01");
        assertThat(group.getShipDueAt()).isEqualTo(group.getOrder().getPaidAt().plusDays(SHIPPING_LEAD_DAYS));
        assertThat(fulfillmentEvents(group.getId())).containsExactly("PAID");
        long groupId = group.getId();
        // 공구 화면도 같은 주문을 실값으로 읽는다 — 판매 실적 · 미종결 1건.
        sellerSend(get(SELLER_GROUP_BUYS + "/" + groupBuyId), null)
                .andExpect(jsonPath("$.sales.orderCount").value(1))
                .andExpect(jsonPath("$.sales.amount").value(CREAM_GROUP_BUY_PRICE))
                .andExpect(jsonPath("$.orderClosure.unclosedCount").value(1));
        appOrder(orderId).andExpect(jsonPath("$.cancellable").value(true));

        // ── [0-10] 신규 탭 — 소비자 실명 · 발송기한 · 요약 바. 타 브랜드에는 없다 ────────────
        sellerSend(get(SELLER_ORDERS + "?tab=NEW"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].deliveryGroupId").value(groupId))
                .andExpect(jsonPath("$.content[0].recipientName").value("김수민"))
                .andExpect(jsonPath("$.content[0].groupBuyName").value("겨울 리페어 크림 공구"))
                .andExpect(jsonPath("$.content[0].shipDueAt").exists())
                .andExpect(jsonPath("$.content[0].overlays.shipOverdue").value(false));
        sellerSend(get(SELLER_ORDERS + "/summary"), null)
                .andExpect(jsonPath("$.actionBar.prepareStart").value(1))
                .andExpect(jsonPath("$.tabCounts.NEW").value(1));
        BrandFixture.Brand otherBrand = fixture.createBrand("other-brand@showroomz.test", "다른브랜드");
        String otherToken = sellerToken(otherBrand.seller());
        mockMvc.perform(get(SELLER_ORDERS).header(HttpHeaders.AUTHORIZATION, otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get(SELLER_ORDERS + "/" + groupId).header(HttpHeaders.AUTHORIZATION, otherToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_GROUP_NOT_FOUND"));

        // ── [0-11] 발주서 다운로드 + 준비 시작 동시 처리 — 소비자 취소권이 닫힌다 ───────────
        byte[] purchaseOrder = sellerSend(post(SELLER_ORDERS + "/purchase-order"), Map.of(
                "columns", List.of("ORDER_NUMBER", "RECIPIENT", "PHONE", "ZIP_CODE", "ADDRESS",
                        "PRODUCT_NAME", "OPTION", "QUANTITY"),
                "startPreparation", true))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        List<List<String>> sheet = readSheet(purchaseOrder);
        assertThat(sheet.get(0)).containsExactly("주문번호", "수취인", "연락처", "우편번호", "주소", "상품명", "옵션", "수량");
        assertThat(sheet).hasSize(2);
        assertThat(sheet.get(1).get(0)).isEqualTo(group.getOrder().getOrderNumber());
        assertThat(sheet.get(1).get(1)).isEqualTo("김수민");
        assertThat(sheet.get(1).get(2)).isEqualTo("010-1234-5678");
        assertThat(sheet.get(1).get(4)).isEqualTo("서울 강남구 테헤란로 000 쇼룸타워 12층");
        assertThat(sheet.get(1).get(5)).isEqualTo(cream.getName());
        assertThat(sheet.get(1).get(7)).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order_download_log WHERE market_id = ?",
                Integer.class, brand.marketId())).isEqualTo(1);
        assertThat(reloadGroup(groupId).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
        assertThat(fulfillmentEvents(groupId)).contains("PREPARE_STARTED");
        // 앱은 「주문 취소」 대신 취소 요청 경로로 — 버튼 판정과 서버 게이트가 같은 답을 낸다.
        appOrder(orderId).andExpect(jsonPath("$.cancellable").value(false));
        buyerSend(post(USER_ORDERS + "/" + orderId + "/cancel"), Map.of("reason", "단순 변심"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CANCEL_WINDOW_CLOSED"));

        // ── [0-12] 송장 등록 — 숫자만 저장 · shipped_at 이 발송기한 판정값으로 박힌다 ─────────
        sellerSend(post(SELLER_ORDERS + "/shipments"), Map.of("rows", List.of(Map.of(
                "deliveryGroupId", groupId, "carrier", "CJ", "trackingNumber", "1234-5678-9012"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.succeeded").value(1));
        OrderDeliveryGroup shipped = reloadGroup(groupId);
        assertThat(shipped.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
        assertThat(shipped.getTrackingNumber()).isEqualTo("123456789012");
        assertThat(shipped.getShippedAt()).isNotNull();

        // ── [0-13] 배송완료 — 추적이 만든다(스텁 기간은 추적 반영 서비스로 재현). 브랜드 경로는 없다 ──
        // 초 미만을 자르지 않는다 — 같은 초 안의 송장 등록보다 앞선 시각으로 이력이 박히면 최신순이 뒤집힌다.
        LocalDateTime now = LocalDateTime.now();
        fulfillmentService.applyTracking(reloadGroup(groupId), Optional.of(new TrackSnapshot(now, now, false, false)),
                now, 24, 7);
        sellerSend(get(SELLER_ORDERS + "/" + groupId), null)
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.timeline.deliveredSourceLabel").value("자동 확인"))
                .andExpect(jsonPath("$.actions.canUpdateInvoice").value(false));
        sellerSend(get(SELLER_ORDERS + "?tab=DELIVERED"), null)
                .andExpect(jsonPath("$.content[0].deliveredSourceLabel").value("자동 확인"))
                .andExpect(jsonPath("$.content[0].confirmRemainingDays").value(7));

        // ── [0-15] 공구 종료 — 진행 중이던 주문 처리는 그대로 이어진다 ────────────────────
        endByPeriod(groupBuyId);
        assertThat(productGroupBuyStatus(cream.getProductId())).isEqualTo(ProductGroupBuyStatus.NOT_CONNECTED);

        // ── [0-16] 이행 확인 — 측별 1회 ─────────────────────────────────────────
        sellerSend(post(SELLER_GROUP_BUYS + "/" + groupBuyId + "/fulfillment-check"), Map.of("result", "FULFILLED"))
                .andExpect(status().isOk());
        studioSend(post(STUDIO_GROUP_BUYS + "/" + groupBuyId + "/fulfillment-check"), Map.of("result", "FULFILLED"))
                .andExpect(status().isOk());

        // ── [0-17 전] 정산 게이트 — 구매확정 전 미종결 1건이 정산을 막는다(이음새 ③) ─────────
        adminSend(get(ADMIN_GROUP_BUYS + "/" + groupBuyId), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.afterEnd.orderClosure.totalCount").value(1))
                .andExpect(jsonPath("$.afterEnd.orderClosure.unclosedCount").value(1))
                .andExpect(jsonPath("$.afterEnd.settlement.blockers.length()").value(1))
                .andExpect(jsonPath("$.afterEnd.settlement.blockers[0]").value("UNCLOSED_ORDERS"))
                .andExpect(jsonPath("$.permissions.canConfirmSettlement").value(false));

        // ── [0-14] 구매확정 — 배송완료 + 7일. 시각만 당기고 배치가 부르는 서비스를 부른다 ─────
        jdbc.update("UPDATE order_delivery_group SET delivered_at = ? WHERE delivery_group_id = ?",
                Timestamp.valueOf(now.minusDays(8)), groupId);
        LocalDateTime confirmAt = LocalDateTime.now();
        assertThat(fulfillmentService.findIdsToConfirm(confirmAt.minusDays(7), 100)).contains(groupId);
        assertThat(fulfillmentService.confirmPurchase(groupId, confirmAt, confirmAt.minusDays(7))).isTrue();
        OrderDeliveryGroup confirmed = reloadGroup(groupId);
        assertThat(confirmed.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);
        assertThat(jdbc.queryForObject("SELECT status FROM order_product WHERE delivery_group_id = ?",
                String.class, groupId)).isEqualTo(OrderProductStatus.PURCHASE_CONFIRMED.name());
        sellerSend(get(SELLER_ORDERS + "?tab=CONFIRMED"), null)
                .andExpect(jsonPath("$.content[0].paidAmount").value(CREAM_GROUP_BUY_PRICE + DELIVERY_FEE))
                .andExpect(jsonPath("$.content[0].settlementLabel").isEmpty());
        assertThat(fulfillmentEvents(groupId)).containsSubsequence(
                "PURCHASE_CONFIRMED", "DELIVERED", "INVOICE_REGISTERED", "PREPARE_STARTED", "PAID");

        // ── [0-17] 정산 게이트 — 미종결 0 · 차단 사유 없음. 확정 버튼은 정산 모듈 연결 후(시나리오 7절) ──
        adminSend(get(ADMIN_GROUP_BUYS + "/" + groupBuyId), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.afterEnd.orderClosure.closedCount").value(1))
                .andExpect(jsonPath("$.afterEnd.orderClosure.unclosedCount").value(0))
                .andExpect(jsonPath("$.afterEnd.settlement.blockers.length()").value(0))
                .andExpect(jsonPath("$.permissions.canConfirmSettlement").value(false));
    }

    // ------------------------------------------------------------------ 흐름

    /** 하드 검증·경고를 하나도 건드리지 않는 조건 — 시작 D+10 · 8일 · 고정 지급비 300,000(고지 확인) · 리워드율 12/10%. */
    private ContractUpdateRequest contractForm(long version) {
        LocalDateTime startAt = LocalDateTime.now().plusDays(10).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endAt = startAt.plusDays(7).withHour(23).withMinute(55);
        return new ContractUpdateRequest(version, partner.getId(), "겨울 리페어 크림 공구", startAt, endAt,
                300_000, FixedFeeTrigger.POST_REGISTERED, true, 1, 1, 3, endAt.toLocalDate().plusDays(3),
                true, SecondaryUsePeriodType.FIXED, 12, false, null,
                List.of(new ContractUpdateRequest.Item(null, cream.getProductId(), CREAM_GROUP_BUY_PRICE,
                                new BigDecimal("12.0"), options(cream, 300)),
                        new ContractUpdateRequest.Item(null, serum.getProductId(), SERUM_GROUP_BUY_PRICE,
                                new BigDecimal("10.0"), options(serum, 200))));
    }

    /** 시작 시각을 1분 전으로 당기고 스케줄러의 오픈 단계를 부른다. */
    private void openAtStart(long groupBuyId) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE group_buy SET start_at = ? WHERE group_buy_id = ?",
                Timestamp.valueOf(now.minusMinutes(1)), groupBuyId);
        assertThat(lifecycleService.open(groupBuyId, now)).isTrue();
        assertThat(groupBuyRepository.findById(groupBuyId).orElseThrow().getStatus())
                .isEqualTo(GroupBuyStatus.IN_PROGRESS);
    }

    /** 종료 시각을 1초 전으로 당기고 스케줄러의 종료 단계를 부른다. */
    private void endByPeriod(long groupBuyId) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?",
                Timestamp.valueOf(now.minusSeconds(1)), groupBuyId);
        assertThat(lifecycleService.end(groupBuyId, now)).isTrue();
        assertThat(groupBuyRepository.findById(groupBuyId).orElseThrow().getStatus()).isEqualTo(GroupBuyStatus.ENDED);
    }

    // ------------------------------------------------------------------ 픽스처 · 읽기

    private Users createBuyer() {
        LocalDateTime at = LocalDateTime.now();
        Users user = new Users("sumin", "김수민", "sumin@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, at, at);
        user.setName("김수민");
        user.setPhoneNumber("010-1234-5678");
        Users saved = users.save(user);
        deliveryAddressRepository.save(DeliveryAddress.builder()
                .user(saved).recipientName("김수민").zipCode("06234").address("서울 강남구 테헤란로 000")
                .detailAddress("쇼룸타워 12층").phoneNumber("010-1234-5678").memo("문 앞에 놓아주세요")
                .isDefault(true).build());
        return saved;
    }

    private OrderDeliveryGroup reloadGroup(Long deliveryGroupId) {
        return deliveryGroupRepository.findOwned(deliveryGroupId, brand.marketId()).orElseThrow();
    }

    private List<String> fulfillmentEvents(Long deliveryGroupId) {
        return fulfillmentHistoryRepository.findByDeliveryGroupId(deliveryGroupId).stream()
                .map(h -> h.getEventType().name())
                .toList();
    }

    private ProductGroupBuyStatus productGroupBuyStatus(Long productId) {
        return products.findById(productId).orElseThrow().getGroupBuyStatus();
    }

    private int stockOf(ProductVariant variant) {
        return jdbc.queryForObject("SELECT stock FROM product_variant WHERE variant_id = ?", Integer.class,
                variant.getVariantId());
    }

    private List<List<String>> readSheet(byte[] xlsx) throws Exception {
        DataFormatter formatter = new DataFormatter();
        List<List<String>> rows = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    cells.add(formatter.formatCellValue(row.getCell(c)));
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    // ------------------------------------------------------------------ 서피스별 요청

    private ResultActions sellerSend(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mockMvc.perform(withBody(request.header(HttpHeaders.AUTHORIZATION, brandToken), body));
    }

    private ResultActions studioSend(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mockMvc.perform(withBody(request.header(HttpHeaders.AUTHORIZATION, partnerToken), body));
    }

    private ResultActions adminSend(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mockMvc.perform(withBody(request.header(HttpHeaders.AUTHORIZATION, adminToken), body));
    }

    private ResultActions buyerSend(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mockMvc.perform(withBody(request.header(HttpHeaders.AUTHORIZATION, buyerToken), body));
    }

    private ResultActions partnerContract(long contractId) throws Exception {
        return mockMvc.perform(get(CREATOR_CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, partnerToken));
    }

    private ResultActions appOrder(long orderId) throws Exception {
        return buyerSend(get(USER_ORDERS + "/" + orderId), null).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, Object body) {
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
    }
}
