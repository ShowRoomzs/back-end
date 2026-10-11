package showroomz.api.app.post;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.global.utils.BusinessCalendar;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공구 관리(브랜드 §30 · 스튜디오 §31 · 어드민 §32) → 소비자 공구 게시물(공구 게시물 설계서)을 <b>한 공구로 잇는</b> 시나리오.
 *
 * <p>역할별 테스트는 상대 서피스가 만드는 사실(게시물 · 요청 · 통지)을 SQL로 적재하고, 소비자 테스트는 {@code seedPost}로 만든
 * 게시물을 투영한다. 그래서 「스튜디오가 쓰고 어드민이 심사한 게시물이 그대로 앱에 나가는가」 · 「각 판정이 앱 노출을 제때 바꾸는가」는
 * 어디에서도 검증되지 않는다. 여기서는 <b>공구 생성(체결 계약 → {@code GroupBuyFactory})만 적재</b>하고 나머지 쓰기는 전부 각 역할의
 * 실제 API로 한다.
 *
 * <p>예외는 시간뿐이다 — 오픈·종료는 스케줄러가 부르는 {@code lifecycleService}를 직접 부르고, 시작·종료 시각만 SQL로 당긴다.
 */
@DisplayName("[통합] 공구 전 과정 — 공구 관리(브랜드·쇼룸·어드민)부터 앱 공구 게시물까지")
class GroupBuyEndToEndIntegrationTest extends GroupBuyPostTestSupport {

    private static final String STUDIO = "/v1/creator/group-buys";
    private static final String ADMIN = "/v1/admin/group-buys";
    private static final String TITLE = "글로우 크림 앵콜 공구 오픈";
    private static final String CONTENT = "크림과 세럼을 두 달 동안 써 보고 고른 조합입니다.";

    @Autowired private BusinessCalendar businessCalendar;

    private String creatorToken;
    private String operatorToken;

    @BeforeEach
    void setUpActors() {
        creatorToken = bearerToken(creator.getUser().getUsername(), RoleType.CREATOR, creator.getUser().getId());
        operatorToken = adminToken(fixture.createAdmin("e2e-operator@showroomz.test", "김운영"));
    }

    // ------------------------------------------------------------------ 오픈까지

