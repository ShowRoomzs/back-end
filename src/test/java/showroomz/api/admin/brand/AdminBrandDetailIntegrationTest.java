package showroomz.api.admin.brand;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.member.brand.entity.BrandAdminMemo;
import showroomz.domain.member.brand.type.BrandWithdrawalCondition;
import showroomz.domain.member.brand.type.BrandWithdrawalReason;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.support.BrandFixture;
import showroomz.support.ContractOptions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 52 테스트 시나리오 3절 — 상세 조회(좌측 7카드 · 레일)와 진행 중 칩 8종. 숫자는 다른 화면이 이미 세는 식이라 재료를 그 화면의 경로(주문 ·
 * 클레임 · 문의 · 정산)로 만들고, 다른 화면이 옮기는 단계만 SQL 로 옮긴다.
 */
@DisplayName("어드민 브랜드 상세 — 정보 카드 · 운영 현황 · 레일 · 진행 중 칩(52 시나리오 DT · CH)")
class AdminBrandDetailIntegrationTest extends AdminBrandTestSupport {

    // ------------------------------------------------------------------ 3-1 헤더 · 기본 · 사업자 · 정산

    @Test
    @DisplayName("DT-02 입점 승인일 — 마지막 승인 이력 · 없으면 셀러 처리일 · 둘 다 없으면 null(레일 현재 상태도 같은 값)")
    void approvedAt() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        BrandFixture.Brand reviewed = cleanBrand("재심사브랜드");
        onboardingApproved(reviewed, operator.getId(), now.minusDays(40), now.minusDays(20));
        JsonNode body = brandDetailJson(reviewed.marketId());
        assertThat(at(body.at("/basicInfo/approvedAt"))).isEqualTo(now.minusDays(20));
        assertThat(at(body.at("/rail/currentStatus/approvedAt"))).isEqualTo(now.minusDays(20));

        BrandFixture.Brand processed = cleanBrand("처리일브랜드");
        jdbc.update("UPDATE seller SET processed_at = ? WHERE seller_id = ?", now.minusDays(7), processed.seller().getId());
        assertThat(at(brandDetailJson(processed.marketId()).at("/basicInfo/approvedAt"))).isEqualTo(now.minusDays(7));

