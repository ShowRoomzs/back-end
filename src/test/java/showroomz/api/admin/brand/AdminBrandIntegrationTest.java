package showroomz.api.admin.brand;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.member.brand.entity.BrandWithdrawalRequest;
import showroomz.domain.member.brand.repository.BrandSanctionRepository;
import showroomz.domain.member.brand.repository.BrandWithdrawalRequestRepository;
import showroomz.domain.member.brand.service.BrandWithdrawalPurger;
import showroomz.domain.member.brand.service.BrandWithdrawalService;
import showroomz.domain.member.brand.type.BrandWithdrawalReason;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.error.exception.ConditionUnmetException;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 회원 관리 · 브랜드 상세(52 설계서 12절 BD 시나리오). 정산 시드({@link SettlementTestSupport})가 진행 중 공구 1건 · 인플루언서 1명 ·
 * 운영자 「김운영」을 깐다 — 이 브랜드는 진행 중 공구가 있어 탈퇴 조건에 걸린다. 탈퇴 승인은 아무것도 없는 별도 브랜드로 본다.
 */
@DisplayName("어드민 회원 관리 · 브랜드 상세 — 조회 · 정지 · 지급 보류 · 차단 가드 · 탈퇴 심사 · 메모 · 이력")
class AdminBrandIntegrationTest extends SettlementTestSupport {

    private static final String BRANDS = "/v1/admin/brands";

    @Autowired private BrandSanctionRepository sanctionRepository;
    @Autowired private BrandWithdrawalRequestRepository withdrawalRepository;
    @Autowired private BrandWithdrawalService withdrawalService;
    @Autowired private showroomz.global.config.properties.MemberProperties memberProperties;

    private String base() {
        return BRANDS + "/" + brand.marketId();
    }

    // ------------------------------------------------------------------ BD-01 상세

    @Test
    @DisplayName("BD-01 활성 상세 — 회원번호 · 계좌 가운데 마스킹 · 업태 없음 · 최근 문의 없음 · 정지 가능 · 칩은 1건 이상만")
    void activeDetail() throws Exception {
        JsonNode body = json(adminGet(base()).andExpect(status().isOk())
                .andExpect(jsonPath("$.brandNo").value("BRD-" + brand.marketId()))
                .andExpect(jsonPath("$.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.purged").value(false))
                .andExpect(jsonPath("$.settlementInfo.maskedAccountNumber").value("110*****6789"))
                .andExpect(jsonPath("$.basicInfo.email").value(brand.email()))
                .andExpect(jsonPath("$.operations.activeGroupBuyCount").value(1))
                .andExpect(jsonPath("$.rail.currentStatus.actions.canSuspend").value(true))
                .andExpect(jsonPath("$.rail.currentStatus.actions.canRelease").value(false))
                .andExpect(jsonPath("$.rail.currentStatus.actions.canReviewWithdrawal").value(false))
                .andExpect(jsonPath("$.rail.withdrawalReview").doesNotExist()));
        assertThat(body.path("businessInfo").has("businessCondition")).isFalse();
        assertThat(body.has("recentInquiries")).isFalse();
        assertThat(body.toString()).doesNotContain("110123456789");
        for (JsonNode chip : body.path("rail").path("pendingChips")) {
            assertThat(chip.path("count").asLong()).isPositive();
        }

        adminGet(BRANDS + "?keyword=BRD-" + brand.marketId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].memberStatus").value("ACTIVE"));
        adminGet(BRANDS + "?keyword=BRD-abc").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        // 운영자 계정 · 존재하지 않는 마켓은 회원 관리 대상이 아니다.
        adminGet(BRANDS + "/999999").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MARKET_NOT_FOUND"));
    }