    @Test
    @DisplayName("오픈까지 — 임시저장·반려·재등록·승인 어느 단계에서도 앱에 나가지 않고, 오픈 시각에 마지막 제출본이 공구 카드로 나간다")
    void preparationToOpen() throws Exception {
        long id = seedPreparing().getId();

        studioSend(put(STUDIO + "/" + id + "/post/draft"), Map.of("title", "초안"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.post.status").value("WRITING"));
        Long postId = groupBuyPostRepository.findByGroupBuyId(id).orElseThrow().getPostId();
        assertInvisible(postId);

        brand(id, "stock-confirmation", null).andExpect(status().isOk());
        studioSend(post(STUDIO + "/" + id + "/post/submission"),
                Map.of("title", TITLE, "content", "최저가 보장! 바르면 바로 주름이 사라집니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.post.status").value("PENDING_APPROVAL"));
        adminSummary().andExpect(jsonPath("$.queues.OPEN_REVIEW").value(1));
        assertInvisible(postId);

        admin(id, "open-review/reject", Map.of("reasonCode", "AD_SUPERLATIVE", "detail", "「최저가 보장」 문구를 빼 주세요."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PREPARING"));
        studioView(id).andExpect(jsonPath("$.post.status").value("REJECTED"))
                .andExpect(jsonPath("$.post.rejection.detail").value("「최저가 보장」 문구를 빼 주세요."))
                .andExpect(jsonPath("$.permissions.canWritePost").value(true));
        adminSummary().andExpect(jsonPath("$.queues.OPEN_REVIEW").value(0));
        assertInvisible(postId);

        studioSend(post(STUDIO + "/" + id + "/post/submission"), Map.of("title", TITLE, "content", CONTENT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.post.status").value("PENDING_APPROVAL"));
        adminGet(id, "post/revisions").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].content").value(CONTENT));
        admin(id, "open-review/approve", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));

        // 준비완료 — 게시물은 예약이고 아직 소비자에게 없다. 좋아요도 받지 않는다.
        brandView(id).andExpect(jsonPath("$.groupBuy.status").value("READY"))
                .andExpect(jsonPath("$.post.status").value("SCHEDULED"));
        assertInvisible(postId);
        mockMvc.perform(post(SHOWROOMS + "posts/" + postId + "/wishlist").header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(status().isNotFound());

        openAtStart(id);

        brandView(id).andExpect(jsonPath("$.groupBuy.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.post.status").value("EXPOSED"));
        studioView(id).andExpect(jsonPath("$.post.status").value("EXPOSED"));
        assertThat(productStatus(cream)).isEqualTo(ProductGroupBuyStatus.IN_PROGRESS);

        pinned(creator).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contentType").value("GROUP_BUY"))
                .andExpect(jsonPath("$[0].post.postId").value(postId))
                .andExpect(jsonPath("$[0].post.content").value(CONTENT))
                .andExpect(jsonPath("$[0].post.hasOngoingGroupBuy").value(true))
                .andExpect(jsonPath("$[0].post.groupBuy.groupBuyId").value(id))
                .andExpect(jsonPath("$[0].post.groupBuy.title").value(TITLE))
                .andExpect(jsonPath("$[0].post.groupBuy.saleState").value("PARTIALLY_SOLD_OUT"))
                .andExpect(jsonPath("$[0].post.groupBuy.adDisclosure.text").value(startsWith("유료 광고 포함 · 글로우랩")))
                .andExpect(jsonPath("$[0].post.groupBuy.productCount").value(2))
                .andExpect(jsonPath("$[0].post.groupBuy.products[0].name").value("글로우 크림 50ml"))
                .andExpect(jsonPath("$[0].post.groupBuy.products[0].regularPrice").value(34_000))
                .andExpect(jsonPath("$[0].post.groupBuy.products[0].groupBuyPrice").value(27_200))
                .andExpect(jsonPath("$[0].post.groupBuy.products[0].state").value("ON_SALE"))
                .andExpect(jsonPath("$[0].post.groupBuy.products[1].state").value("SOLD_OUT"));
        consumerPost(postId).andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("GROUP_BUY"))
                .andExpect(jsonPath("$.content").value(CONTENT))
                .andExpect(jsonPath("$.groupBuy.title").value(TITLE))
                .andExpect(jsonPath("$.groupBuy.products.length()").value(2))
                .andExpect(jsonPath("$.likeLocked").value(false));
        profile(creator).andExpect(jsonPath("$.hasOngoingGroupBuy").value(true))
                .andExpect(jsonPath("$.postCount").value(1));
        lowerFeed(creator).andExpect(jsonPath("$.content[*].post.postId").value(not(hasItem(postId.intValue()))));
        mockMvc.perform(get("/v1/user/feed/recommended"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].post.postId").value(hasItem(postId.intValue())));
    }

    @Test
    @DisplayName("오픈 전 중단 — 준비완료에서 브랜드 중단 요청이 승인되면 게시물은 소비자에게 한 번도 나가지 않는다")
    void suspendedBeforeOpenNeverReachesConsumer() throws Exception {
        long id = seedPreparing().getId();
        studioSend(post(STUDIO + "/" + id + "/post/submission"), Map.of("title", TITLE, "content", CONTENT))
                .andExpect(status().isOk());
        brand(id, "stock-confirmation", null).andExpect(status().isOk());
        admin(id, "open-review/approve", null).andExpect(jsonPath("$.status").value("READY"));
        Long postId = groupBuyPostRepository.findByGroupBuyId(id).orElseThrow().getPostId();

        brand(id, "suspension-request", Map.of("reasonCode", "NEGOTIATION_BROKEN", "memo", "상품 공급이 어렵습니다."))
                .andExpect(status().isOk());
        admin(id, "change-requests/" + latestRequestId(id) + "/approve", Map.of("decisionReason", "공급 중단을 확인했습니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));

        studioView(id).andExpect(jsonPath("$.post.status").value("CLOSED"));
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE group_buy SET start_at = ? WHERE group_buy_id = ?", Timestamp.valueOf(now.minusMinutes(1)), id);
        assertThat(lifecycleService.open(id, now)).isFalse();

        assertInvisible(postId);
        lowerFeed(creator).andExpect(jsonPath("$.content").isEmpty());
        assertThat(postRepository.findById(postId).orElseThrow().getPublishedAt()).isNull();
        assertThat(productStatus(cream)).isEqualTo(ProductGroupBuyStatus.NOT_CONNECTED);
    }

    // ------------------------------------------------------------------ 진행 중

    @Test
    @DisplayName("진행 중 — 쇼룸 수정은 앱에 즉시 반영 · 어드민 숨김 동안은 앱에서 사라지고 판매는 계속 · 해제하면 숨김 중 수정본으로 돌아온다")
    void editHideAndUnhideWhileSelling() throws Exception {
        Opened opened = openThroughApis();
        long id = opened.groupBuyId();
        Long postId = opened.postId();
        follow(viewer, creator);
        like(consumer, postId);
        following().andExpect(jsonPath("$.content[*].post.postId").value(hasItem(postId.intValue())));
        liked(consumer).andExpect(jsonPath("$.content[*].post.postId").value(hasItem(postId.intValue())));

        studioSend(patch(STUDIO + "/" + id + "/post"), Map.of("title", "앵콜 공구 — 세트 구성 안내", "content", "세트 구성을 바꿨습니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.post.status").value("EXPOSED"));
        consumerPost(postId).andExpect(jsonPath("$.groupBuy.title").value("앵콜 공구 — 세트 구성 안내"))
                .andExpect(jsonPath("$.content").value("세트 구성을 바꿨습니다."));

        admin(id, "post/hide", Map.of("reasonCode", "AD_MEDICAL_CLAIM", "detail", "「피부 재생」은 의료적 효능 표현입니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        assertInvisible(postId);
        following().andExpect(jsonPath("$.content[*].post.postId").value(not(hasItem(postId.intValue()))));
        liked(consumer).andExpect(jsonPath("$.content[*].post.postId").value(not(hasItem(postId.intValue()))));
        brandView(id).andExpect(jsonPath("$.groupBuy.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.post.status").value("HIDDEN"));
        assertThat(productStatus(cream)).isEqualTo(ProductGroupBuyStatus.IN_PROGRESS);

        // 숨김 중 수정은 해제가 아니다 — 운영자가 읽고 풀어야 나간다.
        studioSend(patch(STUDIO + "/" + id + "/post"), Map.of("title", "앵콜 공구 — 정정본", "content", "효능 표현을 지웠습니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.post.status").value("HIDDEN"));
        assertInvisible(postId);

        int latest = objectMapper.readTree(adminView(id).andReturn().getResponse().getContentAsString())
                .at("/post/latestRevisionNo").asInt();
        admin(id, "post/unhide", Map.of("expectedRevisionNo", latest)).andExpect(status().isOk());

        consumerPost(postId).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.title").value("앵콜 공구 — 정정본"))
                .andExpect(jsonPath("$.content").value("효능 표현을 지웠습니다."))
                .andExpect(jsonPath("$.isLiked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
        pinned(creator).andExpect(jsonPath("$[0].post.postId").value(postId));
        liked(consumer).andExpect(jsonPath("$.content[*].post.postId").value(hasItem(postId.intValue())));
    }

    @Test
    @DisplayName("진행 중 — 브랜드 연장 요청은 쇼룸이 수락해야 앱 D-day·종료일이 늘고, 전 상품 품절이어도 좋아요는 열려 있다")
    void extensionAndSoldOutReachConsumer() throws Exception {
        Opened opened = openThroughApis();
        long id = opened.groupBuyId();
        Long postId = opened.postId();
        JsonNode before = consumerPostJson(postId);
        int dDay = before.at("/groupBuy/dDay").asInt();

        brand(id, "extension-request", Map.of("extensionDays", 3, "reason", "재입고 물량이 들어왔습니다."))
                .andExpect(status().isOk());
        consumerPost(postId).andExpect(jsonPath("$.groupBuy.dDay").value(dDay));

        studioSend(post(STUDIO + "/" + id + "/extension/acceptance"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.extension.status").value("ACCEPTED"));
        JsonNode after = consumerPostJson(postId);
        assertThat(after.at("/groupBuy/dDay").asInt()).isEqualTo(dDay + 3);
        assertThat(LocalDateTime.parse(after.at("/groupBuy/endAt").asText()))
                .isEqualTo(LocalDateTime.parse(before.at("/groupBuy/endAt").asText()).plusDays(3));

        setStock(cream, 0);
        consumerPost(postId).andExpect(jsonPath("$.groupBuy.saleState").value("SOLD_OUT"))
                .andExpect(jsonPath("$.groupBuy.products[0].state").value("SOLD_OUT"))
                .andExpect(jsonPath("$.likeLocked").value(false));
        like(consumer, postId);
        pinned(creator).andExpect(jsonPath("$[0].post.groupBuy.saleState").value("SOLD_OUT"));
    }

    // ------------------------------------------------------------------ 종결

    @Test
    @DisplayName("기간 종료 — 앱은 마감 게시물로 3일 보여주고(새 좋아요 잠김·해제 허용) 72시간이 되면 내린다")
    void periodEndThenRetention() throws Exception {
        Opened opened = openThroughApis();
        long id = opened.groupBuyId();
        Long postId = opened.postId();
        like(consumer, postId);
        Users latecomer = createUser("viewer-noah", "노아");

        LocalDateTime endedAt = endByPeriod(id);

        brandView(id).andExpect(jsonPath("$.groupBuy.status").value("ENDED"))
                // 계약 이행 확인은 2026-10-06 폐기됐다 — 버튼이 없다(1009 기획 수정본 6절).
                .andExpect(jsonPath("$.permissions.canCheckFulfillment").value(false));
        studioView(id).andExpect(jsonPath("$.post.status").value("CLOSED"));
        assertThat(productStatus(cream)).isEqualTo(ProductGroupBuyStatus.NOT_CONNECTED);

        pinned(creator).andExpect(jsonPath("$").isEmpty());
        profile(creator).andExpect(jsonPath("$.hasOngoingGroupBuy").value(false))
                .andExpect(jsonPath("$.postCount").value(1));
        lowerFeed(creator).andExpect(jsonPath("$.content[0].post.postId").value(postId))
                .andExpect(jsonPath("$.content[0].post.groupBuy.saleState").value("CLOSED"))
                .andExpect(jsonPath("$.content[0].post.groupBuy.products").isEmpty());
        consumerPost(postId).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.saleState").value("CLOSED"))
                .andExpect(jsonPath("$.groupBuy.dDay").doesNotExist())
                .andExpect(jsonPath("$.groupBuy.products[0].state").value("CLOSED"))
                .andExpect(jsonPath("$.likeLocked").value(true));
        liked(consumer).andExpect(jsonPath("$.content[0].post.postId").value(postId))
                .andExpect(jsonPath("$.content[0].post.groupBuy.saleState").value("CLOSED"));
        mockMvc.perform(get("/v1/user/feed/recommended"))
                .andExpect(jsonPath("$.content[*].post.postId").value(not(hasItem(postId.intValue()))));
        mockMvc.perform(post(SHOWROOMS + "posts/" + postId + "/wishlist").header(HttpHeaders.AUTHORIZATION, tokenOf(latecomer)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(SHOWROOMS + "posts/" + postId + "/wishlist").header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(status().isNoContent());

        // 폐기된 이행 확인 API 는 409 로 닫혀 있다 — 게시물 노출과 무관하다.
        brand(id, "fulfillment-check", Map.of("result", "FULFILLED")).andExpect(status().isConflict());
        studioSend(post(STUDIO + "/" + id + "/fulfillment-check"), Map.of("result", "FULFILLED"))
                .andExpect(status().isConflict());
        consumerPost(postId).andExpect(status().isOk());

        assertThat(lifecycleService.retirePost(id, endedAt.plusHours(72).minusSeconds(1))).isZero();
        assertThat(lifecycleService.retirePost(id, endedAt.plusHours(72))).isEqualTo(1);
        assertInvisible(postId);
        lowerFeed(creator).andExpect(jsonPath("$.content").isEmpty());
        profile(creator).andExpect(jsonPath("$.postCount").value(0));
        studioView(id).andExpect(jsonPath("$.post.status").value("CLOSED"));
    }

    @Test
    @DisplayName("조기 마감 — 브랜드 요청 중에는 판매·노출이 이어지고, 운영자 승인 시각부터 마감 게시물 3일이 흐른다")
    void earlyCloseApprovedByOperator() throws Exception {
        Opened opened = openThroughApis();
        long id = opened.groupBuyId();
        Long postId = opened.postId();

        brand(id, "early-close-request", Map.of("reasonCode", "STOCK_OUT", "memo", "준비 물량이 모두 나갔습니다."))
                .andExpect(status().isOk());
        studioView(id).andExpect(jsonPath("$.activeRequest.type").value("EARLY_CLOSE"));
        consumerPost(postId).andExpect(jsonPath("$.groupBuy.saleState").value("PARTIALLY_SOLD_OUT"));
        pinned(creator).andExpect(jsonPath("$.length()").value(1));

        admin(id, "change-requests/" + latestRequestId(id) + "/approve", Map.of("decisionReason", "재고 소진을 확인했습니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENDED"));

        GroupBuy closed = reload(id);
        assertThat(closed.getCloseType()).isEqualTo(GroupBuyCloseType.EARLY_CLOSED);
        assertThat(closed.getEndedAt()).isBefore(closed.getEndAt());
        pinned(creator).andExpect(jsonPath("$").isEmpty());
        consumerPost(postId).andExpect(status().isOk()).andExpect(jsonPath("$.groupBuy.saleState").value("CLOSED"));
        lowerFeed(creator).andExpect(jsonPath("$.content[0].post.postId").value(postId));

        assertThat(lifecycleService.retirePost(id, closed.getEndedAt().plusHours(72).minusSeconds(1))).isZero();
        assertThat(lifecycleService.retirePost(id, closed.getEndedAt().plusHours(72))).isEqualTo(1);
        assertInvisible(postId);
    }

    @Test
    @DisplayName("중단 요청 — 브랜드 요청이 반려되는 동안 노출은 그대로이고, 쇼룸 요청이 승인되면 마감 기간 없이 즉시 앱에서 내려간다")
    void suspensionRequestsDecidedByOperator() throws Exception {
        Opened opened = openThroughApis();
        long id = opened.groupBuyId();
        Long postId = opened.postId();
        like(consumer, postId);

        brand(id, "suspension-request", Map.of("reasonCode", "QUALITY_ISSUE", "memo", "일부 로트 품질 점검 중입니다."))
                .andExpect(status().isOk());
        consumerPost(postId).andExpect(status().isOk());
        admin(id, "change-requests/" + latestRequestId(id) + "/reject", Map.of("decisionReason", "점검 결과 이상이 없습니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        consumerPost(postId).andExpect(status().isOk()).andExpect(jsonPath("$.likeLocked").value(false));
        pinned(creator).andExpect(jsonPath("$.length()").value(1));

        studioSend(post(STUDIO + "/" + id + "/suspension-request"),
                Map.of("reasonCode", "DELIVERY_FAILURE", "memo", "발송되지 않은 주문이 쌓이고 있습니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.groupBuy.status").value("IN_PROGRESS"));
        consumerPost(postId).andExpect(status().isOk());
        admin(id, "change-requests/" + latestRequestId(id) + "/approve", Map.of("decisionReason", "배송 지연을 확인했습니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));

        assertInvisible(postId);
        lowerFeed(creator).andExpect(jsonPath("$.content").isEmpty());
        liked(consumer).andExpect(jsonPath("$.content[*].post.postId").value(not(hasItem(postId.intValue()))));
        studioView(id).andExpect(jsonPath("$.post.status").value("CLOSED"))
                .andExpect(jsonPath("$.closure.decisionReason").value("배송 지연을 확인했습니다."));
        brandView(id).andExpect(jsonPath("$.groupBuy.status").value("SUSPENDED"));
        assertThat(productStatus(cream)).isEqualTo(ProductGroupBuyStatus.NOT_CONNECTED);
        // 이미 내려가 있으므로 스케줄러가 더 할 일이 없다. 좋아요 기록은 인사이트용으로 남는다.
        assertThat(lifecycleService.retirePost(id, LocalDateTime.now().plusDays(4))).isZero();
        assertThat(postRepository.findById(postId).orElseThrow().getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("직권 중단 — 통지·소명 기간에도 앱 판매는 이어지고(쇼룸 수정만 잠김) 철회로 되돌아가며, 긴급 중단은 즉시 앱에서 내린다")
    void adminSuspensionNoticeThenEmergency() throws Exception {
        Opened opened = openThroughApis();
        long id = opened.groupBuyId();
        Long postId = opened.postId();
        LocalDateTime appealDeadline = businessCalendar.addBusinessDays(LocalDate.now(), 3).atTime(23, 59, 59);
        LocalDateTime executeAt = businessCalendar.addBusinessDays(appealDeadline.toLocalDate(), 1).atTime(10, 0);

        admin(id, "admin-suspension/notice", Map.of("clause", "ART17_1_LAW",
                "executeScheduledAt", executeAt.toString(), "appealDeadlineAt", appealDeadline.toString(),
                "noticeBody", "상품 표시 문구의 근거 자료를 제출해 주세요."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENSION_SCHEDULED"));
        consumerPost(postId).andExpect(status().isOk()).andExpect(jsonPath("$.likeLocked").value(false));
        pinned(creator).andExpect(jsonPath("$.length()").value(1));
        profile(creator).andExpect(jsonPath("$.hasOngoingGroupBuy").value(true));
        studioSend(patch(STUDIO + "/" + id + "/post"), Map.of("title", "통지 중 수정", "content", "막혀야 한다"))
                .andExpect(status().isConflict());

        brand(id, "appeal", Map.of("content", "시험성적서에 근거한 표현이며 해당 문구를 정정했습니다."))
                .andExpect(status().isOk());
        admin(id, "admin-suspension/withdraw", Map.of("reasonCode", "RECTIFIED", "detail", "정정된 표시를 확인했습니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        studioSend(patch(STUDIO + "/" + id + "/post"), Map.of("title", "정정 완료 공구", "content", "근거 자료를 함께 안내합니다."))
                .andExpect(status().isOk());
        consumerPost(postId).andExpect(jsonPath("$.groupBuy.title").value("정정 완료 공구"));

        admin(id, "admin-suspension/emergency", Map.of("emergencyReason", "CONSUMER_HARM", "body", "피부 이상 반응 신고가 급증했습니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));

        assertThat(reload(id).getCloseType()).isEqualTo(GroupBuyCloseType.SUSPENDED);
        assertInvisible(postId);
        profile(creator).andExpect(jsonPath("$.hasOngoingGroupBuy").value(false));
        studioView(id).andExpect(jsonPath("$.post.status").value("CLOSED"));
    }

    // ------------------------------------------------------------------ 흐름

    private record Opened(long groupBuyId, Long postId) {
    }

    /** 게이트 세 개(재고 확인 · 게시물 제출 · 오픈 승인)를 모두 API로 채우고 시작 시각에 연다. */
    private Opened openThroughApis() throws Exception {
        long id = seedPreparing().getId();
        studioSend(post(STUDIO + "/" + id + "/post/submission"), Map.of("title", TITLE, "content", CONTENT))
                .andExpect(status().isOk());
        brand(id, "stock-confirmation", null).andExpect(status().isOk());
        admin(id, "open-review/approve", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        openAtStart(id);
        return new Opened(id, groupBuyPostRepository.findByGroupBuyId(id).orElseThrow().getPostId());
    }

    /** 시작 시각을 1분 전으로 당기고 스케줄러의 오픈 단계를 부른다. 기간은 18일로 연장 상한(30일) 안이다. */
    private void openAtStart(long id) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE group_buy SET start_at = ? WHERE group_buy_id = ?", Timestamp.valueOf(now.minusMinutes(1)), id);
        assertThat(lifecycleService.open(id, now)).isTrue();
        assertThat(reload(id).getStatus()).isEqualTo(GroupBuyStatus.IN_PROGRESS);
    }

    /** 종료 시각을 1초 전으로 당기고 스케줄러의 종료 단계를 부른다. 실제 종료 시각을 돌려준다. */
    private LocalDateTime endByPeriod(long id) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?", Timestamp.valueOf(now.minusSeconds(1)), id);
        assertThat(lifecycleService.end(id, now)).isTrue();
        return reload(id).getEndedAt();
    }

    private Long latestRequestId(long id) {
        return changeRequestRepository.findByGroupBuyIdOrderByRequestedAtDescIdDesc(id).get(0).getId();
    }

    /** 소비자 쪽 네 경로(상세 · 고정 섹션 · 링 · 추천 피드) 어디에도 없다. */
    private void assertInvisible(Long postId) throws Exception {
        assertThat(postRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(PostStatus.DRAFT);
        consumerPost(postId).andExpect(status().isNotFound());
        pinned(creator).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        profile(creator).andExpect(jsonPath("$.hasOngoingGroupBuy").value(false));
        mockMvc.perform(get("/v1/user/feed/recommended"))
                .andExpect(jsonPath("$.content[*].post.postId").value(not(hasItem(postId.intValue()))));
    }

    // ------------------------------------------------------------------ 역할별 요청

    private ResultActions brand(long id, String path, Object body) throws Exception {
        return action(id, path, body);
    }

    private ResultActions brandView(long id) throws Exception {
        return mockMvc.perform(get(GROUP_BUYS + "/" + id).header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    private ResultActions studioView(long id) throws Exception {
        return mockMvc.perform(get(STUDIO + "/" + id).header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    private ResultActions studioSend(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mockMvc.perform(withBody(request.header(HttpHeaders.AUTHORIZATION, creatorToken), body));
    }

    private ResultActions admin(long id, String path, Object body) throws Exception {
        return mockMvc.perform(withBody(post(ADMIN + "/" + id + "/" + path)
                .header(HttpHeaders.AUTHORIZATION, operatorToken), body));
    }

    private ResultActions adminView(long id) throws Exception {
        return mockMvc.perform(get(ADMIN + "/" + id).header(HttpHeaders.AUTHORIZATION, operatorToken));
    }

    private ResultActions adminGet(long id, String path) throws Exception {
        return mockMvc.perform(get(ADMIN + "/" + id + "/" + path).header(HttpHeaders.AUTHORIZATION, operatorToken));
    }

    private ResultActions adminSummary() throws Exception {
        return mockMvc.perform(get(ADMIN + "/summary").header(HttpHeaders.AUTHORIZATION, operatorToken));
    }

    private ResultActions consumerPost(Long postId) throws Exception {
        return mockMvc.perform(get(SHOWROOMS + "posts/" + postId).header(HttpHeaders.AUTHORIZATION, consumer));
    }

    private JsonNode consumerPostJson(Long postId) throws Exception {
        return objectMapper.readTree(consumerPost(postId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions following() throws Exception {
        return mockMvc.perform(get("/v1/user/feed/following").header(HttpHeaders.AUTHORIZATION, consumer));
    }

    private ResultActions liked(String token) throws Exception {
        return mockMvc.perform(get("/v1/user/wishlist/contents").header(HttpHeaders.AUTHORIZATION, token));
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, Object body) {
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
    }
}
