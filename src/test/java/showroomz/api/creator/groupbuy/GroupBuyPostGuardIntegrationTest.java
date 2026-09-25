package showroomz.api.creator.groupbuy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.PostStatus;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 31 설계 0-6 — 공구 게시물이 일반 게시물 경로로 새지 않는다. 그리고 2-9 — 노출 투영이 오픈·종료를 따라간다.
 */
@DisplayName("[통합] 공구 게시물 격리 · 노출 투영")
class GroupBuyPostGuardIntegrationTest extends CreatorGroupBuyTestSupport {

    @Test
    @DisplayName("일반 게시물 API는 공구 게시물을 모른다 — 조회·삭제 모두 404")
    void studioPostApiCannotTouchGroupBuyPost() throws Exception {
        GroupBuyPost groupBuyPost = seedPost(seedPreparing().getId(), GroupBuyPostReviewStatus.PENDING, false);

        mockMvc.perform(get("/v1/creator/posts/" + groupBuyPost.getPostId())
                        .header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mockMvc.perform(delete("/v1/creator/posts/" + groupBuyPost.getPostId())
                        .header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isNotFound());

        assertThat(postRepository.findById(groupBuyPost.getPostId()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("운영자 일반 노출 중지는 공구 게시물을 내리지 못한다 — 목록에도 섞이지 않는다")
    void adminGeneralSuspensionSkipsGroupBuyPost() throws Exception {
        GroupBuy groupBuy = openedGroupBuyWithApprovedPost();
        Long postId = groupBuyPostRepository.findByGroupBuyId(groupBuy.getId()).orElseThrow().getPostId();
        Seller admin = fixture.createAdmin("admin@showroomz.test", "운영자");
        String token = adminToken(admin);

        mockMvc.perform(post("/v1/admin/posts/" + postId + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"AD_DISCLOSURE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NOT_EDITABLE"));
        mockMvc.perform(get("/v1/admin/posts").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].postId").value(not(hasItem(postId.intValue()))));

        assertThat(postRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    @DisplayName("오픈하면 노출(PUBLISHED) — 소비자 상세·좋아요는 되고 일반 피드에는 뜨지 않는다 · 종료하면 비노출")
    void exposureFollowsLifecycle() throws Exception {
        GroupBuy groupBuy = openedGroupBuyWithApprovedPost();
        Long postId = groupBuyPostRepository.findByGroupBuyId(groupBuy.getId()).orElseThrow().getPostId();
        Post opened = postRepository.findById(postId).orElseThrow();
        assertThat(opened.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(opened.getPublishedAt()).isNotNull();

        String consumer = consumerToken();
        mockMvc.perform(get("/v1/user/showrooms/posts/" + postId).header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(status().isOk());
        // 정책 빈이 없으면 여기서 500이다(31 설계 0-6).
        mockMvc.perform(post("/v1/user/showrooms/posts/" + postId + "/wishlist").header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/v1/user/showrooms/posts").header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].postId").value(not(hasItem(postId.intValue()))));

        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), groupBuy.getId());
        assertThat(lifecycleService.end(groupBuy.getId(), LocalDateTime.now())).isTrue();

        Post closed = postRepository.findById(postId).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(PostStatus.DRAFT);
        // 게시일은 처음 세상에 나온 때다 — 비노출로 내려가도 지우지 않는다.
        assertThat(closed.getPublishedAt()).isEqualTo(opened.getPublishedAt());
    }

    /** 준비완료 + 승인 게시물 → 시작 시각을 과거로 옮기고 스케줄러의 오픈을 태운다. */
    private GroupBuy openedGroupBuyWithApprovedPost() {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.READY);
        seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        jdbc.update("UPDATE group_buy SET start_at = ? WHERE group_buy_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(5)), groupBuy.getId());
        assertThat(lifecycleService.open(groupBuy.getId(), LocalDateTime.now())).isTrue();
        return reload(groupBuy.getId());
    }

    private String consumerToken() {
        LocalDateTime now = LocalDateTime.now();
        Users viewer = userRepository.save(new Users("viewer-mia", "미아", "mia@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now));
        return bearerToken(viewer.getUsername(), RoleType.USER, viewer.getId());
    }
}
