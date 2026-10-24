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
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.post.entity.Post;
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
 * 쇼룸 스튜디오 홈 세부(1017 설계서 5절 · 9절) — 다가오는 일정(H2) · 세금계산서 반려 · 체결 보류 서명 · 지난 연장 요청 · 정지 중에도
 * 허용되는 할 일 · 가이드 체크리스트 · 연결 요청 카드 · 실시간 방문 경계.
 */
@DisplayName("1017 스튜디오 홈 세부")
class CreatorHomeDetailIntegrationTest extends SettlementTestSupport {

    private static final String HOME = "/v1/creator/home";

    private int contractSeq;

    @Test
    @DisplayName("CD-01 다가오는 일정 — 오늘 이후 오픈 · 종료 3건 · 날짜순 · 같은 날이면 종료 먼저 · 설명은 종류별 고정 문구")
    void upcomingOrdering() throws Exception {
        // 진행 중 공구(fixture)는 4일 뒤 종료 — 같은 날 오픈하는 준비완료 공구를 둔다
        LocalDateTime sameDay = groupBuy.getEndAt().withHour(9).withMinute(0).withSecond(0);
        GroupBuy ready = seed(brand, creator, "같은 날 오픈 공구", sameDay, sameDay.plusDays(7));
        moveTo(ready.getId(), GroupBuyStatus.READY);
        GroupBuy later = seed(brand, creator, "나중 공구", sameDay.plusDays(20), sameDay.plusDays(27));
        moveTo(later.getId(), GroupBuyStatus.READY);

        JsonNode upcoming = json(creatorGet(HOME).andExpect(status().isOk())).get("upcoming");
        assertThat(upcoming).hasSize(3);
        assertThat(upcoming.at("/0/title").asText()).isEqualTo("공구 종료 · 글로우 크림 앵콜 공구");
        assertThat(upcoming.at("/0/description").asText()).isEqualTo("종료 후 주문이 모두 끝나면 정산이 생성돼요");
        assertThat(upcoming.at("/1/title").asText()).isEqualTo("공구 오픈 · 같은 날 오픈 공구");
        assertThat(upcoming.at("/1/date").asText()).isEqualTo(upcoming.at("/0/date").asText());
        assertThat(upcoming.at("/2/title").asText()).isEqualTo("공구 종료 · 같은 날 오픈 공구");
    }

