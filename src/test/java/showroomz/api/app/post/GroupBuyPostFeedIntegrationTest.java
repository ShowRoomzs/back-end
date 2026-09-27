package showroomz.api.app.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소비자 목록의 공구 범위 · 정렬 · 로즈 링(공구 게시물 설계 4-5 · 6절 · 6-1).
 *
 * <pre>
 * C1 팔로잉·추천·전체   일반 + 진행 중 공구
 * C4 고정 섹션          진행 중 공구
 * C4 아래 피드          일반 + 마감 게시물
 * C4 postCount          타입 무관 = 고정 섹션 + 아래 피드
 * C3 좋아요             타입·진행 무관 · GROUP_BUY_FIRST는 진행 중만 앞으로
 * </pre>
 */
@DisplayName("[통합] 공구 게시물 목록 — C1 · C3 · C4 범위와 정렬 · 로즈 링")
class GroupBuyPostFeedIntegrationTest extends GroupBuyPostTestSupport {

    private static final String LIKED = "/v1/user/wishlist/contents";

    // ── C1 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("C1 팔로잉 — 진행 중 공구와 일반 게시물이 게시 시각순으로 섞이고 마감 공구는 빠진다 · 카운트도 같은 조건")
    void followingFeedScope() throws Exception {
        follow(viewer, creator);
        Long general = generalPost(creator, "일반 게시물", LocalDateTime.now().minusHours(2));
        Long ongoing = ongoingPost();
        GroupBuy closedGroupBuy = ongoingGroupBuyOf("마감브랜드", creator);
        Long closed = exposedPost(closedGroupBuy).getPostId();
        endNow(closedGroupBuy.getId(), LocalDateTime.now().minusMinutes(30));

        mockMvc.perform(get("/v1/user/feed/following").header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].post.postId").value(contains(ongoing.intValue(), general.intValue())))
                .andExpect(jsonPath("$.content[*].post.postId").value(not(hasItem(closed.intValue()))))
                .andExpect(jsonPath("$.content[0].contentType").value("GROUP_BUY"))
                .andExpect(jsonPath("$.content[0].post.isFollowing").value(true))
                .andExpect(jsonPath("$.pageInfo.totalResults").value(2));
    }

    @Test
    @DisplayName("C1 추천 — 팔로우한 쇼룸의 공구는 추천에서 빠지고(팔로잉과 겹치지 않음) 팔로우하지 않은 쇼룸의 진행 중 공구만 뜬다")
    void recommendedExcludesFollowed() throws Exception {
        Creator other = visibleCreator("다른_쇼룸", "other");
        Long followedPost = ongoingPost();
        Long otherPost = exposedPost(ongoingGroupBuyOf("다른브랜드", other)).getPostId();
        follow(viewer, creator);

        mockMvc.perform(get("/v1/user/feed/recommended").header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(jsonPath("$.content[*].post.postId").value(hasItem(otherPost.intValue())))
                .andExpect(jsonPath("$.content[*].post.postId").value(not(hasItem(followedPost.intValue()))));
        mockMvc.perform(get("/v1/user/feed/recommended"))
                .andExpect(jsonPath("$.content[*].post.postId")
                        .value(hasItems(otherPost.intValue(), followedPost.intValue())));
    }

    @Test
    @DisplayName("전체 목록(/showrooms/posts)도 C1과 같은 규칙 — 비로그인 허용 · 진행 중 공구 포함 · 마감 제외 · 숨김 제외")
    void globalListFollowsC1Scope() throws Exception {
        Long ongoing = ongoingPost();
        GroupBuy closedGroupBuy = ongoingGroupBuyOf("마감브랜드", creator);
        Long closed = exposedPost(closedGroupBuy).getPostId();
        endNow(closedGroupBuy.getId(), LocalDateTime.now().minusMinutes(30));
        GroupBuy hiddenGroupBuy = ongoingGroupBuyOf("숨김브랜드", creator);
        Long hidden = seedPost(hiddenGroupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, true).getPostId();
        sync(hiddenGroupBuy.getId(), LocalDateTime.now());

        // 비로그인도 열린다(설계 5-1)
        mockMvc.perform(get(SHOWROOMS + "posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].post.postId").value(hasItem(ongoing.intValue())))
                .andExpect(jsonPath("$.content[*].post.postId").value(not(hasItem(closed.intValue()))))
                .andExpect(jsonPath("$.content[*].post.postId").value(not(hasItem(hidden.intValue()))));
    }

    // ── C4 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("C4 게시물 수 = 고정 섹션 + 아래 피드 — 일반 2 · 진행 중 1 · 마감 1 · 숨김 1(제외)")
    void postCountEqualsPinnedPlusLower() throws Exception {
        Long first = generalPost(creator, "일반 1", LocalDateTime.now().minusDays(2));
        Long second = generalPost(creator, "일반 2", LocalDateTime.now().minusDays(1));
        Long ongoing = ongoingPost();
        GroupBuy closedGroupBuy = ongoingGroupBuyOf("마감브랜드", creator);
        Long closed = exposedPost(closedGroupBuy).getPostId();
        endNow(closedGroupBuy.getId(), LocalDateTime.now().minusMinutes(30));
        GroupBuy hiddenGroupBuy = ongoingGroupBuyOf("숨김브랜드", creator);
        seedPost(hiddenGroupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, true);
        sync(hiddenGroupBuy.getId(), LocalDateTime.now());

        pinned(creator)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].post.postId").value(ongoing));
        lowerFeed(creator)
                .andExpect(jsonPath("$.pageInfo.totalResults").value(3))
                .andExpect(jsonPath("$.content[*].post.postId")
                        .value(contains(closed.intValue(), second.intValue(), first.intValue())));
        profile(creator).andExpect(jsonPath("$.postCount").value(4));
    }

