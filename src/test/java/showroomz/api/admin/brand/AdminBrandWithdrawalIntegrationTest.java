package showroomz.api.admin.brand;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.member.brand.entity.BrandSanction;
import showroomz.domain.member.brand.entity.BrandWithdrawalRequest;
import showroomz.domain.member.brand.service.BrandWithdrawalPurger;
import showroomz.domain.member.brand.type.BrandWithdrawalCondition;
import showroomz.domain.member.brand.type.BrandWithdrawalReason;
import showroomz.domain.member.brand.type.BrandWithdrawalRejectReason;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.product.entity.Product;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.ClawbackSide;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 52 테스트 시나리오 7절 — 탈퇴 조건 판정식 · 파트너 신청 · 취소 · 어드민 승인(M2) · 반려(M3) · 탈퇴 브랜드 상세. 조건 재료는 실제 화면이
 * 만드는 행을 쓰되, 다른 브랜드의 행을 이 브랜드로 옮겨야 하는 경우(하위주문 · 정산)는 market_id 만 SQL 로 옮긴다 — 판정식이 market_id 로
 * 세기 때문이다.
 */
@DisplayName("어드민 브랜드 탈퇴 — 조건 판정식 · 신청 · 승인 · 반려 · 탈퇴 상세(52 시나리오 WE · WP · WA · WR · WD)")
class AdminBrandWithdrawalIntegrationTest extends AdminBrandTestSupport {

    private static final String PARTNER_WITHDRAWALS = "/v1/seller/basic-info/withdrawal-requests";

    // ------------------------------------------------------------------ 7-1 조건 판정식

    @Test
    @DisplayName("WE-01 공구 — 준비중 · 준비완료 · 진행중 · 중단 예정만 · 종료 · 정산 완료 · 중단은 세지 않는다")
    void groupBuyCondition() {
        BrandFixture.Brand owner = cleanBrand("공구조건");
        Creator partner = createCreator("공구조건쇼룸", "gbcond");
        connect(owner, partner);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        for (GroupBuyStatus status : List.of(GroupBuyStatus.PREPARING, GroupBuyStatus.READY, GroupBuyStatus.IN_PROGRESS,
                GroupBuyStatus.SUSPENSION_SCHEDULED, GroupBuyStatus.ENDED, GroupBuyStatus.SETTLED, GroupBuyStatus.SUSPENDED)) {
            GroupBuy g = seed(owner, partner, "조건 " + status, now.minusDays(3), now.plusDays(4));
            moveTo(g.getId(), status);
        }
        assertThat(eligibility.evaluate(owner.marketId()).get(BrandWithdrawalCondition.ACTIVE_GROUP_BUYS)).isEqualTo(4);
    }