        BrandFixture.Brand none = cleanBrand("기록없는브랜드");
        brandDetail(none.marketId()).andExpect(jsonPath("$.basicInfo.approvedAt").doesNotExist());
    }

    @Test
    @DisplayName("DT-03 변경 요청 대기 배지 — 정산 계좌 · 사업자 정보 대기만 접수순 · 처리 · 취소 건 제외")
    void pendingChangeRequestBadge() throws Exception {
        BrandFixture.Brand owner = cleanBrand("변경요청브랜드");
        Long account = changeRequest(owner, ChangeRequestType.SETTLEMENT_ACCOUNT, ChangeRequestStatus.PENDING).getId();
        Long business = changeRequest(owner, ChangeRequestType.BUSINESS_INFO, ChangeRequestStatus.PENDING).getId();
        changeRequest(owner, ChangeRequestType.SETTLEMENT_ACCOUNT, ChangeRequestStatus.APPROVED);
        changeRequest(owner, ChangeRequestType.BUSINESS_INFO, ChangeRequestStatus.CANCELED);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE brand_change_request SET requested_at = ? WHERE request_id = ?", now.minusDays(2), business);
        jdbc.update("UPDATE brand_change_request SET requested_at = ? WHERE request_id = ?", now.minusDays(1), account);

        JsonNode badge = brandDetailJson(owner.marketId()).at("/settlementInfo/pendingChangeRequests");
        assertThat(badge).hasSize(2);
        assertThat(badge.get(0).path("requestId").asLong()).isEqualTo(business);
        assertThat(badge.get(0).path("type").asText()).isEqualTo("BUSINESS_INFO");
        assertThat(badge.get(0).path("typeLabel").asText()).isEqualTo("사업자 정보 변경 요청");
        assertThat(badge.get(0).path("requestCode").asText()).startsWith("CHG-");
        assertThat(badge.get(1).path("typeLabel").asText()).isEqualTo("정산 계좌 변경 요청");
    }

    @Test
    @DisplayName("DT-04 미입력 null 은 purged=false — 파기된 null 과 구분된다")
    void unfilledIsNotPurged() throws Exception {
        BrandFixture.Brand owner = cleanBrand("미입력브랜드");
        jdbc.update("UPDATE market SET brand_site_url = NULL WHERE market_id = ?", owner.marketId());
        jdbc.update("UPDATE seller SET mail_order_reg_num = NULL WHERE seller_id = ?", owner.seller().getId());
        brandDetail(owner.marketId()).andExpect(jsonPath("$.purged").value(false))
                .andExpect(jsonPath("$.basicInfo.siteUrl").doesNotExist())
                .andExpect(jsonPath("$.businessInfo.mailOrderRegNumber").doesNotExist())
                .andExpect(jsonPath("$.businessInfo.companyName").value("주식회사 미입력브랜드"));
    }

    @Test
    @DisplayName("DT-05 대상 경계 — 심사 대기 · 반려 셀러 · 인플루언서 역할 셀러의 마켓 · 없는 id 는 상세 · 하위 API · 조치 모두 404")
    void notBrandMembers() throws Exception {
        BrandFixture.Brand pending = cleanBrand("심사대기");
        BrandFixture.Brand rejected = cleanBrand("반려셀러");
        BrandFixture.Brand creatorRole = cleanBrand("크리에이터셀러");
        jdbc.update("UPDATE seller SET status = 'PENDING' WHERE seller_id = ?", pending.seller().getId());
        jdbc.update("UPDATE seller SET status = 'REJECTED' WHERE seller_id = ?", rejected.seller().getId());
        jdbc.update("UPDATE seller SET role_type = 'CREATOR' WHERE seller_id = ?", creatorRole.seller().getId());

        for (Long id : List.of(pending.marketId(), rejected.marketId(), creatorRole.marketId(), 999_999L)) {
            for (String sub : List.of("", "/histories", "/memos", "/connected-creators")) {
                adminGet(base(id) + sub).andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("MARKET_NOT_FOUND"));
            }
            suspend(id, "REPEATED_SHIPPING_DELAY", null).andExpect(status().isNotFound());
            release(id, Map.of()).andExpect(status().isNotFound());
        }
        assertThat(sanctionRepository.findAll()).isEmpty();
        assertThat(marketStatus(pending.marketId())).isEqualTo(MarketStatus.ACTIVE);
    }

    @Test
    @DisplayName("DT-06 이전 · 다음 — 회원번호(id) 순 · 대상 밖은 건너뛰고 탈퇴는 포함 · 끝은 null")
    void navigation() throws Exception {
        BrandFixture.Brand first = cleanBrand("이웃하나");
        BrandFixture.Brand skipped = cleanBrand("이웃심사");
        BrandFixture.Brand withdrawn = cleanBrand("이웃탈퇴");
        BrandFixture.Brand last = cleanBrand("이웃넷");
        jdbc.update("UPDATE seller SET status = 'PENDING' WHERE seller_id = ?", skipped.seller().getId());
        setMarketStatus(withdrawn.marketId(), MarketStatus.WITHDRAWN);

        brandDetail(brand.marketId()).andExpect(jsonPath("$.navigation.previousMarketId").doesNotExist())
                .andExpect(jsonPath("$.navigation.nextMarketId").value(first.marketId()));
        brandDetail(first.marketId()).andExpect(jsonPath("$.navigation.previousMarketId").value(brand.marketId()))
                .andExpect(jsonPath("$.navigation.nextMarketId").value(withdrawn.marketId()));
        brandDetail(withdrawn.marketId()).andExpect(jsonPath("$.navigation.previousMarketId").value(first.marketId()))
                .andExpect(jsonPath("$.navigation.nextMarketId").value(last.marketId()));
        brandDetail(last.marketId()).andExpect(jsonPath("$.navigation.nextMarketId").doesNotExist());
    }

    @Test
    @DisplayName("DT-07 메모 블록 — 최신 1건 + 전체 건수 · 다른 운영자 메모는 editable=false")
    void memoBlock() throws Exception {
        Seller other = fixture.createAdmin("other-ops@showroomz.test", "박운영");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        memoRepository.save(BrandAdminMemo.write(brand.marketId(), operator.getId(), "김운영", "첫 메모", now.minusHours(3)));
        memoRepository.save(BrandAdminMemo.write(brand.marketId(), operator.getId(), "김운영", "둘째 메모", now.minusHours(2)));
        memoRepository.save(BrandAdminMemo.write(brand.marketId(), other.getId(), "박운영", "최신 메모", now.minusHours(1)));

        brandDetail(brand.marketId()).andExpect(jsonPath("$.memos.total").value(3))
                .andExpect(jsonPath("$.memos.items.length()").value(1))
                .andExpect(jsonPath("$.memos.items[0].content").value("최신 메모"))
                .andExpect(jsonPath("$.memos.items[0].authorName").value("박운영"))
                .andExpect(jsonPath("$.memos.items[0].editable").value(false));
    }

    // ------------------------------------------------------------------ 3-2 배송 · 반품 정책

    @Test
    @DisplayName("DT-08 배송 · 반품 정책 — 값 그대로 · 발송 기한 문구 · 보조줄 없음이면 null · 배송 방법 · 택배사 연동 키 없음")
    void deliveryPolicy() throws Exception {
        BrandFixture.Brand owner = cleanBrand("배송정책브랜드");
        jdbc.update("UPDATE market SET default_delivery_fee = 3000, free_shipping_threshold = 50000, jeju_surcharge = 3000, "
                + "remote_area_surcharge = 5000, shipping_lead_days = 2 "
                + "WHERE market_id = ?", owner.marketId());

        JsonNode policy = brandDetailJson(owner.marketId()).path("deliveryPolicy");
        assertThat(policy.path("defaultDeliveryFee").asInt()).isEqualTo(3000);
        assertThat(policy.path("freeShippingThreshold").asInt()).isEqualTo(50000);
        assertThat(policy.path("jejuSurcharge").asInt()).isEqualTo(3000);
        assertThat(policy.path("remoteAreaSurcharge").asInt()).isEqualTo(5000);
        assertThat(policy.has("returnFee")).isFalse();
        assertThat(policy.path("shipLeadBusinessDays").asInt()).isEqualTo(2);
        assertThat(policy.path("shipLeadText").asText()).isNotBlank();
        assertThat(policy.path("shipLeadLastChange").isNull() || policy.path("shipLeadLastChange").isMissingNode()).isTrue();
        assertThat(policy.at("/returnAddress/address").asText()).isEqualTo("서울특별시 강남구 테헤란로 123");
        assertThat(policy.at("/returnAddress/detailAddress").asText()).isEqualTo("1층 물류센터");
        assertThat(policy.has("shippingMethod")).isFalse();
        assertThat(policy.has("courierIntegration")).isFalse();
    }

    // ------------------------------------------------------------------ 3-3 운영 현황 · 연결 · 스레드

    @Test
    @DisplayName("DT-09 진행중 공구는 판매 중(진행중 · 중단 예정)만 — 같은 데이터의 탈퇴 조건 ①은 준비 중까지 센다")
    void activeGroupBuysVersusCondition() throws Exception {
        seedIn(GroupBuyStatus.PREPARING);
        seedIn(GroupBuyStatus.READY);
        seedIn(GroupBuyStatus.IN_PROGRESS);
        seedIn(GroupBuyStatus.SUSPENSION_SCHEDULED);
        seedIn(GroupBuyStatus.ENDED);

        // 시드 공구(진행중) 1 + 진행중 1 + 중단 예정 1
        brandDetail(brand.marketId()).andExpect(jsonPath("$.operations.activeGroupBuyCount").value(3));
        assertThat(eligibility.evaluate(brand.marketId()).get(BrandWithdrawalCondition.ACTIVE_GROUP_BUYS)).isEqualTo(5);
    }

    @Test
    @DisplayName("DT-10 누적 정산액 = 브랜드 몫 지급 완료 합 · 미정산액 = 지급 전 합(해당 없음 제외) · 인플루언서 · 플랫폼 · 남의 행 제외")
    void settledAndUnsettled() throws Exception {
        Object[][] rows = {
                {PayoutStatus.PAID, 1_000_000L}, {PayoutStatus.PAID, 500_000L}, {PayoutStatus.SCHEDULED, 300_000L},
                {PayoutStatus.BLOCKED, 200_000L}, {PayoutStatus.WAITING, 100_000L}, {PayoutStatus.HELD, 50_000L},
                {PayoutStatus.REQUESTED, 30_000L}, {PayoutStatus.FAILED, 20_000L}, {PayoutStatus.NOT_APPLICABLE, 0L}};
        for (Object[] row : rows) {
            Settlement s = seedSettlement("정산 " + row[0], 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
            setPayout(s.getId(), SettlementPayee.BRAND, (PayoutStatus) row[0], (Long) row[1], null, LocalDate.now());
            setPayout(s.getId(), SettlementPayee.CREATOR, PayoutStatus.PAID, 777_777, null, LocalDate.now());
        }
        BrandFixture.Brand other = cleanBrand("남의정산");
        Settlement others = settlementOf(other, SettlementStatus.PAID);
        setPayout(others.getId(), SettlementPayee.BRAND, PayoutStatus.PAID, 9_999_999, null, LocalDate.now());

        brandDetail(brand.marketId()).andExpect(jsonPath("$.operations.settledAmountTotal").value(1_500_000))
                .andExpect(jsonPath("$.operations.unsettledAmount").value(700_000));
    }

    @Test
    @DisplayName("DT-11 지급 정지 표시 — 지급 정지 사유 정지만 true · 해제 · 지급 유지 사유 정지는 false")
    void payoutHeldFlag() throws Exception {
        BrandFixture.Brand owner = cleanBrand("지급표시");
        suspend(owner.marketId(), "SETTLEMENT_INFO_MISMATCH", null).andExpect(status().isOk());
        brandDetail(owner.marketId()).andExpect(jsonPath("$.operations.payoutHeld").value(true));
        release(owner.marketId(), Map.of()).andExpect(status().isOk());
        brandDetail(owner.marketId()).andExpect(jsonPath("$.operations.payoutHeld").value(false));
        suspend(owner.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        brandDetail(owner.marketId()).andExpect(jsonPath("$.operations.payoutHeld").value(false));
    }

    @Test
    @DisplayName("DT-12 · DT-13 계약 한 줄(서명 진행 중 · 체결 — 삭제분 제외) · 상품 한 줄(판매중 · 품절 — 미진열 제외)")
    void contractAndProductLine() throws Exception {
        BrandFixture.Brand owner = cleanBrand("한줄브랜드");
        contractIn(owner, creator, ContractStatus.SIGNING, false);
        contractIn(owner, creator, ContractStatus.SIGNING, false);
        contractIn(owner, creator, ContractStatus.CONCLUDED, false);
        contractIn(owner, creator, ContractStatus.CONCLUDED, true);
        contractIn(owner, creator, ContractStatus.DRAFT, false);

        Product selling = createProduct(owner, "판매중 상품", 10_000);
        Product empty = createProduct(owner, "재고없음 상품", 10_000);
        Product forced = createProduct(owner, "강제품절 상품", 10_000);
        Product hidden = createProduct(owner, "미진열 상품", 10_000);
        setStock(variantOf(selling), 5);
        setStock(variantOf(forced), 5);
        setStock(variantOf(hidden), 5);
        jdbc.update("UPDATE product SET is_out_of_stock_forced = true WHERE product_id = ?", forced.getProductId());
        jdbc.update("UPDATE product SET display_status = 'HIDDEN' WHERE product_id = ?", hidden.getProductId());
        assertThat(empty.getProductId()).isNotNull();

        brandDetail(owner.marketId()).andExpect(jsonPath("$.operations.contracts.signing").value(2))
                .andExpect(jsonPath("$.operations.contracts.concluded").value(1))
                .andExpect(jsonPath("$.operations.products.selling").value(1))
                .andExpect(jsonPath("$.operations.products.soldOut").value(2));
    }

    @Test
    @DisplayName("DT-14 연결 인플루언서 — 상세는 연결중 최근 3명 + 연결중 수 · 전체는 연결중 → 연결 종료 순 · 요청 · 거절 제외 · 이 쌍의 진행 공구 수")
    void connectedCreators() throws Exception {
        BrandFixture.Brand owner = cleanBrand("연결브랜드");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Creator[] connected = new Creator[4];
        for (int i = 0; i < 4; i++) {
            connected[i] = createCreator("연결쇼룸" + i, "conn" + i);
            pairConnected(owner, connected[i], now.minusDays(i + 1));
        }
        Creator gone1 = createCreator("종료쇼룸1", "gone1");
        Creator gone2 = createCreator("종료쇼룸2", "gone2");
        pairDisconnected(owner, gone1, now.minusDays(5));
        pairDisconnected(owner, gone2, now.minusDays(6));
        Creator requested = createCreator("요청쇼룸", "req");
        connectionRepository.save(showroomz.domain.connection.entity.Connection.requestPair(owner.market(), requested));
        Creator rejectedCreator = createCreator("거절쇼룸", "rej");
        Long rejectedId = connectionRepository.save(
                showroomz.domain.connection.entity.Connection.requestPair(owner.market(), rejectedCreator)).getId();
        jdbc.update("UPDATE connection SET status = 'REJECTED' WHERE connection_id = ?", rejectedId);
        operatorChannel(owner);
        GroupBuy selling = seed(owner, connected[0], "연결 공구", now.minusDays(2), now.plusDays(5));
        moveTo(selling.getId(), GroupBuyStatus.IN_PROGRESS);

        JsonNode preview = brandDetailJson(owner.marketId()).path("connectedCreators");
        assertThat(preview.path("total").asLong()).isEqualTo(4);
        assertThat(textsAt(preview.path("items"), "showroomName")).containsExactly("연결쇼룸0", "연결쇼룸1", "연결쇼룸2");
        assertThat(preview.path("items").get(0).path("activeGroupBuyCount").asLong()).isEqualTo(1);
        assertThat(preview.path("items").get(1).path("activeGroupBuyCount").asLong()).isZero();
        assertThat(preview.path("items").get(0).path("statusLabel").asText()).isEqualTo("연결중");
        assertThat(at(preview.path("items").get(0).path("connectedAt"))).isEqualTo(now.minusDays(1));

        JsonNode page = json(adminGet(base(owner.marketId()) + "/connected-creators").andExpect(status().isOk()));
        assertThat(textsAt(page.path("content"), "showroomName"))
                .containsExactly("연결쇼룸0", "연결쇼룸1", "연결쇼룸2", "연결쇼룸3", "종료쇼룸1", "종료쇼룸2");
        assertThat(page.path("content").get(4).path("status").asText()).isEqualTo("DISCONNECTED");
        assertThat(page.path("content").get(4).path("statusLabel").asText()).isEqualTo("연결 종료");
        assertThat(at(page.path("content").get(4).path("disconnectedAt"))).isEqualTo(now.minusDays(5));
    }

    @Test
    @DisplayName("DT-15 운영팀 스레드 — 채널이 없으면 null · 브랜드 글 2건이면 운영팀 미확인 2")
    void operatorThread() throws Exception {
        BrandFixture.Brand owner = cleanBrand("스레드브랜드");
        brandDetail(owner.marketId()).andExpect(jsonPath("$.operatorThread").doesNotExist());

        MessageThread channel = operatorChannel(owner);
        brandSays(channel, owner.seller(), "정산 계좌 문의드립니다.");
        brandSays(channel, owner.seller(), "확인 부탁드립니다.");
        brandDetail(owner.marketId()).andExpect(jsonPath("$.operatorThread.threadId").value(channel.getId()))
                .andExpect(jsonPath("$.operatorThread.unreadByOperator").value(2))
                .andExpect(jsonPath("$.operatorThread.lastMessageAt").exists());
    }

    // ------------------------------------------------------------------ 3-4 레일 — 현재 상태

    @Test
    @DisplayName("DT-16 조치 가능 여부 표 — 활성 · 활성+대기 · 정지 · 정지+대기 · 정지 행 없는 정지 · 탈퇴 · 휴면")
    void actionsTable() throws Exception {
        BrandFixture.Brand active = cleanBrand("표활성");
        BrandFixture.Brand activePending = cleanBrand("표활성대기");
        BrandFixture.Brand suspended = cleanBrand("표정지");
        BrandFixture.Brand suspendedPending = cleanBrand("표정지대기");
        BrandFixture.Brand rowless = cleanBrand("표행없는정지");
        BrandFixture.Brand withdrawn = cleanBrand("표탈퇴");
        BrandFixture.Brand dormant = cleanBrand("표휴면");
        LocalDateTime now = LocalDateTime.now();
        withdrawalService.request(activePending.marketId(), null, null, now);
        suspend(suspended.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        withdrawalService.request(suspendedPending.marketId(), null, null, now);
        suspend(suspendedPending.marketId(), "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        suspendWithoutSanctionRow(rowless.marketId());
        approve(withdrawn.marketId(), withdrawalService.request(withdrawn.marketId(), null, null, now).getId())
                .andExpect(status().isOk());
        setMarketStatus(dormant.marketId(), MarketStatus.DORMANT);

        assertActions(active, "ACTIVE", true, false, false);
        assertActions(activePending, "ACTIVE", true, false, true);
        assertActions(suspended, "SUSPENDED", false, true, false);
        assertActions(suspendedPending, "SUSPENDED", false, true, true);
        assertActions(rowless, "SUSPENDED", false, true, false);
        brandDetail(rowless.marketId()).andExpect(jsonPath("$.rail.currentStatus.sanction").doesNotExist());
        assertActions(withdrawn, "WITHDRAWN", false, false, false);
        assertActions(dormant, "ACTIVE", true, false, false);
    }

    @Test
    @DisplayName("DT-17 정지 블록 — 사유 · 라벨 · 상세 · 지급 정지 · 처리자 · 소명 스레드")
    void sanctionBlock() throws Exception {
        BrandFixture.Brand owner = cleanBrand("정지블록");
        suspend(owner.marketId(), "SETTLEMENT_INFO_MISMATCH", "예금주와 상호가 다릅니다.").andExpect(status().isOk());
        JsonNode body = brandDetailJson(owner.marketId());
        JsonNode sanction = body.at("/rail/currentStatus/sanction");
        assertThat(sanction.path("sanctionId").asLong()).isPositive();
        assertThat(sanction.path("reason").asText()).isEqualTo("SETTLEMENT_INFO_MISMATCH");
        assertThat(sanction.path("reasonLabel").asText()).isEqualTo("정산 정보 불일치");
        assertThat(sanction.path("reasonDetail").asText()).isEqualTo("예금주와 상호가 다릅니다.");
        assertThat(sanction.path("payoutHeld").asBoolean()).isTrue();
        assertThat(sanction.path("suspendedByName").asText()).isEqualTo("김운영");
        assertThat(sanction.path("suspendedAt").isMissingNode()).isFalse();
        assertThat(sanction.path("threadId").asLong()).isEqualTo(body.at("/operatorThread/threadId").asLong());
    }

    @Test
    @DisplayName("DT-18 최근 접속 — 로그인 전 null · 브랜드 로그인 뒤 값")
    void lastLoginAt() throws Exception {
        BrandFixture.Brand owner = cleanBrand("접속브랜드");
        brandDetail(owner.marketId()).andExpect(jsonPath("$.rail.currentStatus.lastLoginAt").doesNotExist());
        postAs(null, "/v1/seller/auth/login", Map.of("email", owner.email(), "password", RAW_PASSWORD))
                .andExpect(status().isOk());
        brandDetail(owner.marketId()).andExpect(jsonPath("$.rail.currentStatus.lastLoginAt").exists());
    }

    // ------------------------------------------------------------------ 3-6 탈퇴 신청 심사 카드

    @Test
    @DisplayName("DT-19 · DT-20 심사 카드 — 신청자 스냅샷 · 사유 라벨(미선택은 「선택하지 않음」) · 상태는 활성 그대로 · 레일 이력은 2건(취소하면 3건)")
    void withdrawalReviewCard() throws Exception {
        BrandFixture.Brand owner = cleanBrand("심사카드");
        adminPost(base(owner.marketId()) + "/memos", Map.of("content", "첫 메모")).andExpect(status().isOk());
        adminPost(base(owner.marketId()) + "/memos", Map.of("content", "둘째 메모")).andExpect(status().isOk());
        Long requestId = withdrawalService.request(owner.marketId(), BrandWithdrawalReason.OTHER_CHANNEL_FOCUS,
                "자사몰 운영에 집중하기 위해 입점을 종료합니다.", LocalDateTime.now()).getId();

        brandDetail(owner.marketId()).andExpect(jsonPath("$.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.withdrawalPending").value(true))
                .andExpect(jsonPath("$.rail.withdrawalReview.requestId").value(requestId))
                .andExpect(jsonPath("$.rail.withdrawalReview.elapsedDays").value(0))
                .andExpect(jsonPath("$.rail.withdrawalReview.requesterName").value("김담당"))
                .andExpect(jsonPath("$.rail.withdrawalReview.reason").value("OTHER_CHANNEL_FOCUS"))
                .andExpect(jsonPath("$.rail.withdrawalReview.reasonLabel").value("다른 채널에 집중"))
                .andExpect(jsonPath("$.rail.withdrawalReview.message").value("자사몰 운영에 집중하기 위해 입점을 종료합니다."))
                .andExpect(jsonPath("$.rail.histories.items.length()").value(2))
                .andExpect(jsonPath("$.rail.histories.total").value(4));

        withdrawalService.cancel(owner.marketId(), requestId, LocalDateTime.now());
        brandDetail(owner.marketId()).andExpect(jsonPath("$.rail.withdrawalReview").doesNotExist())
                .andExpect(jsonPath("$.rail.histories.items.length()").value(3));

        BrandFixture.Brand unselected = cleanBrand("사유미선택");
        withdrawalService.request(unselected.marketId(), null, null, LocalDateTime.now());
        brandDetail(unselected.marketId()).andExpect(jsonPath("$.rail.withdrawalReview.reason").doesNotExist())
                .andExpect(jsonPath("$.rail.withdrawalReview.reasonLabel").value("선택하지 않음"))
                .andExpect(jsonPath("$.rail.histories.items[0].detail").value("사유: 선택하지 않음"));
    }

    @Test
    @DisplayName("DT-21 경과 일수 — 24시간 단위(날짜 차가 아니다) · 음수 없음")
    void elapsedDays() throws Exception {
        BrandFixture.Brand owner = cleanBrand("경과브랜드");
        Long requestId = withdrawalService.request(owner.marketId(), null, null, LocalDateTime.now()).getId();
        LocalDateTime now = LocalDateTime.now();
        Object[][] cases = {{now.minusDays(6).minusMinutes(1), 6}, {now.minusHours(23).minusMinutes(59), 0},
                {now.minusHours(24).minusMinutes(1), 1}, {now.plusHours(1), 0}};
        for (Object[] c : cases) {
            jdbc.update("UPDATE brand_withdrawal_request SET requested_at = ? WHERE request_id = ?", c[0], requestId);
            brandDetail(owner.marketId()).andExpect(jsonPath("$.rail.withdrawalReview.elapsedDays").value(c[1]));
        }
    }

    // ------------------------------------------------------------------ 3-5 진행 중 칩

    @Test
    @DisplayName("CH-01 칩 8종 전수 — enum 순서 · 라벨 · 이동 화면 · 운영팀 채널 칩에만 threadId")
    void allEightChips() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        OrderDeliveryGroup overdue = paidGroup();
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = ? WHERE delivery_group_id = ?", now.minusHours(1),
                overdue.getId());
        OrderDeliveryGroup cancelAsked = paidGroup();
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = NULL WHERE delivery_group_id = ?", cancelAsked.getId());
        seedCancelRequest(cancelAsked, itemsOf(cancelAsked));
        setClaimStatus(returnedClaim(), "ARRIVED");
        setClaimStatus(returnedClaim(), "RESHIP_READY");
        inquiry(cream);
        MessageThread channel = operatorChannel(brand);
        brandSays(channel, brand.seller(), "소명드립니다.");
        seedSettlement("조정 협의 정산", 1_000_000, SettlementStatus.ADJUSTING);
        brandTaxInvoice(seedSettlement("세금계산서 정산", 1_000_000, SettlementStatus.REVIEWING), TaxDocumentStatus.PENDING_ISSUE);

        JsonNode chips = brandDetailJson(brand.marketId()).at("/rail/pendingChips");
        assertThat(textsAt(chips, "key")).containsExactly("SHIP_OVERDUE", "CANCEL_RESPONSE_PENDING", "INSPECTION_PENDING",
                "RESHIP_PENDING", "PRODUCT_INQUIRY_UNANSWERED", "OPERATOR_THREAD_UNREAD", "SETTLEMENT_ADJUSTING",
                "BRAND_TAX_INVOICE_PENDING");
        assertThat(textsAt(chips, "target")).containsExactly("ORDERS", "ORDERS", "CLAIMS", "CLAIMS", "PRODUCT_INQUIRIES",
                "THREAD", "SETTLEMENTS", "SETTLEMENTS");
        assertThat(textsAt(chips, "label")).containsExactly("발송 기한 경과", "취소 요청 응답 대기", "검수 대기", "재발송 대기",
                "상품 문의 미답변", "운영팀 스레드 미확인", "조정 협의", "세금계산서 발행 대기");
        for (JsonNode chip : chips) {
            assertThat(chip.path("count").asLong()).as(chip.path("key").asText()).isEqualTo(1);
            if ("THREAD".equals(chip.path("target").asText())) {
                assertThat(chip.path("threadId").asLong()).isEqualTo(channel.getId());
            } else {
                assertThat(chip.path("threadId").isNull() || chip.path("threadId").isMissingNode()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("CH-02 · CH-10 재료가 없으면 빈 배열 · 탈퇴 대기는 칩이 아니다")
    void noChips() throws Exception {
        BrandFixture.Brand owner = cleanBrand("칩없음");
        withdrawalService.request(owner.marketId(), null, null, LocalDateTime.now());
        brandDetail(owner.marketId()).andExpect(jsonPath("$.rail.pendingChips.length()").value(0));
    }

    @Test
    @DisplayName("CH-03 발송 기한 경과 — 결제된 신규 · 준비중만 · 배송중 · 기한 없음 · 미결제 · 기한 전은 세지 않는다")
    void shipOverdueBoundaries() throws Exception {
        LocalDateTime past = LocalDateTime.now().minusHours(1);
        OrderDeliveryGroup fresh = paidGroup();
        OrderDeliveryGroup preparing = preparingGroup();
        OrderDeliveryGroup shipping = shippingGroup(nextTrackingNumber());
        OrderDeliveryGroup noDue = paidGroup();
        OrderDeliveryGroup unpaid = paidGroup();
        OrderDeliveryGroup future = paidGroup();
        for (OrderDeliveryGroup g : List.of(fresh, preparing, shipping, unpaid)) {
            setShipDue(g, past);
        }
        setShipDue(noDue, null);
        setShipDue(future, LocalDateTime.now().plusDays(1));
        jdbc.update("UPDATE orders SET paid_at = NULL WHERE order_id = "
                + "(SELECT order_id FROM order_delivery_group WHERE delivery_group_id = ?)", unpaid.getId());

        assertThat(chipCount(brand.marketId(), "SHIP_OVERDUE")).isEqualTo(2);
    }

    @Test
    @DisplayName("CH-04 취소 요청 응답 대기 — 신규 · 준비중 하위주문의 대기 요청만 · 처리된 요청 제외")
    void cancelResponsePending() throws Exception {
        OrderDeliveryGroup fresh = paidGroup();
        OrderDeliveryGroup preparing = preparingGroup();
        OrderDeliveryGroup shipping = shippingGroup(nextTrackingNumber());
        OrderDeliveryGroup processed = paidGroup();
        for (OrderDeliveryGroup g : List.of(fresh, preparing, shipping, processed)) {
            setShipDue(g, null);
            seedCancelRequest(g, itemsOf(g));
        }
        jdbc.update("UPDATE order_cancel_request SET status = 'REJECTED' WHERE delivery_group_id = ?", processed.getId());

        assertThat(chipCount(brand.marketId(), "CANCEL_RESPONSE_PENDING")).isEqualTo(2);
    }

    @Test
    @DisplayName("CH-05 검수 · 재발송 대기 — 입고 확인 전 · 검수 대기 = 검수 · 재발송 대기 = 재발송 · 완료는 칩 없음")
    void inspectionAndReship() throws Exception {
        Long claimId = returnedClaim();
        Object[][] steps = {{"ARRIVED", 1L, 0L}, {"RECEIVED", 1L, 0L}, {"RESHIP_READY", 0L, 1L}, {"COMPLETED", 0L, 0L}};
        for (Object[] step : steps) {
            setClaimStatus(claimId, (String) step[0]);
            assertThat(chipCount(brand.marketId(), "INSPECTION_PENDING")).as((String) step[0]).isEqualTo(step[1]);
            assertThat(chipCount(brand.marketId(), "RESHIP_PENDING")).as((String) step[0]).isEqualTo(step[2]);
        }
    }

    @Test
    @DisplayName("CH-06 상품 문의 미답변 — 접수 상태만 · 삭제된 문의 · 답변 완료 · 남의 상품 제외")
    void productInquiryUnanswered() throws Exception {
        inquiry(cream);
        ProductInquiry deleted = inquiry(cream);
        jdbc.update("UPDATE product_inquiry SET exposure_status = 'DELETED' WHERE product_inquiry_id = ?", deleted.getId());
        ProductInquiry answered = inquiry(serum);
        jdbc.update("UPDATE product_inquiry SET status = 'ANSWERED' WHERE product_inquiry_id = ?", answered.getId());
        BrandFixture.Brand other = cleanBrand("남의문의");
        inquiry(createProduct(other, "남의 상품", 10_000));

        assertThat(chipCount(brand.marketId(), "PRODUCT_INQUIRY_UNANSWERED")).isEqualTo(1);
    }

    @Test
    @DisplayName("CH-07 운영팀 스레드 미확인 — 브랜드 글 3건 → 칩 3 · 운영팀이 읽으면 칩이 사라진다")
    void operatorThreadUnread() throws Exception {
        MessageThread channel = operatorChannel(brand);
        for (int i = 0; i < 3; i++) {
            brandSays(channel, brand.seller(), "문의 " + i);
        }
        JsonNode chip = chip(brand.marketId(), "OPERATOR_THREAD_UNREAD");
        assertThat(chip.path("count").asLong()).isEqualTo(3);
        assertThat(chip.path("tone").asText()).isEqualTo("WARNING");
        assertThat(chip.path("threadId").asLong()).isEqualTo(channel.getId());

        inTransaction(() -> {
            messageThreadService.markReadByOperatorTeam(messageThreadRepository.findById(channel.getId()).orElseThrow());
            return null;
        });
        assertThat(chipCount(brand.marketId(), "OPERATOR_THREAD_UNREAD")).isZero();
    }

    @Test
    @DisplayName("CH-08 조정 협의 · 세금계산서 발행 대기 — 조정 협의 정산 수 · 브랜드 세금계산서 발행 대기만")
    void settlementChips() throws Exception {
        seedSettlement("조정1", 1_000_000, SettlementStatus.ADJUSTING);
        Settlement adjusting = seedSettlement("조정2", 1_000_000, SettlementStatus.ADJUSTING);
        Settlement reviewing = seedSettlement("확인중", 1_000_000, SettlementStatus.REVIEWING);
        brandTaxInvoice(reviewing, TaxDocumentStatus.PENDING_ISSUE);
        brandTaxInvoice(adjusting, TaxDocumentStatus.ISSUED);
        taxDocuments.save(SettlementTaxDocument.create(reviewing.getId(), TaxDocumentType.CREATOR_TAX_INVOICE,
                TaxDocumentStatus.PENDING_ISSUE, 10_000, 1_000, "글로우_지민", null, null, LocalDate.now().plusDays(5),
                LocalDateTime.now()));

        assertThat(chipCount(brand.marketId(), "SETTLEMENT_ADJUSTING")).isEqualTo(2);
        assertThat(chipCount(brand.marketId(), "BRAND_TAX_INVOICE_PENDING")).isEqualTo(1);
    }

    @Test
    @DisplayName("CH-09 탈퇴 계정은 재료가 남아 있어도 칩을 집계하지 않는다")
    void withdrawnHasNoChips() throws Exception {
        inquiry(cream);
        seedSettlement("조정 협의 정산", 1_000_000, SettlementStatus.ADJUSTING);
        assertThat(brandDetailJson(brand.marketId()).at("/rail/pendingChips")).isNotEmpty();
        setMarketStatus(brand.marketId(), MarketStatus.WITHDRAWN);
        brandDetail(brand.marketId()).andExpect(jsonPath("$.rail.pendingChips.length()").value(0));
    }

    // ------------------------------------------------------------------ 보조

    private void assertActions(BrandFixture.Brand target, String status, boolean canSuspend, boolean canRelease,
                               boolean canReview) throws Exception {
        brandDetail(target.marketId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.memberStatus").value(status))
                .andExpect(jsonPath("$.rail.currentStatus.actions.canSuspend").value(canSuspend))
                .andExpect(jsonPath("$.rail.currentStatus.actions.canRelease").value(canRelease))
                .andExpect(jsonPath("$.rail.currentStatus.actions.canReviewWithdrawal").value(canReview));
    }

    private ProductVariant variantOf(Product product) {
        return ContractOptions.variantsOf(productVariantRepository, product).get(0);
    }

    private void setShipDue(OrderDeliveryGroup group, LocalDateTime dueAt) {
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = ? WHERE delivery_group_id = ?", dueAt, group.getId());
    }

    /** 배송완료(어제) 하위주문의 반품 클레임 — 단계는 호출자가 SQL 로 옮긴다. */
    private Long returnedClaim() {
        OrderDeliveryGroup delivered = deliveredFrom(paidGroup(), LocalDateTime.now().minusDays(1));
        return returnClaim(delivered, itemOf(delivered, cream), 1, ClaimReason.CHANGE_OF_MIND);
    }

    private void setClaimStatus(Long claimId, String status) {
        jdbc.update("UPDATE order_claim SET status = ? WHERE claim_id = ?", status, claimId);
    }

    private JsonNode chip(Long marketId, String key) throws Exception {
        for (JsonNode chip : brandDetailJson(marketId).at("/rail/pendingChips")) {
            if (key.equals(chip.path("key").asText())) {
                return chip;
            }
        }
        return null;
    }

    private long chipCount(Long marketId, String key) throws Exception {
        JsonNode chip = chip(marketId, key);
        return chip == null ? 0 : chip.path("count").asLong();
    }
}
