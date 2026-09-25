package showroomz.api.creator.groupbuy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.post.type.PostStatus;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §31 화면 분기를 실제 브랜드·스튜디오·운영자 API와 수명주기 전이로 확인한다. */
@DisplayName("[통합] 쇼룸 공구 관리 기능 시나리오")
class CreatorGroupBuyScenarioIntegrationTest extends CreatorGroupBuyTestSupport {

    private static final Map<String, String> POST = Map.of("title", "글로우 크림 공구", "content", "크림과 세럼을 소개합니다.");

    @Test
    @DisplayName("B1→B2→B4→B5→B7: 제출·재고 확인·운영자 승인·자동 시작·종료가 한 공구에 이어진다")
    void fullLifecycleAcrossActors() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        long id = groupBuy.getId();
        Seller admin = fixture.createAdmin("studio-flow-admin@showroomz.test", "운영자");

        studioDetail(id).andExpect(jsonPath("$.groupBuy.status").value("PREPARING"))
                .andExpect(jsonPath("$.post.status").value("NOT_WRITTEN"))
                .andExpect(jsonPath("$.readiness.gates[*].state").value(contains("WAITING", "MY_TURN", "WAITING")));

        submitPost(id, POST).andExpect(status().isOk())
                .andExpect(jsonPath("$.post.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.readiness.gates[*].state").value(contains("WAITING", "DONE", "IN_REVIEW")));
        action(id, "stock-confirmation", null).andExpect(status().isOk());
        assertThat(reload(id).getStatus()).isEqualTo(GroupBuyStatus.PREPARING);