    @Test
    @DisplayName("WE-02 계약 — 검토 대기 · 검토 반려 · 서명 진행중 · 체결 처리 대기만 · 작성중 · 체결 · 거절 · 만료 · 취소 · 삭제분 제외")
    void contractCondition() {
        BrandFixture.Brand owner = cleanBrand("계약조건");
        for (ContractStatus status : ContractStatus.values()) {
            contractIn(owner, creator, status, false);
        }
        contractIn(owner, creator, ContractStatus.REVIEW_PENDING, true);
        assertThat(eligibility.evaluate(owner.marketId()).get(BrandWithdrawalCondition.CONTRACTS_BEFORE_CONCLUSION))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("WE-03 주문 — 결제된 신규 · 준비중 · 배송중 · 배송완료 · 반송중(반송 미완료 / 반송 완료 · 환불 미집행)만 · 구매확정 · 취소 · 미결제 제외")
    void orderCondition() {
        setStock(creamVariant, 30);
        List<String> statuses = List.of("NEW", "PREPARING", "SHIPPING", "DELIVERED", "CONFIRMED", "CANCELLED", "RETURNING",
                "RETURNING");
        List<OrderDeliveryGroup> groups = new ArrayList<>();
        for (String status : statuses) {
            OrderDeliveryGroup g = paidGroup();
            jdbc.update("UPDATE order_delivery_group SET fulfillment_status = ? WHERE delivery_group_id = ?", status, g.getId());
            groups.add(g);
        }
        jdbc.update("UPDATE order_delivery_group SET return_completed_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusHours(1), groups.get(7).getId());
        OrderDeliveryGroup unpaid = paidGroup();
        jdbc.update("UPDATE orders SET paid_at = NULL WHERE order_id = "
                + "(SELECT order_id FROM order_delivery_group WHERE delivery_group_id = ?)", unpaid.getId());

        assertThat(eligibility.evaluate(brand.marketId()).get(BrandWithdrawalCondition.UNCLOSED_ORDERS)).isEqualTo(6);
    }

    @Test
    @DisplayName("WE-04 정산 — 지급 완료 전 정산 4상태 + 브랜드 쪽 회수 대기 차감 · 지급 완료 · 인플루언서 쪽 차감 · 처리된 차감 제외")
    void settlementCondition() {
        Settlement first = null;
        for (SettlementStatus status : SettlementStatus.values()) {
            Settlement s = seedSettlement("조건 " + status, 1_000_000, status);
            first = first == null ? s : first;
        }
        clawback(brand.marketId(), first.getId(), ClawbackSide.BRAND, "PENDING");
        clawback(brand.marketId(), first.getId(), ClawbackSide.CREATOR, "PENDING");
        clawback(brand.marketId(), first.getId(), ClawbackSide.BRAND, "APPLIED");

        // REVIEWING · ADJUSTING · PAYOUT_SCHEDULED · PAYOUT_FAILED 4 + 브랜드 쪽 대기 차감 1
        assertThat(eligibility.evaluate(brand.marketId()).get(BrandWithdrawalCondition.UNSETTLED)).isEqualTo(5);
    }

    @Test
    @DisplayName("WE-05 · WE-09 미충족 응답 — unmet 은 응답 최상위 · 0 인 키 없음 · 조건 선언 순서 · 파트너 체크리스트와 같은 건수")
    void unmetShape() throws Exception {
        seedSettlement("미정산1", 1_000_000, SettlementStatus.REVIEWING);
        seedSettlement("미정산2", 1_000_000, SettlementStatus.ADJUSTING);
        seedSettlement("미정산3", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);

        JsonNode body = json(postAs(brandToken, PARTNER_WITHDRAWALS, null).andExpect(status().isConflict()));
        assertThat(body.path("code").asText()).isEqualTo("BRAND_WITHDRAWAL_CONDITION_UNMET");
        List<String> keys = new ArrayList<>();
        body.path("unmet").fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactly("ACTIVE_GROUP_BUYS", "UNSETTLED");
        assertThat(body.at("/unmet/ACTIVE_GROUP_BUYS").asLong()).isEqualTo(1);
        assertThat(body.at("/unmet/UNSETTLED").asLong()).isEqualTo(3);

        JsonNode conditions = json(getAs(brandToken, "/v1/seller/basic-info/account")).at("/withdrawal/conditions");
        var evaluated = eligibility.evaluate(brand.marketId());
        assertThat(conditions).hasSize(4);
        for (JsonNode condition : conditions) {
            BrandWithdrawalCondition key = BrandWithdrawalCondition.valueOf(condition.path("code").asText());
            assertThat(condition.path("count").asLong()).as(key.name()).isEqualTo(evaluated.get(key));
            assertThat(condition.path("satisfied").asBoolean()).isEqualTo(evaluated.get(key) == 0);
        }
    }

    @Test
    @DisplayName("WE-06 남의 데이터는 세지 않는다 — 시드 브랜드에 재료가 있어도 클린 브랜드는 4조건 0")
    void otherBrandsDataIsNotCounted() {
        seedSettlement("남의 정산", 1_000_000, SettlementStatus.REVIEWING);
        paidGroup();
        contractIn(brand, creator, ContractStatus.SIGNING, false);
        BrandFixture.Brand owner = cleanBrand("깨끗한브랜드");
        assertThat(eligibility.evaluate(owner.marketId()).values()).containsOnly(0L);
    }

    @Test
    @DisplayName("WE-07 브랜드 몫은 다 나갔어도 정산이 지급 완료 전이면 조건 ④ — 상세 미정산액 0 과 409 가 같이 보인다(판정 보류 · 17 #6)")
    void brandPaidButSettlementOpen() throws Exception {
        BrandFixture.Brand owner = cleanBrand("몫완료");
        Settlement s = settlementOf(owner, SettlementStatus.PAYOUT_SCHEDULED);
        setPayout(s.getId(), SettlementPayee.BRAND, PayoutStatus.PAID, 900_000, null, LocalDate.now().minusDays(1));
        setPayout(s.getId(), SettlementPayee.CREATOR, PayoutStatus.BLOCKED, 50_000, "TAX_INVOICE_UNVERIFIED", null);

        brandDetail(owner.marketId()).andExpect(jsonPath("$.operations.unsettledAmount").value(0))
                .andExpect(jsonPath("$.operations.settledAmountTotal").value(900_000));
        postAs(sellerToken(owner.seller()), PARTNER_WITHDRAWALS, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.unmet.UNSETTLED").value(1));
    }

    @Test
    @DisplayName("WE-08 정산 생성 전 틈새 — 종료 공구 · 정산 없음이면 조건 4종이 모두 0 이라 신청 · 승인이 통과한다(현재 동작 · 판정 보류 17 #7)")
    void settlementGenerationGap() throws Exception {
        BrandFixture.Brand owner = cleanBrand("정산전틈새");
        Creator partner = createCreator("틈새쇼룸", "gap");
        connect(owner, partner);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        GroupBuy ended = seed(owner, partner, "끝난 공구", now.minusDays(10), now.minusDays(3));
        moveTo(ended.getId(), GroupBuyStatus.ENDED);

        assertThat(eligibility.evaluate(owner.marketId()).values()).containsOnly(0L);
        Long requestId = withdrawalService.request(owner.marketId(), null, null, LocalDateTime.now()).getId();
        approve(owner.marketId(), requestId).andExpect(status().isOk());
        assertThat(marketStatus(owner.marketId())).isEqualTo(MarketStatus.WITHDRAWN);
    }

    // ------------------------------------------------------------------ 7-2 파트너 신청 · 취소

    @Test
    @DisplayName("WP-02 신청 바디 — 바디 없음 · 사유만 · 말씀 1000자 통과 · 1001자 400 · 공백 말씀은 null")
    void partnerRequestBody() throws Exception {
        BrandFixture.Brand owner = cleanBrand("신청바디");
        String token = sellerToken(owner.seller());

        long none = json(postAs(token, PARTNER_WITHDRAWALS, null).andExpect(status().isCreated())).path("requestId").asLong();
        cancelAsPartner(token, none);
        long reasonOnly = json(postAs(token, PARTNER_WITHDRAWALS, Map.of("reason", "BUSINESS_CLOSED"))
                .andExpect(status().isCreated())).path("requestId").asLong();
        assertThat(withdrawalRepository.findById(reasonOnly).orElseThrow().getReason()).isEqualTo(BrandWithdrawalReason.BUSINESS_CLOSED);
        cancelAsPartner(token, reasonOnly);
        long longest = json(postAs(token, PARTNER_WITHDRAWALS, Map.of("message", "가".repeat(1000)))
                .andExpect(status().isCreated())).path("requestId").asLong();
        cancelAsPartner(token, longest);
        postAs(token, PARTNER_WITHDRAWALS, Map.of("message", "가".repeat(1001))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("남기실 말씀은 1000자 이내로 입력해 주세요."));
        long blank = json(postAs(token, PARTNER_WITHDRAWALS, Map.of("message", "   ")).andExpect(status().isCreated()))
                .path("requestId").asLong();
        BrandWithdrawalRequest saved = withdrawalRepository.findById(blank).orElseThrow();
        assertThat(saved.getMessage()).isNull();
        assertThat(saved.getReason()).isNull();
        assertThat(withdrawalRepository.findAll()).hasSize(4);
    }

    @Test
    @DisplayName("WP-03 · WP-04 검사 순서 · 정지 중 신청 — 대기 중이면 조건보다 중복이 먼저 · 지급 유지 정지는 신청 가능 · 지급 정지로 묶인 정산은 조건 ④")
    void partnerRequestOrderAndSuspension() throws Exception {
        BrandFixture.Brand owner = cleanBrand("순서브랜드");
        String token = sellerToken(owner.seller());
        postAs(token, PARTNER_WITHDRAWALS, null).andExpect(status().isCreated());
        contractIn(owner, creator, ContractStatus.SIGNING, false);
        postAs(token, PARTNER_WITHDRAWALS, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_ALREADY_PENDING"));

        BrandFixture.Brand suspended = cleanBrand("정지중신청");
        suspend(suspended.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        postAs(sellerToken(suspended.seller()), PARTNER_WITHDRAWALS, null).andExpect(status().isCreated());

        BrandFixture.Brand held = cleanBrand("지급정지신청");
        Settlement s = settlementOf(held, SettlementStatus.PAYOUT_SCHEDULED);
        setPayout(s.getId(), SettlementPayee.BRAND, PayoutStatus.SCHEDULED, 900_000, null, LocalDate.now().plusDays(3));
        suspend(held.marketId(), "SETTLEMENT_INFO_MISMATCH", null).andExpect(status().isOk());
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        postAs(sellerToken(held.seller()), PARTNER_WITHDRAWALS, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.unmet.UNSETTLED").value(1));
    }

    @Test
    @DisplayName("WP-05 · WP-06 취소 결과 · 신청자 스냅샷 — 취소 이력(브랜드 · 상세 없음) · 심사 카드 사라짐 · 담당자명이 바뀌어도 신청자는 신청 시점 이름")
    void partnerCancelAndSnapshot() throws Exception {
        BrandFixture.Brand owner = cleanBrand("취소결과");
        String token = sellerToken(owner.seller());
        long requestId = json(postAs(token, PARTNER_WITHDRAWALS, null).andExpect(status().isCreated())).path("requestId").asLong();
        jdbc.update("UPDATE seller SET name = '이담당' WHERE seller_id = ?", owner.seller().getId());
        brandDetail(owner.marketId()).andExpect(jsonPath("$.rail.withdrawalReview.requesterName").value("김담당"));

        cancelAsPartner(token, requestId);
        BrandWithdrawalRequest canceled = withdrawalRepository.findById(requestId).orElseThrow();
        assertThat(canceled.getStatus()).isEqualTo(MemberWithdrawalStatus.CANCELED);
        assertThat(canceled.getCanceledAt()).isNotNull();
        brandDetail(owner.marketId()).andExpect(jsonPath("$.withdrawalPending").value(false))
                .andExpect(jsonPath("$.rail.withdrawalReview").doesNotExist())
                .andExpect(jsonPath("$.rail.histories.items[0].type").value("WITHDRAWAL_CANCELED"))
                .andExpect(jsonPath("$.rail.histories.items[0].actorType").value("BRAND"))
                .andExpect(jsonPath("$.rail.histories.items[0].tone").value("NEUTRAL"))
                .andExpect(jsonPath("$.rail.histories.items[0].detail").doesNotExist());
    }

    // ------------------------------------------------------------------ 7-3 승인

    @Test
    @DisplayName("BD-12 승인 DB — 신청 승인 · 정지 시스템 종결(해제 카드 없음) · 셀러 파기 목록 · 보존 목록 · 토큰 삭제 · 상품 숨김 · 작성중 계약 삭제 · PAIR 종료 · 운영팀 채널 유지")
    void approvalEffects() throws Exception {
        BrandFixture.Brand owner = cleanBrand("승인효과");
        Creator partner = createCreator("승인쇼룸", "approve");
        Connection pair = connect(owner, partner);
        MessageThread pairThread = messageThreadRepository.save(MessageThread.openFor(pair));
        MessageThread channel = operatorChannel(owner);
        List<Product> products = List.of(createProduct(owner, "상품1", 10_000), createProduct(owner, "상품2", 10_000),
                createProduct(owner, "상품3", 10_000));
        Contract draft = contractIn(owner, partner, ContractStatus.DRAFT, false);
        Contract concluded = contractIn(owner, partner, ContractStatus.CONCLUDED, false);
        // 가입 완료(is_new_member=false)여야 로그인이 리프레시 토큰을 저장한다.
        jdbc.update("UPDATE seller SET pg_partner_id = 'pg-partner-1', last_login_at = ?, is_new_member = false "
                + "WHERE seller_id = ?", LocalDateTime.now().minusDays(1), owner.seller().getId());
        postAs(null, "/v1/seller/auth/login", Map.of("email", owner.email(), "password", RAW_PASSWORD))
                .andExpect(status().isOk());
        assertThat(sellerRefreshTokenRepository.findByAdminEmail(owner.email())).isNotNull();
        suspend(owner.marketId(), "CONSUMER_RESPONSE_NEGLECT", null).andExpect(status().isOk());
        Long requestId = withdrawalService.request(owner.marketId(), BrandWithdrawalReason.OTHER_CHANNEL_FOCUS,
                "자사몰 운영에 집중하기 위해 입점을 종료합니다.", LocalDateTime.now()).getId();
        LocalDateTime createdAt = sellerRepository.findById(owner.seller().getId()).orElseThrow().getCreatedAt();

        approve(owner.marketId(), requestId).andExpect(status().isOk());

        BrandWithdrawalRequest approved = withdrawalRepository.findById(requestId).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(MemberWithdrawalStatus.APPROVED);
        assertThat(approved.getProcessedBy()).isEqualTo(operator.getId());
        assertThat(approved.getProcessedByName()).isEqualTo("김운영");
        BrandSanction closed = sanctionRepository.findAll().stream()
                .filter(s -> s.getMarketId().equals(owner.marketId())).findFirst().orElseThrow();
        assertThat(closed.getReleasedAt()).isNotNull();
        assertThat(closed.getReleaseReason().name()).isEqualTo("WITHDRAWN");
        assertThat(closed.getReleasedBy()).isNull();
        assertThat(operatorCards(owner.marketId())).containsExactly(MessageCardType.ACCOUNT_SUSPENDED);
        JsonNode histories = json(adminGet(base(owner.marketId()) + "/histories")).path("content");
        assertThat(histories.get(0).path("type").asText()).isEqualTo("WITHDRAWAL_APPROVED");
        JsonNode systemClose = histories.get(1);
        assertThat(systemClose.path("type").asText()).isEqualTo("SUSPENSION_RELEASED");
        assertThat(systemClose.path("actorType").asText()).isEqualTo("SYSTEM");
        assertThat(systemClose.path("actorName").asText()).isEqualTo("시스템");
        assertThat(systemClose.path("detail").asText()).isEqualTo("탈퇴 승인으로 종결");

        assertThat(marketStatus(owner.marketId())).isEqualTo(MarketStatus.WITHDRAWN);
        Map<String, Object> market = jdbc.queryForMap("SELECT * FROM market WHERE market_id = ?", owner.marketId());
        assertThat(market.get("brand_site_url")).isNull();
        assertThat(market.get("market_name")).isEqualTo("승인효과");
        assertThat(market.get("shipping_address")).isEqualTo("서울특별시 강남구 테헤란로 123");
        assertThat(market.get("shipping_contact")).isEqualTo("010-1111-2222");

        Seller purged = sellerRepository.findById(owner.seller().getId()).orElseThrow();
        assertThat(purged.getName()).isEqualTo("파기됨");
        assertThat(purged.getEmail()).isEqualTo(BrandWithdrawalPurger.placeholderEmail(purged.getId()));
        assertThat(passwordEncoder.matches(RAW_PASSWORD, purged.getPassword())).isFalse();
        assertThat(purged.getBusinessRegistrationNumber()).hasSize(64).isNotEqualTo("123-45-67890");
        assertThat(java.util.Arrays.asList(purged.getPhoneNumber(), purged.getBusinessType(), purged.getRepresentativeName(),
                purged.getRepresentativeContact(), purged.getCompanyName(), purged.getBusinessCondition(),
                purged.getBusinessAddress(), purged.getDetailAddress(), purged.getTaxEmail(),
                purged.getBusinessLicenseImageUrl(), purged.getMailOrderRegImageUrl(), purged.getMailOrderRegNumber(),
                purged.getBankName(), purged.getAccountHolder(), purged.getAccountNumber(), purged.getBankbookImageUrl()))
                .containsOnlyNulls();
        assertThat(purged.getCreatedAt()).isEqualTo(createdAt);
        assertThat(purged.getLastLoginAt()).isNotNull();
        assertThat(purged.getPgPartnerId()).isEqualTo("pg-partner-1");
        assertThat(sellerRefreshTokenRepository.findByAdminEmail(owner.email())).isNull();

        for (Product product : products) {
            assertThat(jdbc.queryForObject("SELECT display_status FROM product WHERE product_id = ?", String.class,
                    product.getProductId())).isEqualTo("HIDDEN");
        }
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM contract WHERE contract_id = ?", LocalDateTime.class,
                draft.getId())).isNotNull();
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM contract WHERE contract_id = ?", LocalDateTime.class,
                concluded.getId())).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM connection WHERE connection_id = ?", String.class, pair.getId()))
                .isEqualTo("DISCONNECTED");
        assertThat(jdbc.queryForObject("SELECT status FROM message_thread WHERE thread_id = ?", String.class,
                pairThread.getId())).isEqualTo("DORMANT");
        assertThat(jdbc.queryForObject("SELECT status FROM message_thread WHERE thread_id = ?", String.class,
                channel.getId())).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("WA-01 · WA-02 로그인 차단 · 재가입 — 옛 이메일 로그인 · 리프레시 · 옛 액세스 토큰 모두 실패 · 같은 사업자등록번호는 다시 쓸 수 있다")
    void loginBlockedAndReRegistrable() throws Exception {
        BrandFixture.Brand owner = cleanBrand("로그인차단");
        jdbc.update("UPDATE seller SET business_reg_number = '214-37-55120', is_new_member = false WHERE seller_id = ?",
                owner.seller().getId());
        JsonNode tokens = json(postAs(null, "/v1/seller/auth/login",
                Map.of("email", owner.email(), "password", RAW_PASSWORD)).andExpect(status().isOk()));
        String access = "Bearer " + tokens.path("accessToken").asText();
        String refresh = tokens.path("refreshToken").asText();
        assertThat(refresh).isNotBlank();
        getAs(access, "/v1/seller/basic-info/account").andExpect(status().isOk());
        getAs(null, "/v1/seller/auth/check-business-registration-number?businessRegistrationNumber=214-37-55120")
                .andExpect(jsonPath("$.code").value("DUPLICATE"));

        Long requestId = withdrawalService.request(owner.marketId(), null, null, LocalDateTime.now()).getId();
        approve(owner.marketId(), requestId).andExpect(status().isOk());

        int login = postAs(null, "/v1/seller/auth/login", Map.of("email", owner.email(), "password", RAW_PASSWORD))
                .andReturn().getResponse().getStatus();
        assertThat(login).isNotEqualTo(200);
        int reissue = postAs(null, "/v1/seller/auth/refresh", Map.of("refreshToken", refresh))
                .andReturn().getResponse().getStatus();
        assertThat(reissue).isNotEqualTo(200);
        int oldAccess = getAs(access, "/v1/seller/basic-info/account").andReturn().getResponse().getStatus();
        assertThat(oldAccess).isNotEqualTo(200);
        getAs(null, "/v1/seller/auth/check-business-registration-number?businessRegistrationNumber=214-37-55120")
                .andExpect(jsonPath("$.code").value("AVAILABLE"));
    }

    @Test
    @DisplayName("WA-03 탈퇴 뒤 운영팀 채널은 열람 전용 — 운영자 글 409 THREAD_READ_ONLY · 조회는 된다")
    void operatorChannelReadOnly() throws Exception {
        BrandFixture.Brand owner = cleanBrand("열람전용");
        MessageThread channel = operatorChannel(owner);
        brandSays(channel, owner.seller(), "탈퇴 전 마지막 문의");
        approve(owner.marketId(), withdrawalService.request(owner.marketId(), null, null, LocalDateTime.now()).getId())
                .andExpect(status().isOk());

        adminPost("/v1/admin/threads/" + channel.getId() + "/messages",
                Map.of("clientMessageId", "after-withdrawal-1", "content", "확인했습니다."))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("THREAD_READ_ONLY"));
        adminGet("/v1/admin/threads/" + channel.getId() + "/messages").andExpect(status().isOk());
    }

    @Test
    @DisplayName("WA-04 승인 직전 재검증 4축 — 신청 뒤 생긴 공구 · 계약 · 주문 · 정산이 각각 409 · 그 사이 부수 효과 없음 · 모두 정리되면 승인")
    void approvalRejudgesEachAxis() throws Exception {
        BrandFixture.Brand owner = cleanBrand("재검증");
        Creator partner = createCreator("재검증쇼룸", "rejudge");
        connect(owner, partner);
        Long requestId = pendingRequestRow(owner.marketId()).getId();
        LocalDateTime now = LocalDateTime.now().withNano(0);

        GroupBuy groupBuy = seed(owner, partner, "신청 뒤 공구", now.plusDays(5), now.plusDays(12));
        expectUnmet(owner, requestId, "ACTIVE_GROUP_BUYS");
        moveTo(groupBuy.getId(), GroupBuyStatus.SUSPENDED);

        Contract contract = contractIn(owner, partner, ContractStatus.REVIEW_PENDING, false);
        expectUnmet(owner, requestId, "CONTRACTS_BEFORE_CONCLUSION");
        jdbc.update("UPDATE contract SET status = 'CANCELED' WHERE contract_id = ?", contract.getId());

        OrderDeliveryGroup order = paidGroup();
        jdbc.update("UPDATE order_delivery_group SET market_id = ? WHERE delivery_group_id = ?", owner.marketId(), order.getId());
        expectUnmet(owner, requestId, "UNCLOSED_ORDERS");
        jdbc.update("UPDATE order_delivery_group SET fulfillment_status = 'CONFIRMED' WHERE delivery_group_id = ?",
                order.getId());

        Settlement s = settlementOf(owner, SettlementStatus.REVIEWING);
        expectUnmet(owner, requestId, "UNSETTLED");
        jdbc.update("UPDATE settlement SET status = 'PAID' WHERE settlement_id = ?", s.getId());

        approve(owner.marketId(), requestId).andExpect(status().isOk());
    }

    @Test
    @DisplayName("WA-05 · WR-03 신청 경계 — 남의 신청 · 없는 id 404 · 취소 · 반려된 신청 409 · 탈퇴한 마켓은 신청 상태보다 탈퇴가 먼저 · 반려는 사유 검증이 먼저")
    void requestBoundaries() throws Exception {
        BrandFixture.Brand owner = cleanBrand("경계신청");
        BrandFixture.Brand other = cleanBrand("경계남");
        Long othersRequest = pendingRequestRow(other.marketId()).getId();
        Long canceled = withdrawalService.request(owner.marketId(), null, null, LocalDateTime.now()).getId();
        withdrawalService.cancel(owner.marketId(), canceled, LocalDateTime.now());
        Long rejected = withdrawalService.request(owner.marketId(), null, null, LocalDateTime.now()).getId();
        reject(owner.marketId(), rejected, Map.of("reason", "UNSETTLED")).andExpect(status().isOk());

        for (Long id : List.of(othersRequest, 999_999L)) {
            approve(owner.marketId(), id).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_NOT_FOUND"));
            reject(owner.marketId(), id, Map.of("reason", "UNSETTLED")).andExpect(status().isNotFound());
        }
        for (Long id : List.of(canceled, rejected)) {
            approve(owner.marketId(), id).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_NOT_PENDING"));
            reject(owner.marketId(), id, Map.of("reason", "UNSETTLED")).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_NOT_PENDING"));
        }
        reject(owner.marketId(), 999_999L, Map.of("reason", "OTHER", "note", " ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_REJECT_DETAIL_REQUIRED"));

        Long approved = withdrawalService.request(owner.marketId(), null, null, LocalDateTime.now()).getId();
        approve(owner.marketId(), approved).andExpect(status().isOk());
        approve(owner.marketId(), approved).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWN"));
        assertThat(withdrawalRepository.findById(othersRequest).orElseThrow().isPending()).isTrue();
    }

    @Test
    @DisplayName("WA-06 지급 정지 중 승인 — 묶인 정산이 있으면 409(UNSETTLED) · 지급 완료 뒤 승인 · 정지는 시스템 종결")
    void approvalWhilePayoutHeld() throws Exception {
        BrandFixture.Brand owner = cleanBrand("지급정지승인");
        Settlement s = settlementOf(owner, SettlementStatus.PAYOUT_SCHEDULED);
        setPayout(s.getId(), SettlementPayee.BRAND, PayoutStatus.SCHEDULED, 900_000, null, LocalDate.now().plusDays(3));
        suspend(owner.marketId(), "SETTLEMENT_INFO_MISMATCH", null).andExpect(status().isOk());
        Long requestId = pendingRequestRow(owner.marketId()).getId();

        expectUnmet(owner, requestId, "UNSETTLED");
        jdbc.update("UPDATE settlement SET status = 'PAID' WHERE settlement_id = ?", s.getId());
        approve(owner.marketId(), requestId).andExpect(status().isOk());

        BrandSanction closed = sanctionRepository.findAll().stream()
                .filter(x -> x.getMarketId().equals(owner.marketId())).findFirst().orElseThrow();
        assertThat(closed.getReleaseReason().name()).isEqualTo("WITHDRAWN");
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
    }

    @Test
    @DisplayName("WA-07 되돌릴 수 없다 — 승인 뒤 다시 승인 · 정지 · 해제 · 메모는 409 BRAND_WITHDRAWN · 반려는 대기 아님 409")
    void approvalIsFinal() throws Exception {
        BrandFixture.Brand owner = cleanBrand("최종승인");
        Long requestId = withdrawalService.request(owner.marketId(), null, null, LocalDateTime.now()).getId();
        approve(owner.marketId(), requestId).andExpect(status().isOk());

        approve(owner.marketId(), requestId).andExpect(jsonPath("$.code").value("BRAND_WITHDRAWN"));
        reject(owner.marketId(), requestId, Map.of("reason", "UNSETTLED"))
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_NOT_PENDING"));
        suspend(owner.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(jsonPath("$.code").value("BRAND_WITHDRAWN"));
        release(owner.marketId(), Map.of()).andExpect(jsonPath("$.code").value("BRAND_WITHDRAWN"));
        adminPost(base(owner.marketId()) + "/memos", Map.of("content", "메모"))
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWN"));
        assertThat(marketStatus(owner.marketId())).isEqualTo(MarketStatus.WITHDRAWN);
    }

    // ------------------------------------------------------------------ 7-4 반려

    @Test
    @DisplayName("WR-01 반려 입력 — 사유 6종 통과 · 기타 안내 공백 3종 400 · 사유 없음 400 · 안내 501자 400 · 비-기타 안내 없음이면 이력은 라벨만")
    void rejectInputs() throws Exception {
        BrandFixture.Brand owner = cleanBrand("반려입력");
        Long first = withdrawalService.request(owner.marketId(), null, null, LocalDateTime.now()).getId();
        for (Object note : new Object[]{null, "", "   "}) {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("reason", "OTHER");
            body.put("note", note);
            reject(owner.marketId(), first, body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_REJECT_DETAIL_REQUIRED"));
        }
        reject(owner.marketId(), first, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        reject(owner.marketId(), first, Map.of("reason", "UNSETTLED", "note", "가".repeat(501)))
                .andExpect(status().isBadRequest());
        assertThat(withdrawalRepository.findById(first).orElseThrow().isPending()).isTrue();

        reject(owner.marketId(), first, Map.of("reason", "UNSHIPPED_ORDERS")).andExpect(status().isOk())
                .andExpect(jsonPath("$.histories.items[0].detail").value("미배송 주문 잔존"));
        for (BrandWithdrawalRejectReason reason : BrandWithdrawalRejectReason.values()) {
            if (reason == BrandWithdrawalRejectReason.UNSHIPPED_ORDERS) {
                continue;
            }
            Long id = withdrawalService.request(owner.marketId(), null, null, LocalDateTime.now()).getId();
            reject(owner.marketId(), id, Map.of("reason", reason.name(), "note", "안내")).andExpect(status().isOk());
            assertThat(withdrawalRepository.findById(id).orElseThrow().getRejectReason()).isEqualTo(reason);
        }
    }

    @Test
    @DisplayName("WR-02 · WR-04 반려 결과 — 행 · 이력(정보 톤) · 회원 상태 불변(정지면 정지 그대로) · 파트너 계정 탭 lastRejection")
    void rejectResult() throws Exception {
        BrandFixture.Brand owner = cleanBrand("반려결과");
        suspend(owner.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        Long requestId = withdrawalService.request(owner.marketId(), null, null, LocalDateTime.now()).getId();

        reject(owner.marketId(), requestId, Map.of("reason", "CLAIM_UNRESOLVED", "note", "반품 1건 수취 확인 후 다시 신청해 주세요."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus.memberStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.currentStatus.actions.canSuspend").value(false))
                .andExpect(jsonPath("$.currentStatus.actions.canRelease").value(true))
                .andExpect(jsonPath("$.currentStatus.actions.canReviewWithdrawal").value(false))
                .andExpect(jsonPath("$.histories.items[0].type").value("WITHDRAWAL_REJECTED"))
                .andExpect(jsonPath("$.histories.items[0].tone").value("INFO"))
                .andExpect(jsonPath("$.histories.items[0].detail").value("반품·교환 처리 미완료 — 반품 1건 수취 확인 후 다시 신청해 주세요."));

        BrandWithdrawalRequest rejected = withdrawalRepository.findById(requestId).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(MemberWithdrawalStatus.REJECTED);
        assertThat(rejected.getRejectReason()).isEqualTo(BrandWithdrawalRejectReason.CLAIM_UNRESOLVED);
        assertThat(rejected.getRejectNote()).isEqualTo("반품 1건 수취 확인 후 다시 신청해 주세요.");
        assertThat(rejected.getProcessedByName()).isEqualTo("김운영");
        assertThat(sanctionRepository.findByMarketIdAndReleasedAtIsNull(owner.marketId())).isPresent();
        getAs(sellerToken(owner.seller()), "/v1/seller/basic-info/account")
                .andExpect(jsonPath("$.withdrawal.lastRejection.requestId").value(requestId))
                .andExpect(jsonPath("$.withdrawal.lastRejection.reasonCode").value("CLAIM_UNRESOLVED"))
                .andExpect(jsonPath("$.withdrawal.lastRejection.reasonLabel").value("반품·교환 처리 미완료"))
                .andExpect(jsonPath("$.withdrawal.lastRejection.rejectedAt").exists());
    }

    // ------------------------------------------------------------------ 7-5 탈퇴 브랜드 상세

    @Test
    @DisplayName("WD-01 · WD-02 탈퇴 뒤 — 연결 종료 행은 남는다(시안 빈 목록과 다름 · 17 #8) · 조회 API 200 · 메모 쓰기 3종 409")
    void afterWithdrawal() throws Exception {
        BrandFixture.Brand owner = cleanBrand("탈퇴뒤");
        Creator partner = createCreator("탈퇴뒤쇼룸", "afterwd");
        connect(owner, partner);
        Long memoId = json(adminPost(base(owner.marketId()) + "/memos", Map.of("content", "탈퇴 전 메모"))
                .andExpect(status().isOk())).path("memoId").asLong();
        approve(owner.marketId(), withdrawalService.request(owner.marketId(), null, null, LocalDateTime.now()).getId())
                .andExpect(status().isOk());

        brandDetail(owner.marketId()).andExpect(jsonPath("$.connectedCreators.total").value(0))
                .andExpect(jsonPath("$.connectedCreators.items.length()").value(1))
                .andExpect(jsonPath("$.connectedCreators.items[0].status").value("DISCONNECTED"))
                .andExpect(jsonPath("$.memos.items[0].editable").value(false));
        for (String sub : List.of("/histories", "/memos", "/connected-creators")) {
            adminGet(base(owner.marketId()) + sub).andExpect(status().isOk());
        }
        String memo = base(owner.marketId()) + "/memos/" + memoId;
        adminPost(base(owner.marketId()) + "/memos", Map.of("content", "새 메모")).andExpect(status().isConflict());
        patchAs(adminToken, memo, Map.of("content", "수정")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWN"));
        deleteAs(adminToken, memo).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BRAND_WITHDRAWN"));
    }

    // ------------------------------------------------------------------ 보조

    private void cancelAsPartner(String token, long requestId) throws Exception {
        postAs(token, PARTNER_WITHDRAWALS + "/" + requestId + "/cancel", null).andExpect(status().isNoContent());
    }

    /** 승인 409 — 그 조건 하나만 1건이고 신청 · 회원 · 셀러는 그대로다. */
    private void expectUnmet(BrandFixture.Brand owner, Long requestId, String condition) throws Exception {
        JsonNode body = json(approve(owner.marketId(), requestId).andExpect(status().isConflict()));
        assertThat(body.path("code").asText()).isEqualTo("BRAND_WITHDRAWAL_CONDITION_UNMET");
        List<String> keys = new ArrayList<>();
        body.path("unmet").fieldNames().forEachRemaining(keys::add);
        assertThat(keys).as(condition).containsExactly(condition);
        assertThat(body.path("unmet").path(condition).asLong()).isEqualTo(1);
        assertThat(withdrawalRepository.findById(requestId).orElseThrow().isPending()).isTrue();
        assertThat(marketStatus(owner.marketId())).isNotEqualTo(MarketStatus.WITHDRAWN);
        assertThat(sellerRepository.findById(owner.seller().getId()).orElseThrow().getEmail()).isEqualTo(owner.email());
    }
}