    // ── C3 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("C3 「공구 게시물 먼저」 — 진행 중 공구만 앞으로, 마감 공구는 일반 게시물과 좋아요한 시각순으로 섞인다")
    void groupBuyFirstRanksOngoingOnly() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Long generalNew = generalPost(creator, "일반 A", now.minusDays(3));
        Long generalOld = generalPost(creator, "일반 B", now.minusDays(3));
        Long ongoing = ongoingPost();
        GroupBuy closedGroupBuy = ongoingGroupBuyOf("마감브랜드", creator);
        Long closed = exposedPost(closedGroupBuy).getPostId();
        for (Long postId : new Long[]{generalNew, generalOld, ongoing, closed}) {
            like(consumer, postId);
        }
        endNow(closedGroupBuy.getId(), now.minusMinutes(30));
        likedAt(viewer, generalNew, now.minusHours(1));
        likedAt(viewer, closed, now.minusHours(2));
        likedAt(viewer, ongoing, now.minusHours(3));
        likedAt(viewer, generalOld, now.minusHours(4));

        liked("GROUP_BUY_FIRST")
                .andExpect(jsonPath("$.content[*].post.postId").value(contains(
                        ongoing.intValue(), generalNew.intValue(), closed.intValue(), generalOld.intValue())));
        liked("DEFAULT")
                .andExpect(jsonPath("$.content[*].post.postId").value(contains(
                        generalNew.intValue(), closed.intValue(), ongoing.intValue(), generalOld.intValue())));
        liked("LIKED_OLDEST")
                .andExpect(jsonPath("$.content[*].post.postId").value(contains(
                        generalOld.intValue(), ongoing.intValue(), closed.intValue(), generalNew.intValue())));
    }

    @Test
    @DisplayName("C3 — 마감 공구는 3일 동안 남는다(하트 잠김 · 상품 행 없음 · 개수 포함) · 그 뒤 빠져도 좋아요 기록·수는 지우지 않는다")
    void closedStaysThreeDaysThenLeaves() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        Long postId = exposedPost(groupBuy).getPostId();
        like(consumer, postId);
        LocalDateTime endedAt = LocalDateTime.now().minusMinutes(30).withNano(0);
        endNow(groupBuy.getId(), endedAt);

        liked("DEFAULT")
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].contentType").value("GROUP_BUY"))
                .andExpect(jsonPath("$.content[0].post.isLiked").value(true))
                .andExpect(jsonPath("$.content[0].post.likeLocked").value(true))
                .andExpect(jsonPath("$.content[0].post.groupBuy.saleState").value("CLOSED"))
                .andExpect(jsonPath("$.content[0].post.groupBuy.products").isEmpty())
                .andExpect(jsonPath("$.content[0].post.groupBuy.productCount").value(2));

        assertThat(lifecycleService.retirePost(groupBuy.getId(), endedAt.plusHours(72))).isEqualTo(1);

        liked("DEFAULT")
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        assertThat(postLikeRepository.existsByUserIdAndPostId(viewer.getId(), postId)).isTrue();
        assertThat(postRepository.findById(postId).orElseThrow().getLikeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("C3 — 공구 카드는 목록에서도 상품을 전부 싣는다(첫 항목만 그리는 것은 앱의 일)")
    void likedCardCarriesAllProducts() throws Exception {
        Long postId = ongoingPost();
        like(consumer, postId);

        liked("DEFAULT")
                .andExpect(jsonPath("$.content[0].post.likeLocked").value(false))
                .andExpect(jsonPath("$.content[0].post.groupBuy.products.length()").value(2));
    }

    // ── 로즈 링 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("로즈 링 — C2 팔로잉 목록 · C14 검색도 같은 정의(게시중 공구 게시물)를 쓴다 · 같은 브랜드에 연결됐을 뿐인 쇼룸은 꺼진다")
    void roseRingEverywhere() throws Exception {
        Creator sibling = visibleCreator("글로우_형제", "sibling");
        connect(brand, sibling);
        ongoingPost();
        follow(viewer, creator);
        follow(viewer, sibling);

        mockMvc.perform(get(SHOWROOMS + "following").header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(status().isOk())
                .andExpect(ring("$.content", creator, true))
                .andExpect(ring("$.content", sibling, false));
        mockMvc.perform(get("/v1/user/search/showrooms").param("keyword", "글로우"))
                .andExpect(status().isOk())
                .andExpect(ring("$.content", creator, true))
                .andExpect(ring("$.content", sibling, false));
    }

    @Test
    @DisplayName("로즈 링 — 마감 3일 동안의 게시물은 링을 켜지 않는다")
    void closedDoesNotLightRing() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        exposedPost(groupBuy);
        follow(viewer, creator);
        endNow(groupBuy.getId(), LocalDateTime.now().minusMinutes(30));

        mockMvc.perform(get(SHOWROOMS + "following").header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(ring("$.content", creator, false));
        profile(creator).andExpect(jsonPath("$.hasOngoingGroupBuy").value(false));
    }

    // ------------------------------------------------------------------ 내부

    private ResultActions liked(String sort) throws Exception {
        return mockMvc.perform(get(LIKED).param("sort", sort).header(HttpHeaders.AUTHORIZATION, consumer))
                .andExpect(status().isOk());
    }

    private static ResultMatcher ring(String listPath, Creator showroom, boolean expected) {
        return jsonPath(listPath + "[?(@.showroomId == " + showroom.getId() + ")].hasOngoingGroupBuy")
                .value(contains(expected));
    }
}