        mockMvc.perform(post("/v1/admin/group-buys/" + id + "/open-review/approve")
                        .header(HttpHeaders.AUTHORIZATION, adminToken(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        studioDetail(id).andExpect(jsonPath("$.post.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.readiness.gates[*].state").value(contains("DONE", "DONE", "DONE")))
                .andExpect(jsonPath("$.permissions.canEditPost").value(true));

        GroupBuyPost post = groupBuyPostRepository.findByGroupBuyId(id).orElseThrow();
        assertThat(post.getPost().getStatus()).isEqualTo(PostStatus.DRAFT);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE group_buy SET start_at = ? WHERE group_buy_id = ?", Timestamp.valueOf(now.minusMinutes(1)), id);
        assertThat(lifecycleService.open(id, now)).isTrue();
        assertThat(lifecycleService.open(id, now)).isFalse();
        studioDetail(id).andExpect(jsonPath("$.groupBuy.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.post.status").value("EXPOSED"))
                .andExpect(jsonPath("$.permissions.canWritePost").value(false));
        assertThat(postRepository.findById(post.getPostId()).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);

        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?", Timestamp.valueOf(now.minusSeconds(1)), id);
        assertThat(lifecycleService.end(id, now)).isTrue();
        assertThat(lifecycleService.end(id, now)).isFalse();
        studioDetail(id).andExpect(jsonPath("$.groupBuy.status").value("ENDED"))
                .andExpect(jsonPath("$.post.status").value("CLOSED"))
                .andExpect(jsonPath("$.payout").doesNotExist())
                .andExpect(jsonPath("$.permissions.canCheckFulfillment").value(true));
        assertThat(postRepository.findById(post.getPostId()).orElseThrow().getStatus()).isEqualTo(PostStatus.DRAFT);
    }

    @Test
    @DisplayName("A1·A2: 빈 상태와 6개 탭의 합계는 같은 소유자 공구만 센다")
    void emptyAndSixTabs() throws Exception {
        studioList(null).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        studioSummary().andExpect(jsonPath("$.tabCounts.ALL").value(0))
                .andExpect(jsonPath("$.actionRequiredCount").value(0));

        GroupBuy preparing = seedIn(GroupBuyStatus.PREPARING);
        GroupBuy ready = seedIn(GroupBuyStatus.READY);
        GroupBuy selling = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuy notice = seedIn(GroupBuyStatus.SUSPENSION_SCHEDULED);
        GroupBuy ended = seedIn(GroupBuyStatus.ENDED);
        GroupBuy settled = seedIn(GroupBuyStatus.SETTLED);
        GroupBuy suspended = seedIn(GroupBuyStatus.SUSPENDED);

        studioSummary().andExpect(jsonPath("$.tabCounts.ALL").value(7))
                .andExpect(jsonPath("$.tabCounts.PREPARING").value(1))
                .andExpect(jsonPath("$.tabCounts.READY").value(1))
                .andExpect(jsonPath("$.tabCounts.IN_PROGRESS").value(2))
                .andExpect(jsonPath("$.tabCounts.ENDED").value(2))
                .andExpect(jsonPath("$.tabCounts.SUSPENDED").value(1));
        studioList("tab=IN_PROGRESS").andExpect(jsonPath("$.content[*].groupBuyId")
                .value(containsInAnyOrder(selling.getId().intValue(), notice.getId().intValue())));
        studioList("tab=ENDED").andExpect(jsonPath("$.content[*].groupBuyId")
                .value(containsInAnyOrder(ended.getId().intValue(), settled.getId().intValue())));
        studioList("tab=SUSPENDED").andExpect(jsonPath("$.content[0].groupBuyId").value(suspended.getId()));
        studioList("tab=PREPARING").andExpect(jsonPath("$.content[0].groupBuyId").value(preparing.getId()));
        studioList("tab=READY").andExpect(jsonPath("$.content[0].groupBuyId").value(ready.getId()));
    }

    @Test
    @DisplayName("B1·B2·B3: 게시물 심사 상태가 준비 게이트와 내 조치 건수를 바꾼다")
    void preparingVariantsAndActions() throws Exception {
        GroupBuy unwritten = seedPreparing();
        GroupBuy draft = seedPreparing();
        seedPost(draft.getId(), GroupBuyPostReviewStatus.DRAFT, false);
        GroupBuy pending = seedPreparing();
        seedPost(pending.getId(), GroupBuyPostReviewStatus.PENDING, false);
        GroupBuy rejected = seedPreparing();
        seedPost(rejected.getId(), GroupBuyPostReviewStatus.REJECTED, false);

        studioSummary().andExpect(jsonPath("$.actionRequiredCount").value(3));
        studioDetail(unwritten.getId()).andExpect(jsonPath("$.post.status").value("NOT_WRITTEN"))
                .andExpect(jsonPath("$.permissions.canWritePost").value(true));
        studioDetail(draft.getId()).andExpect(jsonPath("$.post.status").value("WRITING"))
                .andExpect(jsonPath("$.readiness.gates[1].state").value("MY_TURN"));
        studioDetail(pending.getId()).andExpect(jsonPath("$.post.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.readiness.gates[2].state").value("IN_REVIEW"))
                .andExpect(jsonPath("$.permissions.canWritePost").value(false));
        studioDetail(rejected.getId()).andExpect(jsonPath("$.post.status").value("REJECTED"))
                .andExpect(jsonPath("$.post.rejection.detail").value("공구가 표기가 계약과 다릅니다."))
                .andExpect(jsonPath("$.readiness.gates[1].state").value("MY_TURN"));
    }

    @Test
    @DisplayName("B5a: 운영자 숨김 뒤 크리에이터가 수정해도 해제 전까지 비노출이다")
    void hiddenPostNeedsAdminUnhide() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuyPost post = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        Seller admin = fixture.createAdmin("studio-hide-admin@showroomz.test", "운영자");
        String token = adminToken(admin);

        mockMvc.perform(post("/v1/admin/group-buys/" + groupBuy.getId() + "/post/hide")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("reasonCode", "AD_MEDICAL_CLAIM", "detail", "의약품 오인 표현"))))
                .andExpect(status().isOk());
        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.groupBuy.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.post.status").value("HIDDEN"))
                .andExpect(jsonPath("$.post.hidden.detail").value("의약품 오인 표현"))
                .andExpect(jsonPath("$.permissions.canEditPost").value(true));
        assertThat(postRepository.findById(post.getPostId()).orElseThrow().getStatus()).isEqualTo(PostStatus.DRAFT);