    @Test
    @DisplayName("BD-01b 역할 경계 — 브랜드 · 인플루언서 토큰 403 · 토큰 없음 401")
    void operatorsOnly() throws Exception {
        for (String token : List.of(brandToken, creatorToken)) {
            mockMvc.perform(get(base()).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
            mockMvc.perform(post(base() + "/suspend").header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("reason", "OTHER", "reasonDetail", "x"))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(base())).andExpect(status().isUnauthorized());
        assertThat(sanctionRepository.findAll()).isEmpty();
    }

    // ------------------------------------------------------------------ BD-02 ~ BD-08 정지 · 지급

    @Test
    @DisplayName("BD-02 정지(정산 정보 불일치) — 정지 행 · SUSPENDED · 브랜드 몫 SCHEDULED → BLOCKED(BRAND_SUSPENDED) · 인플루언서 몫 불변 · 운영팀 카드 · 이력")
    void suspendHoldsBrandPayout() throws Exception {
        registerSellerAccount();
        Settlement s = confirm(seedSettlement("가을 공구", 1_000_000, SettlementStatus.REVIEWING));
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        PayoutStatus creatorBefore = payout(s.getId(), SettlementPayee.CREATOR).getStatus();

        suspend("SETTLEMENT_INFO_MISMATCH", "예금주와 상호가 다릅니다.").andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus.memberStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.currentStatus.sanction.reasonLabel").value("정산 정보 불일치"))
                .andExpect(jsonPath("$.currentStatus.sanction.reasonDetail").value("예금주와 상호가 다릅니다."))
                .andExpect(jsonPath("$.currentStatus.sanction.payoutHeld").value(true))
                .andExpect(jsonPath("$.currentStatus.sanction.suspendedByName").value("김운영"))
                .andExpect(jsonPath("$.currentStatus.actions.canRelease").value(true))
                .andExpect(jsonPath("$.histories.items[0].type").value("SUSPENDED"));

        assertThat(marketStatus()).isEqualTo(MarketStatus.SUSPENDED);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getFailCode()).isEqualTo(PayoutBlockReason.BRAND_SUSPENDED.name());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(creatorBefore);
        assertThat(operatorCards()).contains(MessageCardType.ACCOUNT_SUSPENDED);
        adminGet(base()).andExpect(jsonPath("$.operations.payoutHeld").value(true));
    }