    @Test
    @DisplayName("[56 A4] 다가오는 일정 오픈 카드 — kind · 공구 상태 · 게시물 상태로 문구를 고른다(승인 완료 SCHEDULED · 운영자 검토 중 PENDING_APPROVAL)")
    void upcomingOpenCardCarriesPostStatus() throws Exception {
        // 진행 중 공구(fixture)는 4일 뒤 종료 — 그 뒤로 오픈 두 건
        LocalDateTime base = groupBuy.getEndAt().plusDays(1).withHour(9).withMinute(0).withSecond(0);
        GroupBuy approved = seed(brand, creator, "여름 수분 세럼 공구", base, base.plusDays(7));
        moveTo(approved.getId(), GroupBuyStatus.READY);
        seedPost(approved.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        GroupBuy reviewing = seed(brand, creator, "수분 토너 대용량 공구", base.plusDays(1), base.plusDays(8));
        seedPost(reviewing.getId(), GroupBuyPostReviewStatus.PENDING, false);

        JsonNode upcoming = json(creatorGet(HOME).andExpect(status().isOk())).get("upcoming");
        assertThat(upcoming).hasSize(3);
        assertThat(upcoming.at("/0/kind").asText()).isEqualTo("GROUP_BUY_END");
        assertThat(upcoming.at("/0/groupBuyStatus").asText()).isEqualTo("IN_PROGRESS");

        JsonNode open = upcoming.get(1);
        assertThat(open.at("/target/id").asLong()).isEqualTo(approved.getId());
        assertThat(open.get("kind").asText()).isEqualTo("GROUP_BUY_OPEN");
        assertThat(open.get("groupBuyStatus").asText()).isEqualTo("READY");
        assertThat(open.get("postStatus").asText()).isEqualTo("SCHEDULED");
        // 기존 필드는 그대로
        assertThat(open.get("title").asText()).isEqualTo("공구 오픈 · 여름 수분 세럼 공구");
        assertThat(open.get("description").asText()).isEqualTo("오픈되면 게시물이 공개되고 판매가 시작돼요");

        JsonNode pending = upcoming.get(2);
        assertThat(pending.at("/target/id").asLong()).isEqualTo(reviewing.getId());
        assertThat(pending.get("kind").asText()).isEqualTo("GROUP_BUY_OPEN");
        assertThat(pending.get("groupBuyStatus").asText()).isEqualTo("PREPARING");
        assertThat(pending.get("postStatus").asText()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    @DisplayName("[56 A4-02] 다가오는 일정 종료 카드 kind GROUP_BUY_END — 같은 날이면 오픈 시각이 더 이르고 제목순(「공구 오픈」 < 「공구 종료」)이 "
            + "반대여도 종료가 먼저(kind 로 정렬)")
    void upcomingEndCardSortsFirstByKind() throws Exception {
        // 진행 중 공구(fixture)를 4일 뒤 23:00 종료로 — 같은 날 00:30 에 오픈하는 공구가 시각 · 제목으로는 앞선다
        LocalDateTime endAt = groupBuy.getEndAt().withHour(23).withMinute(0).withSecond(0).withNano(0);
        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?", endAt, groupBuy.getId());
        LocalDateTime openAt = endAt.withHour(0).withMinute(30);
        GroupBuy opening = seed(brand, creator, "같은 날 오픈 공구", openAt, openAt.plusDays(7));
        moveTo(opening.getId(), GroupBuyStatus.READY);

        JsonNode upcoming = json(creatorGet(HOME).andExpect(status().isOk())).get("upcoming");
        assertThat(upcoming).hasSize(3);

        JsonNode end = upcoming.get(0);
        assertThat(end.get("kind").asText()).isEqualTo("GROUP_BUY_END");
        assertThat(end.at("/target/id").asLong()).isEqualTo(groupBuy.getId());
        assertThat(end.get("date").asText()).isEqualTo(endAt.toLocalDate().toString());
        assertThat(end.get("groupBuyStatus").asText()).isEqualTo("IN_PROGRESS");
        assertThat(end.get("title").asText()).isEqualTo("공구 종료 · 글로우 크림 앵콜 공구");
        assertThat(end.get("description").asText()).isEqualTo("종료 후 주문이 모두 끝나면 정산이 생성돼요");

        JsonNode open = upcoming.get(1);
        assertThat(open.get("kind").asText()).isEqualTo("GROUP_BUY_OPEN");
        assertThat(open.at("/target/id").asLong()).isEqualTo(opening.getId());
        assertThat(open.get("date").asText()).isEqualTo(end.get("date").asText());

        // 오픈한 공구의 종료도 종료 카드다
        JsonNode openingEnd = upcoming.get(2);
        assertThat(openingEnd.get("kind").asText()).isEqualTo("GROUP_BUY_END");
        assertThat(openingEnd.at("/target/id").asLong()).isEqualTo(opening.getId());
        assertThat(openingEnd.get("groupBuyStatus").asText()).isEqualTo("READY");
    }

    @Test
    @DisplayName("[56 A4-03] 게시물이 없는 오픈 카드 — postStatus 는 공구 관리 · 파트너 홈과 같은 파생값 NOT_WRITTEN(설계서 1017 5-1 "
            + "GroupBuyPostStatus.of) · kind · 공구 상태 · 날짜 · 제목 · 설명 · 대상은 그대로")
    void upcomingOpenCardWithoutPost() throws Exception {
        // 진행 중 공구(fixture)는 4일 뒤 종료 — 그 뒤로 게시물 없는 준비중 공구 하나
        LocalDateTime base = groupBuy.getEndAt().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        GroupBuy noPost = seed(brand, creator, "게시물 없는 공구", base, base.plusDays(7));

        JsonNode upcoming = json(creatorGet(HOME).andExpect(status().isOk())).get("upcoming");
        JsonNode open = upcoming.get(1);
        assertThat(open.at("/target/type").asText()).isEqualTo("GROUP_BUY");
        assertThat(open.at("/target/id").asLong()).isEqualTo(noPost.getId());
        assertThat(open.get("kind").asText()).isEqualTo("GROUP_BUY_OPEN");
        assertThat(open.get("groupBuyStatus").asText()).isEqualTo("PREPARING");
        assertThat(open.get("postStatus").asText()).isEqualTo("NOT_WRITTEN");
        assertThat(open.get("date").asText()).isEqualTo(base.toLocalDate().toString());
        assertThat(open.get("title").asText()).isEqualTo("공구 오픈 · 게시물 없는 공구");
        assertThat(open.get("description").asText()).isEqualTo("오픈되면 게시물이 공개되고 판매가 시작돼요");
    }

    @Test
    @DisplayName("CD-02 세금계산서 반려 — 「다시 입력」 · 반려 안내 · extra.rejected / 제출됨 · 검증됨은 할 일이 아니다")
    void rejectedTaxInvoice() throws Exception {
        Settlement rejected = seedSettlement("반려 공구", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        seedTaxInvoice(rejected, "REJECTED");
        Settlement submitted = seedSettlement("제출 공구", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        seedTaxInvoice(submitted, "SUBMITTED");

        JsonNode todos = json(creatorGet(HOME).andExpect(status().isOk())).get("todos");
        List<JsonNode> tax = new ArrayList<>();
        todos.forEach(t -> {
            if ("TAX_INVOICE_ISSUE".equals(t.get("kind").asText())) {
                tax.add(t);
            }
        });
        assertThat(tax).hasSize(1);
        assertThat(tax.get(0).get("actionLabel").asText()).isEqualTo("다시 입력");
        assertThat(tax.get(0).get("description").asText()).startsWith("승인번호가 반려됐어요");
        assertThat(tax.get(0).at("/extra/rejected").asBoolean()).isTrue();
        assertThat(tax.get(0).at("/target/id").asLong()).isEqualTo(rejected.getId());
    }

    @Test
    @DisplayName("CD-03 체결 보류 중 서명 — 할 일에는 남고 기한 칩은 「보류 중」 · 달력 서명 기한에서는 빠진다")
    void heldSignature() throws Exception {
        Contract held = seedSigningContract("보류 공구", LocalDateTime.now().plusDays(2));
        jdbc.update("UPDATE contract SET sanction_hold_since = ?, sanction_hold_party = 'BRAND' WHERE contract_id = ?",
                LocalDateTime.now(), held.getId());

        JsonNode sign = todo(json(creatorGet(HOME).andExpect(status().isOk())).get("todos"), "CONTRACT_SIGN");
        assertThat(sign.get("dueLabel").asText()).isEqualTo("보류 중");
        assertThat(sign.at("/extra/brandSigned").asBoolean()).isFalse();
        assertThat(sign.get("description").asText()).isEqualTo("메일 · 문자로 받은 링크에서 서명해 주세요");

        JsonNode calendar = json(creatorGet(HOME + "/calendar?month=" + YearMonth.now()).andExpect(status().isOk()));
        calendar.get("deadlines").forEach(d -> assertThat(d.get("targetId").asLong()).isNotEqualTo(held.getId()));
    }

    @Test
    @DisplayName("CD-04 연장 요청 — 공구가 이미 끝났으면(종료 시각 경과) 응답할 일이 아니다")
    void expiredExtensionIsNotTodo() throws Exception {
        seedExtensionRequest(groupBuy);
        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?", LocalDateTime.now().minusMinutes(1),
                groupBuy.getId());

        json(creatorGet(HOME).andExpect(status().isOk())).get("todos")
                .forEach(t -> assertThat(t.get("kind").asText()).isNotEqualTo("EXTENSION_RESPOND"));
    }

    @Test
    @DisplayName("CD-05 정지 중 — 운영자가 숨긴 게시물 수정 · 계약 서명은 막히지 않는다 · 기한 없는 일은 연결 요청 바로 위")
    void suspendedAllowsHiddenFixAndSigning() throws Exception {
        GroupBuyPost hidden = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, true);
        seedSigningContract("서명 공구", LocalDateTime.now().plusDays(3));
        requestConnectionFrom("코스랩");
        jdbc.update("UPDATE creator SET status = 'SUSPENDED' WHERE creator_id = ?", creator.getId());

        JsonNode todos = json(creatorGet(HOME).andExpect(status().isOk())).get("todos");
        List<String> kinds = new ArrayList<>();
        todos.forEach(t -> kinds.add(t.get("kind").asText()));
        assertThat(kinds).containsExactly("CONTRACT_SIGN", "GROUP_BUY_POST_HIDDEN_FIX", "CONNECTION_REQUESTS");
        assertThat(todo(todos, "GROUP_BUY_POST_HIDDEN_FIX").get("blocked").isNull()).isTrue();
        assertThat(todo(todos, "GROUP_BUY_POST_HIDDEN_FIX").at("/target/id").asLong()).isEqualTo(groupBuy.getId());
        assertThat(todo(todos, "CONTRACT_SIGN").get("blocked").isNull()).isTrue();
        assertThat(todo(todos, "CONNECTION_REQUESTS").get("blocked").isNull()).isFalse();
        assertThat(hidden.getPostId()).isNotNull();
    }

    @Test
    @DisplayName("CD-05b 정지 중 연장 요청 응답 — 카드는 막지 않는다(거절 허용) · 수락만 extra.acceptBlocked · 해제되면 사유가 사라진다(55 S9)")
    void suspendedExtensionCardBlocksAcceptOnly() throws Exception {
        seedExtensionRequest(groupBuy);
        jdbc.update("UPDATE creator SET status = 'SUSPENDED' WHERE creator_id = ?", creator.getId());

        JsonNode card = todo(json(creatorGet(HOME).andExpect(status().isOk())).get("todos"), "EXTENSION_RESPOND");
        assertThat(card.get("blocked").isNull()).isTrue();
        assertThat(card.get("actionLabel").asText()).isEqualTo("응답하기");
        assertThat(card.at("/extra/acceptBlocked").asBoolean()).isTrue();
        assertThat(card.at("/extra/acceptBlockedMessage").asText()).isEqualTo("연장 수락은 정지 해제 후 할 수 있어요");

        jdbc.update("UPDATE creator SET status = 'ACTIVE' WHERE creator_id = ?", creator.getId());
        JsonNode released = todo(json(creatorGet(HOME).andExpect(status().isOk())).get("todos"), "EXTENSION_RESPOND");
        assertThat(released.get("blocked").isNull()).isTrue();
        assertThat(released.at("/extra/acceptBlocked").asBoolean()).isFalse();
        assertThat(released.at("/extra").has("acceptBlockedMessage")).isFalse();
    }

    @Test
    @DisplayName("CD-06 가이드 체크리스트 — 계좌 3필드 · 프로필 3필드 · 공개 게시물 1건이면 완료 3 · 검증 중 계좌도 완료")
    void guideChecklist() throws Exception {
        Creator newbie = createCreator("체크쇼룸", "check-room");
        String token = bearerToken(newbie.getUser().getUsername(), RoleType.CREATOR, newbie.getUser().getId());
        jdbc.update("UPDATE creator SET BANK_NAME = '국민', ACCOUNT_NUMBER = '123456789', BANKBOOK_IMAGE_URL = 'https://cdn.test/b.png', "
                + "INTRODUCTION = '안녕하세요', PROFILE_IMAGE_URL = 'https://cdn.test/p.png' WHERE creator_id = ?", newbie.getId());

        JsonNode guide = json(creatorGet(HOME, token).andExpect(status().isOk())).get("guide");
        assertThat(guide.at("/checklist/0/done").asBoolean()).isTrue();
        assertThat(guide.at("/checklist/1/done").asBoolean()).isTrue();
        assertThat(guide.at("/checklist/2/done").asBoolean()).isFalse();
        assertThat(guide.get("completed").asInt()).isEqualTo(2);

        Long postId = transactionTemplate.execute(tx -> postRepository.save(
                Post.draft(creatorRepository.findById(newbie.getId()).orElseThrow(), "첫 게시물", null)).getId());
        jdbc.update("UPDATE post SET status = 'PUBLISHED', published_at = ? WHERE post_id = ?", LocalDateTime.now(), postId);
        jdbc.update("UPDATE creator SET BANK_NAME = NULL, account_verification_status = 'VERIFYING' WHERE creator_id = ?",
                newbie.getId());
        guide = json(creatorGet(HOME, token).andExpect(status().isOk())).get("guide");
        assertThat(guide.at("/checklist/0/done").asBoolean()).isTrue();
        assertThat(guide.at("/checklist/2/done").asBoolean()).isTrue();
        assertThat(guide.get("completed").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("CD-07 연결 요청 카드 — 2곳 이하면 「외 N곳」 없음 · 거절 · 연결된 요청은 세지 않는다")
    void connectionRequestCard() throws Exception {
        requestConnectionFrom("코스랩");
        Connection rejected = requestConnectionFrom("거절랩");
        jdbc.update("UPDATE connection SET status = 'REJECTED' WHERE connection_id = ?", rejected.getId());

        JsonNode card = todo(json(creatorGet(HOME).andExpect(status().isOk())).get("todos"), "CONNECTION_REQUESTS");
        assertThat(card.get("title").asText()).isEqualTo("연결 요청 · 코스랩");
        assertThat(card.at("/extra/count").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("CD-08 실시간 방문 — 공구 시작 전 방문은 누적에서 빠지고 · 오늘 방문만 「오늘」")
    void liveVisitsBoundaries() throws Exception {
        seedVisit("before-start", groupBuy.getStartAt().minusHours(1));
        seedVisit("during", groupBuy.getStartAt().plusHours(1));
        seedVisit("today", LocalDateTime.now().minusMinutes(1));

        JsonNode live = json(creatorGet(HOME + "/live").andExpect(status().isOk()));
        assertThat(live.get("visits").asLong()).isEqualTo(2);
        assertThat(live.get("visitsToday").asLong()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 픽스처

    private static JsonNode todo(JsonNode todos, String kind) {
        for (JsonNode t : todos) {
            if (kind.equals(t.get("kind").asText())) {
                return t;
            }
        }
        throw new AssertionError("no todo " + kind);
    }

    private org.springframework.test.web.servlet.ResultActions creatorGet(String url, String token) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, token));
    }

    private Contract seedSigningContract(String title, LocalDateTime signatureDeadlineAt) {
        return transactionTemplate.execute(tx -> {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            LocalDateTime startAt = now.plusDays(20);
            Contract contract = Contract.createDraft(brand.market(), creator, null);
            contract.updateTerms(title, startAt, startAt.plusDays(7), 300_000, FixedFeeTrigger.POST_REGISTERED, now,
                    1, 1, 3, startAt.toLocalDate().plusDays(10), true, SecondaryUsePeriodType.FIXED, 12, false, null);
            List<ContractItem> items = new ArrayList<>();
            items.add(item(cream, 27_200, "12.0", 300));
            contract.replaceItems(items);
            contract.applyReviewRequested("CTR-CD-%03d".formatted(++contractSeq), null, null, now.minusDays(2));
            contract.approveReview(now.minusDays(1), signatureDeadlineAt, now.minusDays(1));
            return contractRepository.saveAndFlush(contract);
        });
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

    private Connection requestConnectionFrom(String brandName) throws InterruptedException {
        BrandFixture.Brand other = fixture.createBrand(Math.abs(brandName.hashCode()) + "@showroomz.test", brandName);
        Connection saved = connectionRepository.save(Connection.requestPair(other.market(), creator));
        Thread.sleep(5);
        return saved;
    }

    private void seedVisit(String visitorKey, LocalDateTime at) {
        jdbc.update("INSERT INTO showroom_visit (creator_id, visitor_key, source, visited_at) VALUES (?, ?, 'APP_SEARCH', ?)",
                creator.getId(), visitorKey, at);
    }
}
