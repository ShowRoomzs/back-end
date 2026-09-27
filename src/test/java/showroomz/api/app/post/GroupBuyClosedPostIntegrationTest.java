package showroomz.api.app.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.service.GroupBuyBackfillService;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.post.type.PostStatus;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 마감 게시물 3일 보존 · 스케줄러 「마감 게시물 내리기」 · 1회 백필 · 마감 중 좋아요·신고(공구 게시물 설계 4-1 · 4-2 · 7-2).
 *
 * <p>실제 스케줄러가 테스트 중에도 돌므로 종료 시각은 최근으로 두고, 판정 시각({@code now})을 미래로 넘겨 72시간 경과를 만든다.
 */
@DisplayName("[통합] 마감 공구 게시물 — 3일 보존 · 내리기 · 백필 · 좋아요·신고")
class GroupBuyClosedPostIntegrationTest extends GroupBuyPostTestSupport {

    @Autowired
    private GroupBuyBackfillService backfillService;

    // ── 3일 보존 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("스케줄러 종료 직후 — 상세 200 · CLOSED · 새 좋아요 거절 · 해제 허용 · 72시간 되기 1초 전까지 유지")
    void closedRetainedForThreeDays() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        Long postId = exposedPost(groupBuy).getPostId();
        like(consumer, postId);
        LocalDateTime endedAt = LocalDateTime.now().minusMinutes(1).withNano(0);
        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?", Timestamp.valueOf(endedAt), groupBuy.getId());
        assertThat(lifecycleService.end(groupBuy.getId(), LocalDateTime.now())).isTrue();
        assertThat(reload(groupBuy.getId()).getEndedAt()).isEqualTo(endedAt);