    @Test
    @DisplayName("BD-03 정지(배송 지연 반복) — 지급 유지 · 정산 행 불변")
    void suspendWithoutPayoutHold() throws Exception {
        registerSellerAccount();
        Settlement s = confirm(seedSettlement("가을 공구", 1_000_000, SettlementStatus.REVIEWING));
        suspend("REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus.sanction.payoutHeld").value(false));
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    @Test
    @DisplayName("BD-04 · BD-05 거절 — 기타 상세 누락 400 · 사유 없음 400 · 이중 정지 409 · 정지 아닌데 해제 409")
    void suspendRejections() throws Exception {
        suspend("OTHER", "  ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BRAND_SUSPEND_DETAIL_REQUIRED"));
        adminPost(base() + "/suspend", Map.of()).andExpect(status().isBadRequest());
        adminPost(base() + "/release", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_NOT_SUSPENDED"));
        assertThat(sanctionRepository.findAll()).isEmpty();

        suspend("PRODUCT_INFO_FALSIFIED", null).andExpect(status().isOk());
        suspend("PRODUCT_INFO_FALSIFIED", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_ALREADY_SUSPENDED"));
        assertThat(sanctionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("BD-06 정지 중 확정 — 브랜드 몫은 예정 대신 보류 · 플랫폼 몫은 예정")
    void confirmWhileSuspended() throws Exception {
        registerSellerAccount();
        Settlement s = seedSettlement("가을 공구", 1_000_000, SettlementStatus.REVIEWING);
        suspend("BUSINESS_INFO_FALSIFIED", null).andExpect(status().isOk());

        confirm(s);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getFailCode()).isEqualTo(PayoutBlockReason.BRAND_SUSPENDED.name());
        assertThat(payout(s.getId(), SettlementPayee.PLATFORM).getStatus())
                .isIn(PayoutStatus.SCHEDULED, PayoutStatus.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("BD-07 지급 배치 재판정 — 정지 행만 있고 보류가 안 걸린 틈새에서도 브랜드 몫 지시가 나가지 않는다")
    void payoutBatchRejudges() {
        registerSellerAccount();
        Settlement s = confirm(seedSettlement("가을 공구", 1_000_000, SettlementStatus.REVIEWING));
        // 훅을 거치지 않은 정지 — 확정 · 배치 경합의 틈새를 흉내 낸다.
        jdbc.update("INSERT INTO brand_sanction (market_id, reason, payout_held, suspended_at, suspended_by, "
                + "suspended_by_name) VALUES (?, 'SETTLEMENT_INFO_MISMATCH', true, ?, ?, '김운영')",
                brand.marketId(), LocalDateTime.now(), operator.getId());
        jdbc.update("UPDATE market SET status = 'SUSPENDED' WHERE market_id = ?", brand.marketId());

        LocalDate due = payout(s.getId(), SettlementPayee.BRAND).getDueDate();
        pay(s, due);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payoutGateway.calls().stream().flatMap(c -> c.lines().stream())
                .noneMatch(line -> line.payee() == SettlementPayee.BRAND)).isTrue();
    }

    @Test
    @DisplayName("BD-08 해제 — ACTIVE · BLOCKED → SCHEDULED(다음 영업일) · 해제 카드 · 이력")
    void releaseRestoresPayout() throws Exception {
        registerSellerAccount();
        Settlement s = confirm(seedSettlement("가을 공구", 1_000_000, SettlementStatus.REVIEWING));
        suspend("SETTLEMENT_INFO_MISMATCH", null).andExpect(status().isOk());

        adminPost(base() + "/release", Map.of("note", "통장 사본 확인")).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStatus.sanction").doesNotExist())
                .andExpect(jsonPath("$.histories.items[0].type").value("SUSPENSION_RELEASED"));

        assertThat(marketStatus()).isEqualTo(MarketStatus.ACTIVE);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getDueDate())
                .isEqualTo(businessCalendar.addBusinessDays(LocalDate.now(), 1));
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getFailCode()).isNull();
        assertThat(operatorCards()).contains(MessageCardType.ACCOUNT_SUSPENDED, MessageCardType.ACCOUNT_SUSPENSION_RELEASED);
        assertThat(sanctionRepository.findByMarketIdAndReleasedAtIsNull(brand.marketId())).isEmpty();
    }

    // ------------------------------------------------------------------ BD-09 차단 가드

    @Test
    @DisplayName("BD-09 정지 중 가드 — 계약 작성 409 · 준비완료 공구는 시작 시각이 지나도 열리지 않고 해제 뒤 열린다 · 배송 정책 수정은 통과")
    void guardsWhileSuspended() throws Exception {
        GroupBuy ready = seedIn(GroupBuyStatus.READY);
        jdbc.update("UPDATE group_buy SET start_at = ? WHERE group_buy_id = ?", LocalDateTime.now().minusMinutes(1),
                ready.getId());
        suspend("REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());

        sellerPost("/v1/seller/contracts", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_SUSPENDED"));
        assertThat(lifecycleService.open(ready.getId(), LocalDateTime.now())).isFalse();
        assertThat(reload(ready.getId()).getStatus()).isEqualTo(GroupBuyStatus.READY);
        mockMvc.perform(put("/v1/seller/basic-info/delivery-policy").header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("shippingLeadDays", 4, "defaultDeliveryFee", 3000))))
                .andExpect(status().is2xxSuccessful());

        adminPost(base() + "/release", Map.of()).andExpect(status().isOk());
        assertThat(lifecycleService.open(ready.getId(), LocalDateTime.now())).isTrue();
    }

    // ------------------------------------------------------------------ BD-10 ~ BD-15 탈퇴

    @Test
    @DisplayName("BD-10 탈퇴 신청 조건 — 진행 중 공구가 있는 브랜드는 신청 409(unmet ACTIVE_GROUP_BUYS)")
    void requestBlockedByConditions() {
        assertThatThrownBy(() -> withdrawalService.request(brand.marketId(), null, null, LocalDateTime.now()))
                .isInstanceOf(ConditionUnmetException.class)
                .satisfies(e -> assertThat(((ConditionUnmetException) e).getUnmet()).containsKey("ACTIVE_GROUP_BUYS"));
        assertThat(withdrawalRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("BD-11 · BD-12 · BD-15 탈퇴 승인 — 심사 중 정지 가능 · 승인 시 WITHDRAWN · 파기 · 로그인 차단 · 정지 종결 · 배송 정책 보존 · 이력 중립 · 탈퇴 상세")
    void approveWithdrawal() throws Exception {
        BrandFixture.Brand clean = fixture.createBrand("clean@showroomz.test", "클린브랜드");
        String cleanBase = BRANDS + "/" + clean.marketId();
        BrandWithdrawalRequest request = withdrawalService.request(clean.marketId(),
                BrandWithdrawalReason.OTHER_CHANNEL_FOCUS, "자사몰에 집중합니다.", LocalDateTime.now());

        adminGet(cleanBase).andExpect(status().isOk())
                .andExpect(jsonPath("$.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.withdrawalPending").value(true))
                .andExpect(jsonPath("$.rail.withdrawalReview.reasonLabel").value("다른 채널에 집중"))
                .andExpect(jsonPath("$.rail.histories.items.length()").value(2))
                .andExpect(jsonPath("$.rail.currentStatus.actions.canSuspend").value(true));
        adminPost(cleanBase + "/suspend", Map.of("reason", "CONSUMER_RESPONSE_NEGLECT")).andExpect(status().isOk());

        adminPost(cleanBase + "/withdrawal-requests/" + request.getId() + "/approve", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus.memberStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.currentStatus.withdrawal.approvedByName").value("김운영"))
                .andExpect(jsonPath("$.currentStatus.actions.canSuspend").value(false))
                .andExpect(jsonPath("$.withdrawalReview").doesNotExist());

        Seller seller = sellerRepository.findById(clean.seller().getId()).orElseThrow();
        assertThat(seller.getEmail()).isEqualTo(BrandWithdrawalPurger.placeholderEmail(seller.getId()));
        assertThat(seller.getAccountNumber()).isNull();
        assertThat(seller.getCompanyName()).isNull();
        assertThat(seller.getBusinessRegistrationNumber()).isNotEqualTo("123-45-67890").hasSize(64);
        assertThat(sanctionRepository.findByMarketIdAndReleasedAtIsNull(clean.marketId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM market WHERE market_id = ?", String.class, clean.marketId()))
                .isEqualTo("WITHDRAWN");
        // 탈퇴 계정 로그인 불가 — 원래 이메일로는 계정이 없다.
        assertThat(sellerRepository.findByEmail(clean.email())).isEmpty();

        adminGet(cleanBase).andExpect(status().isOk())
                .andExpect(jsonPath("$.purged").value(true))
                .andExpect(jsonPath("$.basicInfo.email").doesNotExist())
                .andExpect(jsonPath("$.businessInfo.companyName").doesNotExist())
                .andExpect(jsonPath("$.settlementInfo.maskedAccountNumber").doesNotExist())
                .andExpect(jsonPath("$.brandName").value("클린브랜드"))
                .andExpect(jsonPath("$.deliveryPolicy.returnAddress.address").value("서울특별시 강남구 테헤란로 123"))
                .andExpect(jsonPath("$.rail.pendingChips.length()").value(0))
                .andExpect(jsonPath("$.rail.histories.items[0].type").value("WITHDRAWAL_APPROVED"))
                .andExpect(jsonPath("$.rail.histories.items[0].tone").value("NEUTRAL"));
        adminPost(cleanBase + "/suspend", Map.of("reason", "OTHER", "reasonDetail", "x")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWN"));
        adminPost(cleanBase + "/memos", Map.of("content", "메모")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWN"));
    }

    @Test
    @DisplayName("BD-13 승인 직전 재검증 — 신청 뒤 진행 중 공구가 남아 있으면 409 · unmet 에 건수 · 신청은 대기 그대로")
    void approveRejudges() throws Exception {
        // 신청 화면의 관문을 지나간 뒤 상황이 바뀐 건 — 신청 행만 직접 만든다.
        BrandWithdrawalRequest request = withdrawalRepository.save(BrandWithdrawalRequest.open(brand.marketId(), null,
                null, "김담당", LocalDateTime.now()));

        adminPost(base() + "/withdrawal-requests/" + request.getId() + "/approve", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_CONDITION_UNMET"))
                .andExpect(jsonPath("$.unmet.ACTIVE_GROUP_BUYS").value(1));
        assertThat(withdrawalRepository.findById(request.getId()).orElseThrow().isPending()).isTrue();
        assertThat(marketStatus()).isEqualTo(MarketStatus.ACTIVE);
    }

    @Test
    @DisplayName("BD-14 반려 — 기타 안내 누락 400 · 반려 뒤 활성 유지 · 재처리 409 · 재신청은 새 행")
    void rejectWithdrawal() throws Exception {
        BrandFixture.Brand clean = fixture.createBrand("reject@showroomz.test", "반려브랜드");
        String cleanBase = BRANDS + "/" + clean.marketId();
        BrandWithdrawalRequest request = withdrawalService.request(clean.marketId(), null, null, LocalDateTime.now());
        String url = cleanBase + "/withdrawal-requests/" + request.getId() + "/reject";

        adminPost(url, Map.of("reason", "OTHER")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_REJECT_DETAIL_REQUIRED"));
        adminPost(url, Map.of("reason", "UNCLOSED_ORDERS", "note", "반품 1건 수취 확인 후 다시 신청해 주세요."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.withdrawalReview").doesNotExist());
        adminPost(url, Map.of("reason", "UNCLOSED_ORDERS")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_NOT_PENDING"));

        BrandWithdrawalRequest again = withdrawalService.request(clean.marketId(), null, null, LocalDateTime.now());
        assertThat(again.getId()).isNotEqualTo(request.getId());
    }

    @Test
    @DisplayName("WP-01 파트너센터 탈퇴 신청 API —조건 미충족 409(unmet) · 계정 탭 BLOCKED · 신청 → PENDING · 취소 → AVAILABLE · 재신청 → 어드민 반려 → lastRejection")
    void partnerWithdrawalRequest() throws Exception {
        // 진행 중 공구가 있는 시드 브랜드 — 신청은 409 · 계정 탭은 BLOCKED.
        mockMvc.perform(post("/v1/seller/basic-info/withdrawal-requests").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_CONDITION_UNMET"))
                .andExpect(jsonPath("$.unmet.ACTIVE_GROUP_BUYS").value(1));
        mockMvc.perform(get("/v1/seller/basic-info/account").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawal.state").value("BLOCKED"))
                .andExpect(jsonPath("$.withdrawal.totalCount").value(4))
                .andExpect(jsonPath("$.withdrawal.conditions[0].code").value("ACTIVE_GROUP_BUYS"))
                .andExpect(jsonPath("$.withdrawal.conditions[0].satisfied").value(false))
                .andExpect(jsonPath("$.withdrawal.reasonOptions.length()").value(5));

        BrandFixture.Brand clean = fixture.createBrand("partner-wd@showroomz.test", "신청브랜드");
        String token = sellerToken(clean.seller());
        String account = "/v1/seller/basic-info/account";
        mockMvc.perform(get(account).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.withdrawal.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.withdrawal.satisfiedCount").value(4));

        JsonNode created = json(mockMvc.perform(post("/v1/seller/basic-info/withdrawal-requests")
                        .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("reason", "BUSINESS_CLOSED", "message", "사업을 정리합니다."))))
                .andExpect(status().isCreated()));
        long requestId = created.path("requestId").asLong();
        mockMvc.perform(get(account).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.withdrawal.state").value("PENDING"))
                .andExpect(jsonPath("$.withdrawal.pendingRequest.requestId").value(requestId))
                .andExpect(jsonPath("$.withdrawal.pendingRequest.reasonLabel").value("사업 종료"))
                .andExpect(jsonPath("$.withdrawal.pendingRequest.requesterEmail").value("partner-wd@showroomz.test"));
        mockMvc.perform(post("/v1/seller/basic-info/withdrawal-requests").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_ALREADY_PENDING"));
        adminGet(BRANDS + "/" + clean.marketId()).andExpect(jsonPath("$.withdrawalPending").value(true));
        // 다른 브랜드의 신청 id 로는 취소할 수 없다.
        mockMvc.perform(post("/v1/seller/basic-info/withdrawal-requests/" + requestId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_NOT_FOUND"));

        mockMvc.perform(post("/v1/seller/basic-info/withdrawal-requests/" + requestId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(account).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.withdrawal.state").value("AVAILABLE"));
        mockMvc.perform(post("/v1/seller/basic-info/withdrawal-requests/" + requestId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_NOT_PENDING"));

        long again = json(mockMvc.perform(post("/v1/seller/basic-info/withdrawal-requests")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isCreated())).path("requestId").asLong();
        adminPost(BRANDS + "/" + clean.marketId() + "/withdrawal-requests/" + again + "/reject",
                Map.of("reason", "UNSETTLED", "note", "정산 지급 후 다시 신청해 주세요.")).andExpect(status().isOk());
        mockMvc.perform(get(account).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.withdrawal.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.withdrawal.lastRejection.requestId").value(again))
                .andExpect(jsonPath("$.withdrawal.lastRejection.reasonCode").value("UNSETTLED"))
                .andExpect(jsonPath("$.withdrawal.lastRejection.note").value("정산 지급 후 다시 신청해 주세요."));
        // 셀러 즉시 탈퇴는 여전히 막힌다 — 신청이 유일한 길이다.
        assertThat(withdrawalRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("RO-01 모달 사유 목록 —정지 6종(지급 정지 2종) · 해제 2종(탈퇴 종결 제외) · 반려 5종(시안 M3) · 인플루언서 상세와 같은 모양")
    void reasonOptions() throws Exception {
        adminGet(BRANDS + "/reason-options").andExpect(status().isOk())
                .andExpect(jsonPath("$.suspensionReasons.length()").value(6))
                .andExpect(jsonPath("$.suspensionReasons[0].code").value("SETTLEMENT_INFO_MISMATCH"))
                .andExpect(jsonPath("$.suspensionReasons[0].holdsPayout").value(true))
                .andExpect(jsonPath("$.suspensionReasons[5].detailRequired").value(true))
                .andExpect(jsonPath("$.releaseReasons.length()").value(2))
                .andExpect(jsonPath("$.withdrawalRejectReasons.length()").value(5));
    }

    @Test
    @DisplayName("[56 B4] 사유 목록의 정지 모달 미리보기 — 이의 신청 3영업일 · 소명 검토 미정 null · 미리보기 기한 = 지금 정지하면 저장되는 기한 · "
            + "설정 영업일을 바꾸면 따라간다")
    void reasonOptionsSuspensionTermPreview() throws Exception {
        JsonNode term = json(adminGet(BRANDS + "/reason-options").andExpect(status().isOk())).get("suspensionTerm");
        assertThat(term.get("appealBusinessDays").asInt()).isEqualTo(3);
        assertThat(term.path("appealReviewBusinessDays").isNull() || term.path("appealReviewBusinessDays").isMissingNode())
                .isTrue();
        String preview = term.get("appealDeadlineAt").asText();
        assertThat(preview).contains("T23:59:59");

        suspend("CONSUMER_RESPONSE_NEGLECT", null).andExpect(status().isOk());
        assertThat(json(adminGet(base())).at("/rail/currentStatus/sanction/term/appealDeadlineAt").asText())
                .isEqualTo(preview);

        memberProperties.getSanction().setAppealBusinessDays(5);
        try {
            JsonNode changed = json(adminGet(BRANDS + "/reason-options")).get("suspensionTerm");
            assertThat(changed.get("appealBusinessDays").asInt()).isEqualTo(5);
            // 같은 포맷(yyyy-MM-dd'T'HH:mm:ss'Z')이라 문자열 순서가 시각 순서다
            assertThat(changed.get("appealDeadlineAt").asText()).isGreaterThan(preview);
        } finally {
            memberProperties.getSanction().setAppealBusinessDays(3);
        }
    }

    // ------------------------------------------------------------------ BD-16 · BD-17 메모 · 이력

    @Test
    @DisplayName("BD-16 메모 — 300자 초과 400 · 타인 수정 403 · 24시간 경과 403 · 삭제 뒤 목록 제외 · editable 판정")
    void memos() throws Exception {
        String memos = base() + "/memos";
        adminPost(memos, Map.of("content", "가".repeat(301))).andExpect(status().isBadRequest());
        Long memoId = json(adminPost(memos, Map.of("content", "미정산액 318만 원 — 정기 정산 포함 예정"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.editable").value(true))).path("memoId").asLong();

        Seller other = fixture.createAdmin("other-ops@showroomz.test", "박운영");
        String otherToken = adminToken(other);
        mockMvc.perform(patch(memos + "/" + memoId).header(HttpHeaders.AUTHORIZATION, otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("content", "덮어쓰기"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BRAND_MEMO_NOT_AUTHOR"));
        mockMvc.perform(get(memos).header(HttpHeaders.AUTHORIZATION, otherToken))
                .andExpect(jsonPath("$.content[0].editable").value(false));

        mockMvc.perform(patch(memos + "/" + memoId).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("content", "수정본"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modifiedAt").exists());

        jdbc.update("UPDATE brand_admin_memo SET created_at = ? WHERE memo_id = ?",
                LocalDateTime.now().minusHours(25), memoId);
        mockMvc.perform(delete(memos + "/" + memoId).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BRAND_MEMO_EDIT_WINDOW_CLOSED"));

        Long second = json(adminPost(memos, Map.of("content", "두 번째 메모"))).path("memoId").asLong();
        mockMvc.perform(delete(memos + "/" + second).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
        adminGet(memos).andExpect(jsonPath("$.content.length()").value(1));
        adminGet(base()).andExpect(jsonPath("$.memos.total").value(1));
    }

    @Test
    @DisplayName("BD-17 이력 병합 — 가입 · 정지 · 메모가 한 목록 최신순 · 레일은 3건 · 전체 건수")
    void historyMerge() throws Exception {
        suspend("REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        adminPost(base() + "/memos", Map.of("content", "지연 3회 확인")).andExpect(status().isOk());
        adminPost(base() + "/release", Map.of()).andExpect(status().isOk());

        JsonNode page = json(adminGet(base() + "/histories").andExpect(status().isOk()));
        List<String> types = page.path("content").findValuesAsText("type");
        assertThat(types).containsSubsequence("SUSPENSION_RELEASED", "MEMO_CREATED", "SUSPENDED");
        assertThat(types).contains("SIGNED_UP");
        adminGet(base()).andExpect(jsonPath("$.rail.histories.items.length()").value(3))
                .andExpect(jsonPath("$.rail.histories.total").value(types.size()));
    }

    // ------------------------------------------------------------------ BD-18 · BD-19

    @Test
    @DisplayName("BD-18 즉시 탈퇴 차단 — 브랜드가 DELETE /withdraw 를 부르면 409 · 계정 그대로")
    void immediateWithdrawBlocked() throws Exception {
        mockMvc.perform(delete("/v1/seller/auth/withdraw").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_REQUIRES_REQUEST"));
        assertThat(sellerRepository.findById(brand.seller().getId())).isPresent();
        assertThat(marketRepository.findById(brand.marketId())).isPresent();
    }

    @Test
    @DisplayName("BD-19 발송 기한 변경 보조줄 — 값이 바뀔 때만 이력 1행 · 상세에 최신 변경")
    void shipLeadHistory() throws Exception {
        putDeliveryPolicy(3);
        putDeliveryPolicy(3);
        putDeliveryPolicy(5);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM market_ship_lead_history WHERE market_id = ?",
                Integer.class, brand.marketId());
        adminGet(base()).andExpect(jsonPath("$.deliveryPolicy.shipLeadBusinessDays").value(5))
                .andExpect(jsonPath("$.deliveryPolicy.shipLeadLastChange.previousDays").value(3))
                .andExpect(jsonPath("$.deliveryPolicy.shipLeadLastChange.newDays").value(5));
        // 시드의 발송 기한(2)에서 3으로 · 3에서 5로 — 같은 값 저장은 이력을 만들지 않는다.
        assertThat(count).isEqualTo(2);
    }

    // ------------------------------------------------------------------ 보조

    private ResultActions suspend(String reason, String detail) throws Exception {
        Map<String, Object> body = detail == null ? Map.of("reason", reason) : Map.of("reason", reason, "reasonDetail", detail);
        return adminPost(base() + "/suspend", body);
    }

    private void putDeliveryPolicy(int days) throws Exception {
        mockMvc.perform(put("/v1/seller/basic-info/delivery-policy").header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("shippingLeadDays", days, "defaultDeliveryFee", 3000))))
                .andExpect(status().is2xxSuccessful());
    }

    private MarketStatus marketStatus() {
        return MarketStatus.valueOf(jdbc.queryForObject("SELECT status FROM market WHERE market_id = ?", String.class,
                brand.marketId()));
    }

    private List<MessageCardType> operatorCards() {
        Long threadId = jdbc.queryForObject("SELECT t.thread_id FROM message_thread t JOIN connection c "
                + "ON c.connection_id = t.connection_id WHERE c.type = 'OPERATOR_MARKET' AND c.market_id = ?",
                Long.class, brand.marketId());
        return messagesOf(threadId).stream().map(Message::getCardType).filter(java.util.Objects::nonNull).toList();
    }

    @SuppressWarnings("unused")
    private String sellerTokenOf(Seller seller) {
        return bearerToken(seller.getEmail(), RoleType.SELLER, seller.getId());
    }
}