        editPost(groupBuy.getId(), Map.of("title", "수정 제목", "content", "오인 표현을 지웠습니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.post.status").value("HIDDEN"));
        assertThat(postRepository.findById(post.getPostId()).orElseThrow().getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(revisionRepository.findByGroupBuyPostPostIdOrderByRevisionNoAsc(post.getPostId())).hasSize(1);

        mockMvc.perform(post("/v1/admin/group-buys/" + groupBuy.getId() + "/post/unhide")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("expectedRevisionNo", 1))))
                .andExpect(status().isOk());
        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.post.status").value("EXPOSED"));
        assertThat(postRepository.findById(post.getPostId()).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    @DisplayName("B6·C1·C2: 연장 요청은 진행중 배지를 바꾸지 않고 응답은 한 번만 가능하다")
    void extensionKeepsSellingUntilAnswered() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        action(groupBuy.getId(), "extension-request", Map.of("extensionDays", 2, "reason", "반응이 좋습니다"))
                .andExpect(status().isOk());

        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.groupBuy.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.extension.status").value("PENDING"))
                .andExpect(jsonPath("$.permissions.canRespondExtension").value(true));
        studioSummary().andExpect(jsonPath("$.actionRequiredCount").value(1));

        studioAction(groupBuy.getId(), "extension/rejection", Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.extension.status").value("REJECTED"))
                .andExpect(jsonPath("$.groupBuy.status").value("IN_PROGRESS"));
        studioAction(groupBuy.getId(), "extension/acceptance", null).andExpect(status().isConflict());
        action(groupBuy.getId(), "extension-request", Map.of("extensionDays", 2, "reason", "재요청"))
                .andExpect(status().isConflict());
        studioSummary().andExpect(jsonPath("$.actionRequiredCount").value(0));
    }

    @Test
    @DisplayName("C7·B10·B9: 크리에이터 중단 요청은 판매를 멈추지 않고 운영자 승인 때 종료된다")
    void creatorRequestNeedsAdminDecision() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuyPost post = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        Seller admin = fixture.createAdmin("studio-suspend-admin@showroomz.test", "운영자");

        studioAction(groupBuy.getId(), "suspension-request",
                Map.of("reasonCode", "DELIVERY_FAILURE", "memo", "배송하지 않은 주문이 있습니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.groupBuy.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.activeRequest.mine").value(true));
        long requestId = changeRequestRepository.findByGroupBuyIdOrderByRequestedAtDescIdDesc(groupBuy.getId()).get(0).getId();
        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.post.status").value("EXPOSED"));

        mockMvc.perform(post("/v1/admin/group-buys/" + groupBuy.getId() + "/change-requests/" + requestId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, adminToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("decisionReason", "배송 지연 확인"))))
                .andExpect(status().isOk());
        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.groupBuy.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.post.status").value("CLOSED"))
                .andExpect(jsonPath("$.closure.decisionReason").value("배송 지연 확인"))
                .andExpect(jsonPath("$.payout").doesNotExist());
        assertThat(postRepository.findById(post.getPostId()).orElseThrow().getStatus()).isEqualTo(PostStatus.DRAFT);
    }

    @Test
    @DisplayName("C3·C4: 제목·본문 경계 길이는 허용되고 초과 입력은 저장되지 않는다")
    void postInputBoundariesPreserveLastGoodDraft() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        String title = "가".repeat(40);
        String content = "나".repeat(2000);
        saveDraft(groupBuy.getId(), Map.of("title", title, "content", content))
                .andExpect(status().isOk());
        saveDraft(groupBuy.getId(), Map.of("title", "가".repeat(41), "content", "교체"))
                .andExpect(status().isBadRequest());
        submitPost(groupBuy.getId(), Map.of("title", title, "content", "나".repeat(2001)))
                .andExpect(status().isBadRequest());
        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.post.title").value(title))
                .andExpect(jsonPath("$.post.content").value(content))
                .andExpect(jsonPath("$.post.status").value("WRITING"));
        assertThat(revisionRepository.count()).isZero();
    }

    @Test
    @DisplayName("권한: 브랜드·운영자 토큰으로 스튜디오 API를 호출할 수 없다")
    void roleCannotCallCreatorApi() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        Seller admin = fixture.createAdmin("studio-role-admin@showroomz.test", "운영자");
        for (String token : new String[]{brandToken, adminToken(admin)}) {
            mockMvc.perform(get(STUDIO + "/" + groupBuy.getId()).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(STUDIO + "/" + groupBuy.getId() + "/post/submission")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(POST)))
                    .andExpect(status().isForbidden());
        }
        assertThat(groupBuyPostRepository.findByGroupBuyId(groupBuy.getId())).isEmpty();
    }

    @Test
    @DisplayName("목록 페이지를 넘겨도 총수와 정렬 순서가 유지되고 행이 중복되지 않는다")
    void listPaginationHasStableOrder() throws Exception {
        for (int i = 0; i < 5; i++) {
            GroupBuy groupBuy = seedPreparing();
            jdbc.update("UPDATE group_buy SET start_at = ? WHERE group_buy_id = ?",
                    Timestamp.valueOf(LocalDateTime.now().plusDays(10 + i)), groupBuy.getId());
        }

        var first = objectMapper.readTree(studioList("page=1&size=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(5))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andReturn().getResponse().getContentAsString());
        var second = objectMapper.readTree(studioList("page=2&size=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andReturn().getResponse().getContentAsString());
        var third = objectMapper.readTree(studioList("page=3&size=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andReturn().getResponse().getContentAsString());
        long[] ids = {
                first.at("/content/0/groupBuyId").asLong(), first.at("/content/1/groupBuyId").asLong(),
                second.at("/content/0/groupBuyId").asLong(), second.at("/content/1/groupBuyId").asLong(),
                third.at("/content/0/groupBuyId").asLong()
        };
        assertThat(ids).doesNotHaveDuplicates();
        studioList("page=4&size=2").andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("시작 시각이 지나도 준비 게이트가 비어 있으면 공구는 열리지 않고 게시물 제출은 가능하다")
    void missedStartKeepsPreparingUntilGatesAreReady() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE group_buy SET start_at = ? WHERE group_buy_id = ?",
                Timestamp.valueOf(now.minusMinutes(1)), groupBuy.getId());

        assertThat(lifecycleService.open(groupBuy.getId(), now)).isFalse();
        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.timeline.startOverdue").value(true))
                .andExpect(jsonPath("$.readiness.registrationOverdue").value(true))
                .andExpect(jsonPath("$.permissions.canWritePost").value(true));
        submitPost(groupBuy.getId(), POST).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("PREPARING"));
    }

    @Test
    @DisplayName("B5: 승인된 노출 게시물은 수정 즉시 반영되고 새 심사 없이 판본이 남는다")
    void exposedPostEditDoesNotRestartReview() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.READY);
        GroupBuyPost post = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE group_buy SET start_at = ? WHERE group_buy_id = ?",
                Timestamp.valueOf(now.minusMinutes(1)), groupBuy.getId());
        assertThat(lifecycleService.open(groupBuy.getId(), now)).isTrue();

        editPost(groupBuy.getId(), Map.of("title", "수정된 제목", "content", "새 본문"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post.status").value("EXPOSED"))
                .andExpect(jsonPath("$.post.title").value("수정된 제목"))
                .andExpect(jsonPath("$.permissions.canEditPost").value(true));
        assertThat(groupBuyPostRepository.findByGroupBuyId(groupBuy.getId()).orElseThrow().getReviewStatus())
                .isEqualTo(GroupBuyPostReviewStatus.APPROVED);
        assertThat(postRepository.findById(post.getPostId()).orElseThrow().getStatus())
                .isEqualTo(PostStatus.PUBLISHED);
        assertThat(revisionRepository.findByGroupBuyPostPostIdOrderByRevisionNoAsc(post.getPostId())).hasSize(1);
    }

    @Test
    @DisplayName("다른 크리에이터 공구의 모든 쓰기 경로는 같은 404 경계에서 막힌다")
    void allMutationsHideAnotherCreatorsGroupBuy() throws Exception {
        Creator other = createCreator("다른_쇼룸", "another-scenario");
        connect(brand, other);
        LocalDateTime start = LocalDateTime.now().plusDays(10);
        GroupBuy target = seed(brand, other, "다른 공구", start, start.plusDays(7));
        long id = target.getId();

        saveDraft(id, POST).andExpect(status().isNotFound());
        submitPost(id, POST).andExpect(status().isNotFound());
        editPost(id, POST).andExpect(status().isNotFound());
        studioAction(id, "extension/acceptance", null).andExpect(status().isNotFound());
        studioAction(id, "extension/rejection", Map.of()).andExpect(status().isNotFound());
        studioAction(id, "suspension-request", Map.of("reasonCode", "PERSONAL_REASON", "memo", "사유"))
                .andExpect(status().isNotFound());
        studioAction(id, "fulfillment-check", Map.of("result", "FULFILLED"))
                .andExpect(status().isNotFound());

        assertThat(groupBuyPostRepository.findByGroupBuyId(id)).isEmpty();
        assertThat(changeRequestRepository.findByGroupBuyIdOrderByRequestedAtDescIdDesc(id)).isEmpty();
        assertThat(reload(id).getStatus()).isEqualTo(GroupBuyStatus.PREPARING);
    }

    @Test
    @DisplayName("이행 기한이 지나도 자동 간주 스위치가 꺼져 있으면 직접 확인할 수 있다")
    void fulfillmentTimeoutDoesNotSilentlyConfirm() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.ENDED);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE group_buy SET fulfillment_due_at = ? WHERE group_buy_id = ?",
                Timestamp.valueOf(now.minusDays(1)), groupBuy.getId());

        assertThat(lifecycleService.autoConfirmFulfillment(groupBuy.getId(), now)).isZero();
        assertThat(fulfillmentCheckRepository.findByGroupBuyId(groupBuy.getId())).isEmpty();
        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.afterEnd.fulfillment.autoConfirmOnTimeout").value(false))
                .andExpect(jsonPath("$.permissions.canCheckFulfillment").value(true));
        studioAction(groupBuy.getId(), "fulfillment-check", Map.of("result", "FULFILLED"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("두 창에서 최초 게시물을 동시에 제출해도 게시물·제출 판본은 각각 하나만 남는다")
    void concurrentFirstSubmissionCannotOverwriteReview() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() -> {
                start.await();
                return submitPost(groupBuy.getId(), Map.of("title", "첫 번째 제목", "content", "첫 번째 본문"))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> second = pool.submit(() -> {
                start.await();
                return submitPost(groupBuy.getId(), Map.of("title", "두 번째 제목", "content", "두 번째 본문"))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(new int[]{first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }

        assertThat(groupBuyPostRepository.count()).isEqualTo(1);
        assertThat(groupBuyPostRepository.findByGroupBuyId(groupBuy.getId()).orElseThrow().getReviewStatus())
                .isEqualTo(GroupBuyPostReviewStatus.PENDING);
        assertThat(revisionRepository.count()).isEqualTo(1);
        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.post.status").value("PENDING_APPROVAL"));
    }
}