        detail(postId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.saleState").value("CLOSED"))
                .andExpect(jsonPath("$.likeLocked").value(true))
                .andExpect(jsonPath("$.isLiked").value(true));
        mockMvc.perform(post(SHOWROOMS + "posts/" + postId + "/wishlist").header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(SHOWROOMS + "posts/" + postId + "/wishlist").header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(status().isNoContent());
        assertThat(postRepository.findById(postId).orElseThrow().getLikeCount()).isZero();

        assertThat(lifecycleService.retirePost(groupBuy.getId(), endedAt.plusHours(72).minusSeconds(1))).isZero();
        assertThat(postRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    @DisplayName("조기 마감은 end_at이 아니라 실제로 끝난 시각(ended_at)부터 3일")
    void earlyCloseCountsFromEndedAt() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        Long postId = exposedPost(groupBuy).getPostId();
        LocalDateTime closedAt = LocalDateTime.now().minusMinutes(10).withNano(0);
        terminate(groupBuy.getId(), GroupBuyStatus.ENDED, GroupBuyCloseType.EARLY_CLOSED, closedAt);
        assertThat(reload(groupBuy.getId()).getEndAt()).isAfter(LocalDateTime.now());

        detail(postId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.saleState").value("CLOSED"));
        assertThat(lifecycleService.retirePost(groupBuy.getId(), closedAt.plusHours(72).minusSeconds(1))).isZero();
        assertThat(lifecycleService.retirePost(groupBuy.getId(), closedAt.plusHours(72))).isEqualTo(1);
        detail(postId).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("중단(직권·요청 승인 모두 close_type SUSPENDED)은 즉시 비노출 — 상세 404")
    void suspendedIsHiddenImmediately() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        Long postId = exposedPost(groupBuy).getPostId();

        terminate(groupBuy.getId(), GroupBuyStatus.SUSPENDED, GroupBuyCloseType.SUSPENDED,
                LocalDateTime.now().minusMinutes(1));

        assertThat(postRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(PostStatus.DRAFT);
        detail(postId).andExpect(status().isNotFound());
        lowerFeed(creator).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("정산완료(SETTLED)로 넘어가도 종료 시각 기준 3일은 그대로 보존")
    void settledKeepsRetention() {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        Long postId = exposedPost(groupBuy).getPostId();
        LocalDateTime endedAt = LocalDateTime.now().minusMinutes(10).withNano(0);
        endNow(groupBuy.getId(), endedAt);
        jdbc.update("UPDATE group_buy SET status = 'SETTLED' WHERE group_buy_id = ?", groupBuy.getId());
        sync(groupBuy.getId(), LocalDateTime.now());

        assertThat(postRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(lifecycleService.retirePost(groupBuy.getId(), endedAt.plusHours(72))).isEqualTo(1);
    }

    // ── 내리기 대상 선정 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("내리기 대상 — 종결 ∧ 종료 후 72시간 경과 ∧ 아직 게시중만 · 오래 끝난 것부터")
    void retireTargetSelection() {
        LocalDateTime base = LocalDateTime.now().minusMinutes(30).withNano(0);
        GroupBuy due = seedIn(GroupBuyStatus.IN_PROGRESS);
        exposedPost(due);
        endNow(due.getId(), base);
        GroupBuy dueLater = ongoingGroupBuyOf("늦게끝난브랜드", creator);
        exposedPost(dueLater);
        endNow(dueLater.getId(), base.plusMinutes(10));
        GroupBuy notYet = ongoingGroupBuyOf("아직브랜드", creator);
        exposedPost(notYet);
        endNow(notYet.getId(), base.plusHours(2));
        GroupBuy alreadyDraft = ongoingGroupBuyOf("숨김브랜드", creator);
        seedPost(alreadyDraft.getId(), GroupBuyPostReviewStatus.APPROVED, true);
        endNow(alreadyDraft.getId(), base);
        GroupBuy ongoing = ongoingGroupBuyOf("진행브랜드", creator);
        exposedPost(ongoing);

        List<Long> ids = lifecycleService.findIdsToRetirePost(base.plusHours(73), 100);

        assertThat(ids).containsSubsequence(due.getId(), dueLater.getId());
        assertThat(ids).doesNotContain(notYet.getId(), alreadyDraft.getId(), ongoing.getId());
        assertThat(lifecycleService.findIdsToRetirePost(base.plusHours(73), 1)).hasSize(1);
    }

    @Test
    @DisplayName("내리기는 멱등이고 종결되지 않은 공구는 건드리지 않는다 · 없는 공구도 조용히 0")
    void retireIsIdempotentAndGuarded() {
        GroupBuy ongoing = seedIn(GroupBuyStatus.IN_PROGRESS);
        Long ongoingPostId = exposedPost(ongoing).getPostId();

        assertThat(lifecycleService.retirePost(ongoing.getId(), LocalDateTime.now().plusDays(30))).isZero();
        assertThat(postRepository.findById(ongoingPostId).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(lifecycleService.retirePost(999_999L, LocalDateTime.now())).isZero();

        GroupBuy closed = ongoingGroupBuyOf("마감브랜드", creator);
        Long closedPostId = exposedPost(closed).getPostId();
        LocalDateTime endedAt = LocalDateTime.now().minusMinutes(5).withNano(0);
        endNow(closed.getId(), endedAt);
        assertThat(lifecycleService.retirePost(closed.getId(), endedAt.plusHours(72))).isEqualTo(1);
        assertThat(lifecycleService.retirePost(closed.getId(), endedAt.plusHours(72))).isZero();
        assertThat(postRepository.findById(closedPostId).orElseThrow().getStatus()).isEqualTo(PostStatus.DRAFT);
        // 게시일은 지우지 않는다 — 처음 세상에 나온 때다
        assertThat(postRepository.findById(closedPostId).orElseThrow().getPublishedAt()).isNotNull();
    }

    // ── 백필 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("백필 — 옛 규칙으로 내려간 게시물 중 종료 3일 이내 · 노출된 적 있음 · 숨김 아님 · 중단 아님만 되살린다 · 두 번 돌려도 같다")
    void backfillRestoresOnlyEligible() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Long recent = closedAsDraft(seedIn(GroupBuyStatus.IN_PROGRESS), now.minusHours(1),
                GroupBuyStatus.ENDED, GroupBuyCloseType.COMPLETED);
        Long early = closedAsDraft(ongoingGroupBuyOf("조기브랜드", creator), now.minusHours(2),
                GroupBuyStatus.ENDED, GroupBuyCloseType.EARLY_CLOSED);
        Long old = closedAsDraft(ongoingGroupBuyOf("오래된브랜드", creator), now.minusDays(4),
                GroupBuyStatus.ENDED, GroupBuyCloseType.COMPLETED);
        Long suspended = closedAsDraft(ongoingGroupBuyOf("중단브랜드", creator), now.minusHours(1),
                GroupBuyStatus.SUSPENDED, GroupBuyCloseType.SUSPENDED);
        GroupBuy hiddenGroupBuy = ongoingGroupBuyOf("숨김브랜드", creator);
        Long hidden = closedAsDraft(hiddenGroupBuy, now.minusHours(1), GroupBuyStatus.ENDED, GroupBuyCloseType.COMPLETED);
        jdbc.update("UPDATE group_buy_post SET hidden_at = ? WHERE post_id = ?",
                Timestamp.valueOf(now.minusHours(3)), hidden);
        GroupBuy unpublishedGroupBuy = ongoingGroupBuyOf("미노출브랜드", creator);
        Long unpublished = seedPost(unpublishedGroupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false).getPostId();
        jdbc.update("UPDATE group_buy SET status = 'ENDED', close_type = 'COMPLETED', ended_at = ? WHERE group_buy_id = ?",
                Timestamp.valueOf(now.minusHours(1)), unpublishedGroupBuy.getId());

        List<Long> targets = backfillService.findClosedPostTargets(now);
        assertThat(targets).doesNotContain(groupBuyIdOf(old));

        int restored = 0;
        for (Long groupBuyId : targets) {
            restored += backfillService.resyncClosedPost(groupBuyId, now) ? 1 : 0;
        }
        assertThat(restored).isEqualTo(2);
        assertThat(statusOf(recent)).isEqualTo(PostStatus.PUBLISHED);
        assertThat(statusOf(early)).isEqualTo(PostStatus.PUBLISHED);
        assertThat(statusOf(old)).isEqualTo(PostStatus.DRAFT);
        assertThat(statusOf(suspended)).isEqualTo(PostStatus.DRAFT);
        assertThat(statusOf(hidden)).isEqualTo(PostStatus.DRAFT);
        assertThat(statusOf(unpublished)).isEqualTo(PostStatus.DRAFT);

        for (Long groupBuyId : backfillService.findClosedPostTargets(now)) {
            assertThat(backfillService.resyncClosedPost(groupBuyId, now)).isFalse();
        }
    }

    // ── 신고 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("마감 3일 동안에도 신고는 접수되고, 어드민 신고 목록에 postType GROUP_BUY로 실린다(7-2)")
    void reportDuringRetention() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        Long postId = exposedPost(groupBuy).getPostId();
        Long generalId = generalPost(creator, "일반", LocalDateTime.now().minusHours(1));
        endNow(groupBuy.getId(), LocalDateTime.now().minusMinutes(5));

        for (Long target : new Long[]{postId, generalId}) {
            mockMvc.perform(post("/v1/user/posts/" + target + "/reports")
                            .header(HttpHeaders.AUTHORIZATION, consumer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reasonCode\":\"AD_DISCLOSURE\"}"))
                    .andExpect(status().isNoContent());
        }

        Seller admin = fixture.createAdmin("admin@showroomz.test", "운영자");
        mockMvc.perform(get("/v1/admin/post-reports").header(HttpHeaders.AUTHORIZATION, adminToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.postId == " + postId + ")].postType").value(hasItem("GROUP_BUY")))
                .andExpect(jsonPath("$.content[?(@.postId == " + generalId + ")].postType").value(hasItem("GENERAL")));
    }

    // ------------------------------------------------------------------ 내부

    /** 옛 투영식이 남긴 상태 — 노출됐다가 종결 즉시 DRAFT로 내려갔다. */
    private Long closedAsDraft(GroupBuy groupBuy, LocalDateTime endedAt, GroupBuyStatus status,
                               GroupBuyCloseType closeType) {
        Long postId = exposedPost(groupBuy).getPostId();
        jdbc.update("UPDATE group_buy SET status = ?, close_type = ?, ended_at = ? WHERE group_buy_id = ?",
                status.name(), closeType.name(), Timestamp.valueOf(endedAt), groupBuy.getId());
        jdbc.update("UPDATE post SET status = 'DRAFT' WHERE post_id = ?", postId);
        return postId;
    }

    private PostStatus statusOf(Long postId) {
        return postRepository.findById(postId).orElseThrow().getStatus();
    }

    private Long groupBuyIdOf(Long postId) {
        return jdbc.queryForObject("SELECT group_buy_id FROM group_buy_post WHERE post_id = ?", Long.class, postId);
    }
}
