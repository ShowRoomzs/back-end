package showroomz.api.common.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.scenario.OrderFlowTestSupport;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementItem;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.repository.SettlementHistoryRepository;
import showroomz.domain.settlement.repository.SettlementItemRepository;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.service.SettlementAmounts;
import showroomz.domain.settlement.service.SettlementCalculator;
import showroomz.domain.settlement.service.SettlementConfirmService;
import showroomz.domain.settlement.service.SettlementGenerationService;
import showroomz.domain.settlement.service.SettlementInput;
import showroomz.domain.settlement.service.SettlementNumberGenerator;
import showroomz.domain.settlement.service.SettlementPayoutService;
import showroomz.domain.settlement.service.SettlementRates;
import showroomz.domain.settlement.type.SettlementConfirmReason;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.utils.PersonalDataCipher;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.global.utils.BusinessCalendar;
import showroomz.support.ContractOptions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정산 통합 테스트 공용 시드(44 구현 계획서 4-4) — 「공구 종료 + 전 주문 종결 + 정산 생성 + 확정」을 운영과 같은 경로로 만든다.
 *
 * <p>진행 중 공구 1건(크림 27,200 · 리워드 12% / 세럼 24,000 · 리워드 10% · 배송비 3,000 · 무료배송 50,000)과 소비자 1명은
 * {@link OrderFlowTestSupport}가 깐다. 주문은 실제 결제 → 발송 → 배송완료(추적 반영) → 구매확정 경로로만 만든다. 공구 종료 ·
 * 시각 소급만 SQL 이다. 배치는 전부 꺼져 있어 생성 · 확정 · 지급 서비스를 직접 부른다.
 *
 * <p>영업일은 {@link #fixHolidays}로 고정하고 테스트가 끝나면 비운다 — 달력은 컨텍스트 공용 빈이다.
 */
public abstract class SettlementTestSupport extends OrderFlowTestSupport {

    protected static final String ADMIN_ORDERS = "/v1/admin/orders";
    protected static final String ADMIN_REFUNDS = "/v1/admin/refunds";

    @Autowired protected SettlementGenerationService generationService;
    @Autowired protected SettlementRepository settlementRepository;
    @Autowired protected SettlementItemRepository settlementItemRepository;
    @Autowired protected SettlementPayoutRepository payoutRepository;
    @Autowired protected SettlementHistoryRepository settlementHistoryRepository;
    @Autowired protected BusinessCalendar businessCalendar;
    @Autowired protected SettlementConfirmService confirmService;
    @Autowired protected SettlementPayoutService payoutService;
    @Autowired protected FakeSettlementPayoutGateway payoutGateway;
    @Autowired protected SettlementCalculator settlementCalculator;
    @Autowired protected SettlementNumberGenerator settlementNumberGenerator;
    @Autowired protected OrderClaimService claimService;
    @Autowired protected PersonalDataCipher personalDataCipher;
    @Autowired protected FakeSettlementTaxDocumentStorage taxDocumentStorage;
    @Autowired protected FakeSettlementPartnerGateway partnerGateway;

    /** 시드 주민등록번호 — 실제 암호문으로 넣는다(원천징수영수증 · 신고 자료가 복호화한다). */
    protected static final String RESIDENT_NUMBER = "900101-1234567";

    protected Seller operator;
    protected String adminToken;
    protected String creatorToken;
    private int trackingSeq;

    @BeforeEach
    void setUpSettlementFixtures() {
        operator = fixture.createAdmin("settlement-ops@showroomz.test", "김운영");
        adminToken = adminToken(operator);
        creatorToken = bearerToken(creator.getUser().getUsername(), RoleType.CREATOR, creator.getUser().getId());
        payoutGateway.reset();
        partnerGateway.reset();
    }

    @AfterEach
    void resetHolidays() {
        businessCalendar.replaceRegisteredHolidays(Set.of());
        payoutGateway.reset();
        partnerGateway.reset();
    }

    // ------------------------------------------------------------------ 영업일

    /** 2026 하반기 공휴일 — 추석 연휴(09.24 · 09.25) · 개천절 대체(10.05) · 한글날(10.09). */
    protected void fixHolidays() {
        businessCalendar.replaceRegisteredHolidays(Set.of(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9)));
    }

    // ------------------------------------------------------------------ 주문 종결

    /** 구매확정까지 — 결제 → 준비 → 발송 → 배송완료(8일 전) → 구매확정 배치 1회. */
    protected OrderDeliveryGroup confirmedGroup(ProductVariant variant, int quantity) {
        OrderDeliveryGroup group = deliveredFrom(purchase(variant, quantity).group());
        LocalDateTime now = LocalDateTime.now();
        assertThat(fulfillmentService.confirmPurchase(group.getId(), now, now.minusDays(7))).isTrue();
        return reloadGroup(group);
    }

    /** 크림 + 세럼 2항목 하위주문의 구매확정. */
    protected OrderDeliveryGroup confirmedGroupWithTwoItems() {
        OrderDeliveryGroup group = deliveredFrom(paidGroupWithTwoItems());
        LocalDateTime now = LocalDateTime.now();
        assertThat(fulfillmentService.confirmPurchase(group.getId(), now, now.minusDays(7))).isTrue();
        return reloadGroup(group);
    }

    /** 배송완료(8일 전)까지 — 구매확정 전. */
    protected OrderDeliveryGroup deliveredFrom(OrderDeliveryGroup paid) {
        return deliveredFrom(paid, LocalDateTime.now().minusDays(8));
    }

    protected OrderDeliveryGroup deliveredFrom(OrderDeliveryGroup paid, LocalDateTime deliveredAt) {
        try {
            OrderDeliveryGroup group = preparing(paid);
            registerShipment(group, "CJ", nextTrackingNumber()).andExpect(status().isOk());
            return track(reloadGroup(group), new TrackSnapshot(deliveredAt.withNano(0), deliveredAt.withNano(0),
                    false, false), LocalDateTime.now());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 반품 · 교환 클레임이 끝난 뒤의 구매확정 — 재개된 타이머를 넉넉히 지난 시각으로 돌린다. */
    protected OrderDeliveryGroup confirmLater(OrderDeliveryGroup group) {
        LocalDateTime now = LocalDateTime.now();
        assertThat(fulfillmentService.confirmPurchase(group.getId(), now.plusDays(10), now.plusDays(3))).isTrue();
        return reloadGroup(group);
    }

    /** 반품 신청(소비자 · 회수 송장 포함) — 클레임 id. */
    protected Long returnClaim(OrderDeliveryGroup group, OrderProduct item, int quantity, ClaimReason reason) {
        return claimService.request(new OrderClaimService.RequestCommand(consumer.getId(), group.getId(),
                ClaimType.RETURN, reason, reason.isDetailRequired() ? "상세 내용입니다" : null, List.of(),
                List.of(new OrderClaimService.Item(item.getId(), quantity)),
                new OrderClaimService.Invoice(DeliveryCarrier.CJ, nextTrackingNumber()), null, null),
                LocalDateTime.now()).claimIds().get(0);
    }

    /** 파트너센터 입고 확인 → 검수 통과 — 반품 환불(PG 자동)까지. */
    protected void passClaim(Long claimId) {
        try {
            sellerPost("/v1/seller/claims/receive", Map.of("claimIds", List.of(claimId))).andExpect(status().isOk());
            sellerPost("/v1/seller/claims/" + claimId + "/inspection/pass", Map.of()).andExpect(status().isOk());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 브랜드 직권 취소(준비 중) — PG 자동 환불까지. */
    protected OrderDeliveryGroup cancelledBySeller(OrderDeliveryGroup group) {
        try {
            directCancel(List.of(group.getId()), "SOLD_OUT", "품절로 취소합니다").andExpect(status().isOk());
            return reloadGroup(group);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected String nextTrackingNumber() {
        return "5%011d".formatted(++trackingSeq);
    }

    // ------------------------------------------------------------------ 공구 · 정산

    /** 공구 종료(스케줄러 몫) — SQL 로 옮긴다. */
    protected void endGroupBuy() {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
    }

    /**
     * 같은 브랜드의 다음 공구(진행 중) — {@code counterparty}와 맺은 새 계약 · 크림 · 세럼 재고 10. 이후 {@link #purchase} ·
     * {@link #endGroupBuy} · {@link #generate}가 이 공구를 쓰도록 {@code groupBuy} · 옵션 필드를 바꿔 끼운다.
     */
    protected GroupBuy openNextGroupBuy(Creator counterparty) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        GroupBuy next = seed(brand, counterparty, "글로우 크림 앵콜 공구", now.minusDays(3), now.plusDays(4));
        moveTo(next.getId(), GroupBuyStatus.IN_PROGRESS);
        groupBuy = reload(next.getId());
        for (Product product : List.of(cream, serum)) {
            jdbc.update("UPDATE product SET group_buy_status = 'IN_PROGRESS' WHERE product_id = ?", product.getProductId());
        }
        creamVariant = ContractOptions.variantsOf(productVariantRepository, cream).get(0);
        serumVariant = ContractOptions.variantsOf(productVariantRepository, serum).get(0);
        setStock(creamVariant, 10);
        setStock(serumVariant, 10);
        return groupBuy;
    }

    protected Settlement generate(LocalDateTime now) {
        Long id = generationService.generate(groupBuy.getId(), now).orElseThrow();
        return settlementRepository.findById(id).orElseThrow();
    }

    /** 자동 확정 — 확정 시각은 규칙값(마감 다음 날 00:00). */
    protected Settlement confirm(Settlement settlement) {
        confirmService.confirm(settlement.getId(), SettlementConfirmReason.AUTO, settlement.autoConfirmAt());
        return settlement(settlement.getId());
    }

    /** 지급 배치 1회분(예정일 = today). */
    protected Settlement pay(Settlement settlement, LocalDate today) {
        payoutService.distribute(settlement.getId(), today, today.atTime(10, 0));
        return settlement(settlement.getId());
    }

    /**
     * 목록 · 요약용 보조 정산 — 실제 주문 없이 같은 브랜드 · 인플루언서의 다른 종료 공구에 금액만 계산해 둔다(명세 행 없음).
     * 생성 경로는 {@link #generate}가 검증한다.
     */
    protected Settlement seedSettlement(String title, long confirmedSales, SettlementStatus status) {
        GroupBuy other = seed(brand, creator, title, LocalDateTime.now().minusDays(20).withNano(0),
                LocalDateTime.now().minusDays(12).withNano(0));
        moveTo(other.getId(), GroupBuyStatus.ENDED);
        Long id = inTransaction(() -> {
            GroupBuy g = groupBuyRepository.findDetailById(other.getId()).orElseThrow();
            LocalDateTime now = LocalDateTime.now().withNano(0);
            SettlementAmounts amounts = settlementCalculator.calculate(new SettlementInput(confirmedSales,
                    confirmedSales / 10, 0, 0, 0, 0, CreatorBusinessType.INDIVIDUAL,
                    new SettlementRates(new BigDecimal("0.03"), BigDecimal.ZERO, new BigDecimal("0.10"),
                            new BigDecimal("0.03"), new BigDecimal("0.003"))));
            Settlement s = settlementRepository.save(Settlement.builder()
                    .settlementNumber(settlementNumberGenerator.nextSettlementNumber(now))
                    .groupBuy(g).contract(g.getContract()).market(g.getMarket()).creator(g.getCreator())
                    .creatorBusinessType(CreatorBusinessType.INDIVIDUAL)
                    .periodStartAt(g.getStartAt()).periodEndAt(g.getEndAt()).ordersClosedAt(now.minusDays(1))
                    .createdAt(now.minusDays(1)).reviewDueAt(now.plusDays(3))
                    .breakdown(new Settlement.Breakdown(confirmedSales, 0, 0, 0, 0, 0, 0, 0))
                    .amounts(amounts).build());
            payoutRepository.saveAll(List.of(
                    SettlementPayout.waiting(s.getId(), SettlementPayee.BRAND, amounts.brandPayoutAmount()),
                    SettlementPayout.waiting(s.getId(), SettlementPayee.CREATOR, amounts.creatorPayoutAmount()),
                    SettlementPayout.waiting(s.getId(), SettlementPayee.PLATFORM, amounts.platformShareAmount())));
            return s.getId();
        });
        if (status != SettlementStatus.REVIEWING) {
            jdbc.update("UPDATE settlement SET status = ? WHERE settlement_id = ?", status.name(), id);
        }
        return settlement(id);
    }

    protected Settlement settlement(Long settlementId) {
        return settlementRepository.findById(settlementId).orElseThrow();
    }

    protected List<SettlementItem> itemsOf(Settlement settlement) {
        return settlementItemRepository.findAllBySettlementId(settlement.getId());
    }

    protected List<SettlementPayout> payoutsOf(Long settlementId) {
        return payoutRepository.findBySettlementId(settlementId);
    }

    protected SettlementPayout payout(Long settlementId, SettlementPayee payee) {
        return payoutRepository.findBySettlementIdAndPayee(settlementId, payee).orElseThrow();
    }

    protected List<SettlementEventType> settlementEvents(Long settlementId) {
        return settlementHistoryRepository.findLatestFirst(settlementId).stream()
                .map(h -> h.getEventType()).toList().reversed();
    }

    // ------------------------------------------------------------------ 회원 정보(지급 · 증빙)

    protected void makeCreatorBusiness() {
        jdbc.update("UPDATE creator SET business_type = ? WHERE creator_id = ?", CreatorBusinessType.BUSINESS.name(),
                creator.getId());
    }

    protected void registerCreatorAccount() {
        jdbc.update("UPDATE creator SET real_name = ?, bank_name = ?, account_number = ? WHERE creator_id = ?",
                "김지민", "국민은행", "123456789012", creator.getId());
    }

    protected void registerCreatorResidentNumber() {
        jdbc.update("UPDATE creator SET resident_registration_number_enc = ?, resident_registration_number_masked = ? "
                + "WHERE creator_id = ?", personalDataCipher.encrypt(RESIDENT_NUMBER), "900101-1******", creator.getId());
    }

    protected void registerSellerAccount() {
        jdbc.update("UPDATE seller SET bank_name = ?, account_holder = ?, account_number = ? WHERE seller_id = ?",
                "신한은행", "글로우랩", "110123456789", brand.seller().getId());
    }

    // ------------------------------------------------------------------ 어드민 요청

    /** 06a B5 운영자 사유 환불 편입 → 06c 집행 — 큐 행 id. */
    protected Long operatorRefund(OrderDeliveryGroup group, String reason, int amount) {
        try {
            adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/refund-tasks",
                    Map.of("reason", reason, "amount", amount, "detail", "구매확정 후 하자 확인"))
                    .andExpect(status().isOk());
            Long taskId = jdbc.queryForObject("SELECT MAX(refund_task_id) FROM order_refund_task "
                    + "WHERE delivery_group_id = ? AND source = 'OPERATOR_REASON'", Long.class, group.getId());
            adminPost(ADMIN_REFUNDS + "/" + taskId + "/execute", Map.of()).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT status FROM order_refund_task WHERE refund_task_id = ?",
                    String.class, taskId)).isEqualTo("DONE");
            return taskId;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected ResultActions creatorGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    protected ResultActions adminGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    protected ResultActions adminPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
