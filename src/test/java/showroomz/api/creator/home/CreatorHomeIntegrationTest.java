package showroomz.api.creator.home;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.contract.type.SecondaryUsePeriodType;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쇼룸 스튜디오 홈(1017 설계서 5절) — 할 일 정렬 · 문구 · 정지 중 막힘 · 실시간 띠(확정 기준) · 달력 · 가이드 · 리워드 지급 조건.
 * 진행 중 공구 1건(브랜드 「글로우랩」 · 인플루언서 「글로우_지민」 · 연결됨)과 소비자 주문 픽스처는 {@link SettlementTestSupport} 가 깐다.
 */
class CreatorHomeIntegrationTest extends SettlementTestSupport {

    private static final String HOME = "/v1/creator/home";

    private int contractSeq;

    @Test
    @DisplayName("S-01 할 일 — 세금계산서 맨 위 · 기한순(서명 → 연장 응답 → 게시물) · 연결 요청 한 장 맨 아래 · 서버 문구")
    void todosOrderedWithServerSentences() throws Exception {
        Settlement settlement = seedSettlement("지난 공구", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        seedTaxInvoice(settlement, "PENDING_INPUT");
        Contract signing = seedSigningContract("수분 앰플 공구", LocalDateTime.now().plusDays(3), true);
        seedExtensionRequest(groupBuy);
        GroupBuy toWrite = seedIn(GroupBuyStatus.PREPARING);
        GroupBuy rejected = seedIn(GroupBuyStatus.PREPARING);
        seedPost(rejected.getId(), GroupBuyPostReviewStatus.REJECTED, false);
        requestConnectionFrom("코스랩");
        requestConnectionFrom("데일리랩");
        requestConnectionFrom("모닝랩");

        JsonNode todos = json(creatorGet(HOME).andExpect(status().isOk())).get("todos");
        List<String> kinds = new ArrayList<>();
        todos.forEach(t -> kinds.add(t.get("kind").asText()));
        assertThat(kinds).containsExactly("TAX_INVOICE_ISSUE", "CONTRACT_SIGN", "EXTENSION_RESPOND",
                "GROUP_BUY_POST_WRITE", "GROUP_BUY_POST_RESUBMIT", "CONNECTION_REQUESTS");

        JsonNode tax = todos.get(0);
        assertThat(tax.get("dueLabel").asText()).isEqualTo("지급 대기");
        assertThat(tax.get("title").asText()).isEqualTo("세금계산서 발행 · 지난 공구");
        assertThat(tax.at("/target/id").asLong()).isEqualTo(settlement.getId());
        assertThat(tax.at("/extra/amount").asLong()).isEqualTo(settlement.getCreatorPayoutAmount());
        assertThat(tax.at("/extra/rejected").asBoolean()).isFalse();

        JsonNode sign = todos.get(1);
        assertThat(sign.get("title").asText()).isEqualTo("계약 서명 · 수분 앰플 공구");
        assertThat(sign.get("description").asText()).startsWith(brand.market().getMarketName() + " 브랜드가 서명했어요");
        assertThat(sign.at("/target/id").asLong()).isEqualTo(signing.getId());
        assertThat(sign.get("dueLabel").asText()).startsWith("서명 기한 ");

        assertThat(todos.get(2).at("/target/id").asLong()).isEqualTo(groupBuy.getId());
        assertThat(todos.get(3).at("/target/id").asLong()).isEqualTo(toWrite.getId());
        assertThat(todos.get(3).get("dueLabel").asText()).startsWith("오픈 ");
        assertThat(todos.get(4).at("/target/id").asLong()).isEqualTo(rejected.getId());

        JsonNode connections = todos.get(5);
        assertThat(connections.get("title").asText()).isEqualTo("연결 요청 · 모닝랩 · 데일리랩 외 1곳");
        assertThat(connections.at("/extra/count").asLong()).isEqualTo(3);
        todos.forEach(t -> assertThat(t.get("blocked").isNull()).isTrue());
    }

    @Test
    @DisplayName("S-02 정지 중 — 할 일은 숨기지 않고 막힌 카드만 blocked(연장 수락 · 게시물 등록 · 연결 수락) · 서명은 그대로")
    void suspendedBlocksOnlySomeTodos() throws Exception {
        seedSigningContract("수분 앰플 공구", LocalDateTime.now().plusDays(3), false);
        seedExtensionRequest(groupBuy);
        seedIn(GroupBuyStatus.PREPARING);
        requestConnectionFrom("코스랩");
        jdbc.update("UPDATE creator SET status = 'SUSPENDED' WHERE creator_id = ?", creator.getId());

        JsonNode home = json(creatorGet(HOME).andExpect(status().isOk()));
        assertThat(home.at("/account/status").asText()).isEqualTo("SUSPENDED");
        for (JsonNode todo : home.get("todos")) {
            String kind = todo.get("kind").asText();
            boolean blocked = !todo.get("blocked").isNull();
            switch (kind) {
                case "CONTRACT_SIGN" -> assertThat(blocked).as(kind).isFalse();
                case "EXTENSION_RESPOND" -> assertThat(todo.at("/blocked/message").asText())
                        .isEqualTo("연장 수락은 정지 해제 후 할 수 있어요");
                case "GROUP_BUY_POST_WRITE" -> assertThat(todo.at("/blocked/message").asText())
                        .isEqualTo("정지 해제 후 등록할 수 있어요");
                case "CONNECTION_REQUESTS" -> assertThat(blocked).as(kind).isTrue();
                default -> {
                }
            }
        }
        assertThat(home.get("todos")).hasSize(4);
    }

    @Test
    @DisplayName("S-03 실시간 띠 — 공구 관리 잠정 실적과 같은 값(결제 뒤 취소는 빠진다) · 오늘 + · 쇼룸 방문 · 진행 중 없으면 null")
    void liveUsesConfirmedDefinition() throws Exception {
        Purchase kept = purchase(creamVariant, 2);
        Purchase cancelled = purchase(serumVariant, 1);
        jdbc.update("UPDATE orders SET status = 'CANCELLED', cancelled_at = ? WHERE order_id = ?",
                LocalDateTime.now(), cancelled.orderId());
        jdbc.update("UPDATE order_product SET status = 'CANCELLED' WHERE order_id = ?", cancelled.orderId());
        seedVisit("v-1", LocalDateTime.now().minusMinutes(5));
        seedVisit("v-2", LocalDateTime.now().minusDays(1));

        JsonNode live = json(creatorGet(HOME + "/live").andExpect(status().isOk()));
        assertThat(live.get("groupBuyId").asLong()).isEqualTo(groupBuy.getId());
        assertThat(live.get("brandName").asText()).isEqualTo(brand.market().getMarketName());
        assertThat(live.get("salesAmount").asLong()).isEqualTo(CREAM_PRICE * 2L);
        assertThat(live.get("salesAmountToday").asLong()).isEqualTo(CREAM_PRICE * 2L);
        assertThat(live.get("orders").asLong()).isEqualTo(1);
        assertThat(live.get("ordersToday").asLong()).isEqualTo(1);
        assertThat(live.get("visits").asLong()).isEqualTo(2);
        assertThat(live.get("visitsToday").asLong()).isEqualTo(1);
        assertThat(kept.orderId()).isNotNull();

        // 파트너 홈 live 는 같은 주문을 결제 기준(취소 전 포함)으로 센다 — 정의가 다르다.
        JsonNode partner = json(sellerGet("/v1/seller/home/live").andExpect(status().isOk()));
        assertThat(partner.at("/items/0/ordersToday").asLong()).isEqualTo(2);

        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        assertThat(creatorGet(HOME + "/live").andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString()).isEmpty();
        assertThat(json(creatorGet(HOME).andExpect(status().isOk())).get("live").isNull()).isTrue();
    }

    @Test
    @DisplayName("S-04 달력 — 내 공구 막대 · 내 서명 기한(보류 중 · 이미 서명한 건 제외) · 연장 응답 기한 = 공구 종료일")
    void calendar() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Contract signing = seedSigningContract("서명 공구", now.plusDays(2), false);
        Contract held = seedSigningContract("보류 공구", now.plusDays(2), false);
        jdbc.update("UPDATE contract SET sanction_hold_since = ?, sanction_hold_party = 'BRAND' WHERE contract_id = ?",
                now, held.getId());
        Contract signedByMe = seedSigningContract("내가 서명한 공구", now.plusDays(2), false);
        jdbc.update("UPDATE contract SET creator_signed_at = ? WHERE contract_id = ?", now, signedByMe.getId());
        seedExtensionRequest(groupBuy);

        String month = YearMonth.from(now).toString();
        JsonNode calendar = json(creatorGet(HOME + "/calendar?month=" + month).andExpect(status().isOk()));
        assertThat(calendar.at("/groupBuys/0/groupBuyId").asLong()).isEqualTo(groupBuy.getId());
        assertThat(calendar.at("/groupBuys/0/status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(calendar.at("/groupBuys/0/brandName").asText()).isEqualTo(brand.market().getMarketName());

        List<String> deadlines = new ArrayList<>();
        calendar.get("deadlines").forEach(d -> deadlines.add(d.get("kind").asText() + "|" + d.get("targetId").asLong()));
        assertThat(deadlines).contains("SIGN_DUE|" + signing.getId(), "EXTENSION_RESPOND_DUE|" + groupBuy.getId())
                .doesNotContain("SIGN_DUE|" + held.getId(), "SIGN_DUE|" + signedByMe.getId());
        creatorGet(HOME + "/calendar?month=2026-1").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("S-05 시작 가이드 — 연결 요청을 받은 적 없을 때만 · 체크리스트 자동 판정 · 링크는 판정 없이 주소만")
    void guide() throws Exception {
        Creator newbie = createCreator("새싹쇼룸", "sprout");
        jdbc.update("UPDATE creator SET showroom_address = 'sprout', introduction = '안녕하세요', "
                + "profile_image_url = 'https://cdn.test/p.png' WHERE creator_id = ?", newbie.getId());
        String token = bearerToken(newbie.getUser().getUsername(), RoleType.CREATOR, newbie.getUser().getId());

        JsonNode guide = json(creatorGet(HOME, token).andExpect(status().isOk())).get("guide");
        assertThat(guide.get("visible").asBoolean()).isTrue();
        assertThat(guide.get("total").asInt()).isEqualTo(4);
        assertThat(guide.at("/checklist/0/key").asText()).isEqualTo("SETTLEMENT_INFO");
        assertThat(guide.at("/checklist/0/done").asBoolean()).isFalse();
        assertThat(guide.at("/checklist/1/done").asBoolean()).isTrue();
        assertThat(guide.at("/checklist/3/done").isNull()).isTrue();
        assertThat(guide.at("/checklist/3/showroomUrl").asText()).endsWith("/@sprout");
        assertThat(guide.get("completed").asInt()).isEqualTo(1);

        // 첫 연결 요청이 오면 사라진다.
        BrandFixture.Brand other = fixture.createBrand("cos@showroomz.test", "코스랩");
        connectionRepository.save(Connection.requestPair(other.market(), newbie));
        assertThat(json(creatorGet(HOME, token).andExpect(status().isOk())).at("/guide/visible").asBoolean()).isFalse();
        // 기존 인플루언서는 연결돼 있다.
        assertThat(json(creatorGet(HOME).andExpect(status().isOk())).at("/guide/visible").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("S-06 리워드 — 지급 예정 · 지급 조건(세금계산서 확인 전 보류) · 정산 요약과 같은 값 · 쇼룸 7일")
    void rewardAndShowroom() throws Exception {
        Settlement settlement = seedSettlement("지난 공구", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        seedTaxInvoice(settlement, "SUBMITTED");
        jdbc.update("UPDATE settlement_payout SET status = 'BLOCKED' WHERE settlement_id = ? AND payee = 'CREATOR'",
                settlement.getId());
        seedVisit("v-1", LocalDateTime.now().minusDays(1));

        JsonNode home = json(creatorGet(HOME).andExpect(status().isOk()));
        JsonNode summary = json(creatorGet("/v1/creator/settlements/summary").andExpect(status().isOk()));
        assertThat(home.at("/reward/payoutCondition").asText()).isEqualTo("TAX_INVOICE_UNVERIFIED");
        assertThat(summary.get("payoutCondition").asText()).isEqualTo("TAX_INVOICE_UNVERIFIED");
        assertThat(home.at("/reward/payoutScheduledAmount").asLong())
                .isEqualTo(summary.at("/payoutScheduled/amount").asLong()).isPositive();
        assertThat(home.at("/showroom7d/visitors").asLong()).isEqualTo(1);
        assertThat(home.at("/showroom7d/conversionRate").asDouble()).isZero();
        // 제출된 세금계산서는 할 일이 아니다(입력 대기 · 반려만).
        home.get("todos").forEach(t -> assertThat(t.get("kind").asText()).isNotEqualTo("TAX_INVOICE_ISSUE"));

        jdbc.update("UPDATE settlement_tax_document SET status = 'VERIFIED' WHERE settlement_id = ?", settlement.getId());
        assertThat(json(creatorGet(HOME).andExpect(status().isOk())).at("/reward/payoutCondition").isNull()).isTrue();
    }

    @Test
    @DisplayName("S-07 브랜드 토큰은 스튜디오 홈에 들어오지 못한다(403)")
    void sellerCannotReadCreatorHome() throws Exception {
        sellerGet(HOME).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 픽스처

    private org.springframework.test.web.servlet.ResultActions creatorGet(String url, String token) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, token));
    }

    /** 서명 진행중 계약 — 검토 통과까지. {@code brandSigned} 면 브랜드만 서명했다. */
    private Contract seedSigningContract(String title, LocalDateTime signatureDeadlineAt, boolean brandSigned) {
        Contract saved = transactionTemplate.execute(tx -> {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            LocalDateTime startAt = now.plusDays(20);
            Contract contract = Contract.createDraft(brand.market(), creator, null);
            contract.updateTerms(title, startAt, startAt.plusDays(7), 300_000, FixedFeeTrigger.POST_REGISTERED, now,
                    1, 1, 3, startAt.toLocalDate().plusDays(10), true, SecondaryUsePeriodType.FIXED, 12, false, null);
            List<ContractItem> items = new ArrayList<>();
            items.add(item(cream, 27_200, "12.0", 300));
            contract.replaceItems(items);
            contract.applyReviewRequested("CTR-STUDIO-%03d".formatted(++contractSeq), null, null, now.minusDays(2));
            contract.approveReview(now.minusDays(1), signatureDeadlineAt, now.minusDays(1));
            return contractRepository.saveAndFlush(contract);
        });
        if (brandSigned) {
            jdbc.update("UPDATE contract SET brand_signed_at = ? WHERE contract_id = ?", LocalDateTime.now().minusHours(3),
                    saved.getId());
        }
        return saved;
    }

    private void seedExtensionRequest(GroupBuy target) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("INSERT INTO group_buy_extension_request (group_buy_id, requested_by, extension_days, reason, "
                        + "before_end_at, after_end_at, status, requested_at) VALUES (?, ?, 3, '재고 추가 입고', ?, ?, 'PENDING', ?)",
                target.getId(), brand.seller().getId(), target.getEndAt(), target.getEndAt().plusDays(3), now.minusHours(1));
    }

    private void seedTaxInvoice(Settlement settlement, String status) {
        jdbc.update("INSERT INTO settlement_tax_document (settlement_id, type, status, supply_amount, vat_amount, "
                        + "total_amount, created_at) VALUES (?, 'CREATOR_TAX_INVOICE', ?, 100000, 10000, 110000, ?)",
                settlement.getId(), status, LocalDateTime.now());
        jdbc.update("UPDATE settlement SET confirmed_at = ? WHERE settlement_id = ?", LocalDateTime.now().minusDays(1),
                settlement.getId());
    }

    private void requestConnectionFrom(String brandName) throws InterruptedException {
        BrandFixture.Brand other = fixture.createBrand(brandName.hashCode() + "@showroomz.test", brandName);
        connectionRepository.save(Connection.requestPair(other.market(), creator));
        Thread.sleep(5); // 요청 시각 순서(최신순 이름 2곳)를 분명히 한다
    }

    private void seedVisit(String visitorKey, LocalDateTime at) {
        jdbc.update("INSERT INTO showroom_visit (creator_id, visitor_key, source, visited_at) VALUES (?, ?, 'APP_SEARCH', ?)",
                creator.getId(), visitorKey, at);
    }
}
